package com.factory.anomaly.service;

import org.springframework.beans.factory.annotation.Value;
import com.factory.anomaly.engine.RuleEngine;
import com.factory.anomaly.engine.RuleResult;
import com.factory.anomaly.engine.RuleSensorSample;
import com.factory.anomaly.event.payload.AnomalyCreatedPayload;
import com.factory.anomaly.event.payload.type.AnomalyEventType;
import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.entity.Equipment;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipe;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.redis.SensorSample;
import com.factory.anomaly.infrastructure.repository.AnomalyLogRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeDetailRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRepository;
import com.factory.common.event.domain.EventEnvelope;
import com.factory.common.event.support.EventEnvelopeFactory;
import com.factory.common.kafka.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private static final int FIVE_MINUTES = 5;
    private static final int ONE_MINUTE = 1;

    private final SensorRedisRepository sensorRedisRepository;
    private final RuleEngine ruleEngine;
    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentRecipeRepository equipmentRecipeRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;
    private final EventPublisher eventPublisher;
    private final EventEnvelopeFactory eventEnvelopeFactory;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Optional<AnomalyLog> detect(String equipmentCode, String sensorType) {
        return detect(equipmentCode, sensorType, LocalDateTime.now());
    }

    @Override
    public Optional<AnomalyLog> detect(
            String equipmentCode,
            String sensorType,
            LocalDateTime detectedAt
    ) {
        log.info(
                "Start anomaly detection. equipmentCode={}, sensorType={}, detectedAt={}",
                equipmentCode,
                sensorType,
                detectedAt
        );

        List<SensorSample> fiveMinuteRedisSamples = sensorRedisRepository.findSamples(
                equipmentCode,
                sensorType,
                detectedAt,
                FIVE_MINUTES,
                0
        );

        log.info(
                "Five-minute Redis samples loaded. equipmentCode={}, sensorType={}, sampleCount={}",
                equipmentCode,
                sensorType,
                fiveMinuteRedisSamples.size()
        );

        if (fiveMinuteRedisSamples.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=NO_REDIS_SAMPLES, equipmentCode={}, sensorType={}, detectedAt={}",
                    equipmentCode,
                    sensorType,
                    detectedAt
            );
            return Optional.empty();
        }

        List<SensorSample> oneMinuteRedisSamples = sensorRedisRepository.findSamples(
                equipmentCode,
                sensorType,
                detectedAt,
                ONE_MINUTE,
                0
        );

        log.info(
                "One-minute Redis samples loaded. equipmentCode={}, sensorType={}, sampleCount={}",
                equipmentCode,
                sensorType,
                oneMinuteRedisSamples.size()
        );

        List<RuleSensorSample> fiveMinuteSamples = toRuleSamples(fiveMinuteRedisSamples);
        List<RuleSensorSample> oneMinuteSamples = toRuleSamples(oneMinuteRedisSamples);

        Optional<Equipment> equipmentOptional = equipmentRepository.findByName(equipmentCode);

        if (equipmentOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentCode={}",
                    equipmentCode
            );
            return Optional.empty();
        }

        Equipment equipment = equipmentOptional.get();

        Optional<EquipmentRecipe> equipmentRecipeOptional = equipmentRecipeRepository
                .findTopByEquipment_IdOrderByVersionDesc(equipment.getId());

        if (equipmentRecipeOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_RECIPE_NOT_FOUND, equipmentId={}, equipmentCode={}",
                    equipment.getId(),
                    equipmentCode
            );
            return Optional.empty();
        }

        EquipmentRecipe equipmentRecipe = equipmentRecipeOptional.get();

        Optional<EquipmentRecipeDetail> recipeDetailOptional = equipmentRecipeDetailRepository
                .findById(new EquipmentRecipeDetailId(equipmentRecipe.getId(), sensorType));

        if (recipeDetailOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=RECIPE_DETAIL_NOT_FOUND, equipmentRecipeId={}, sensorType={}",
                    equipmentRecipe.getId(),
                    sensorType
            );
            return Optional.empty();
        }

        EquipmentRecipeDetail recipeDetail = recipeDetailOptional.get();

        log.info(
                "Recipe threshold loaded. equipmentRecipeId={}, sensorType={}, minValue={}, maxValue={}",
                equipmentRecipe.getId(),
                sensorType,
                recipeDetail.getMin(),
                recipeDetail.getMax()
        );

        RuleResult ruleResult = ruleEngine.evaluate(
                fiveMinuteSamples,
                oneMinuteSamples,
                recipeDetail.getMin(),
                recipeDetail.getMax()
        );

        if (!ruleResult.detected()) {
            log.info(
                    "No anomaly detected. equipmentCode={}, sensorType={}, reason={}",
                    equipmentCode,
                    sensorType,
                    ruleResult.reason()
            );
            return Optional.empty();
        }

        log.warn(
                "Anomaly detected. equipmentCode={}, sensorType={}, ruleName={}, anomalyType={}, severity={}",
                equipmentCode,
                sensorType,
                ruleResult.ruleName(),
                ruleResult.anomalyType(),
                ruleResult.severity()
        );

        AnomalyLog anomalyLog = AnomalyLog.builder()
                .equipment(equipment)
                .equipmentRecipe(equipmentRecipe)
                .recipeParameter(sensorType)
                .severity(ruleResult.severity())
                .occurredTime(detectedAt)
                .ruleName(ruleResult.ruleName())
                .anomalyType(ruleResult.anomalyType())
                .logType(LogType.SENSOR)
                .windowStartTime(detectedAt.minusMinutes(FIVE_MINUTES))
                .sampleCount(fiveMinuteSamples.size())
                .detectionReason(ruleResult.reason())
                .relatedLogIds(null)
                .measuredValue(ruleResult.measuredValue())
                .referenceValue(ruleResult.referenceValue())
                .deviation(ruleResult.deviation())
                .deviationRate(ruleResult.deviationRate())
                .build();

        AnomalyLog savedAnomalyLog = anomalyLogRepository.save(anomalyLog);

        log.info(
                "Anomaly log saved. logId={}, equipmentId={}, equipmentRecipeId={}, sensorType={}, severity={}, ruleName={}",
                savedAnomalyLog.getLogId(),
                equipment.getId(),
                equipmentRecipe.getId(),
                sensorType,
                savedAnomalyLog.getSeverity(),
                savedAnomalyLog.getRuleName()
        );

        AnomalyCreatedPayload payload = AnomalyCreatedPayload.builder()
                .equipmentId(equipment.getId())
                .equipmentName(equipment.getName())
                .recipeParameter(sensorType)
                .severity(savedAnomalyLog.getSeverity().name())
                .occurredTime(savedAnomalyLog.getOccurredTime().atZone(ZoneId.systemDefault()).toInstant())
                .causeRule(savedAnomalyLog.getRuleName().name())
                .build();

        EventEnvelope<AnomalyCreatedPayload> eventEnvelope = eventEnvelopeFactory.create(
                AnomalyEventType.ANOMALY_CREATED,
                payload
        );

        if (eventPublishEnabled) {
            publishAfterCommit(eventEnvelope);
        } else {
            log.info(
                    "Skip anomaly event publishing. reason=EVENT_PUBLISH_DISABLED, logId={}",
                    savedAnomalyLog.getLogId()
            );
        }

        return Optional.of(savedAnomalyLog);
    }

    private void publishAfterCommit(EventEnvelope<AnomalyCreatedPayload> eventEnvelope) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publish(eventEnvelope);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publish(eventEnvelope);
                    }
                }
        );
    }

    private List<RuleSensorSample> toRuleSamples(List<SensorSample> redisSamples) {
        return redisSamples.stream()
                .map(sample -> new RuleSensorSample(
                        sample.timestamp(),
                        sample.value()
                ))
                .toList();
    }
}
