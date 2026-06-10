package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.engine.RuleEngine;
import com.factory.anomaly.engine.RuleResult;
import com.factory.anomaly.engine.RuleSensorSample;
import com.factory.anomaly.event.payload.producer.AnomalyCreatedPayload;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.redis.SensorSample;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.factory.common.event.domain.Event;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.kafka.publisher.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final AnomalyRepository anomalyRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;
    private final ObjectMapper objectMapper;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Optional<Anomaly> detect(String equipmentCode, String sensorType) {
        return detect(equipmentCode, sensorType, LocalDateTime.now());
    }

    @Override
    public Optional<Anomaly> detect(
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

        // 5분치 샘플
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

        // 1분치 샘플
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

        // equipment_projection에서 Equipment 정보 로드 (Numeric ID 우선, fallback string name)
        Long equipmentId = null;
        EquipmentProjection equipment = null;
        try {
            equipmentId = Long.parseLong(equipmentCode);
            equipment = equipmentProjectionRepository.findById(equipmentId).orElse(null);
        } catch (NumberFormatException e) {
            log.debug("equipmentCode is not numeric ID, falling back to name search: {}", equipmentCode);
        }

        if (equipment == null) {
            equipment = equipmentProjectionRepository.findByName(equipmentCode).orElse(null);
        }

        if (equipment == null) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentCode={}",
                    equipmentCode
            );
            return Optional.empty();
        }

        // Redis 샘플에서 min, max 기준값 로드
        Double min = null;
        Double max = null;
        for (SensorSample sample : fiveMinuteRedisSamples) {
            if (sample.min() != null && sample.max() != null) {
                min = sample.min();
                max = sample.max();
                break;
            }
        }

        log.info(
                "Recipe threshold loaded from Redis. equipmentId={}, sensorType={}, minValue={}, maxValue={}",
                equipment.getId(),
                sensorType,
                min,
                max
        );

        RuleResult ruleResult = ruleEngine.evaluate(
                fiveMinuteSamples,
                oneMinuteSamples,
                min,
                max
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

        Instant detectedInstant = detectedAt.toInstant(ZoneOffset.UTC);

        Instant firstDetectedAt;
        int sampleCount;
        if (ruleResult.ruleName() == RuleName.NELSON_RULE_3) {
            firstDetectedAt = detectedInstant.minusSeconds(60L);
            sampleCount = oneMinuteSamples.size();
        } else {
            firstDetectedAt = detectedInstant.minusSeconds(300L);
            sampleCount = fiveMinuteSamples.size();
        }

        Anomaly anomaly = Anomaly.builder()
                .name("Anomaly_" + equipment.getName() + "_" + sensorType)
                .equipmentId(equipment.getId())
                .recipeParameter(sensorType)
                .severity(ruleResult.severity())
                .lastDetectedAt(detectedInstant)
                .ruleName(ruleResult.ruleName())
                .anomalyType(ruleResult.anomalyType())
                .logType(LogType.SENSOR)
                .firstDetectedAt(firstDetectedAt)
                .sampleCount(sampleCount)
                .detectionReason(ruleResult.reason())
                .measuredValue(ruleResult.measuredValue())
                .referenceValue(ruleResult.referenceValue())
                .deviation(ruleResult.deviation())
                .deviationRate(ruleResult.deviationRate())
                .min(min)
                .max(max)
                .relatedLogIds(null)
                .build();

        Anomaly savedAnomaly = anomalyRepository.save(anomaly);

        log.info(
                "Anomaly log saved. logId={}, equipmentId={}, sensorType={}, severity={}, ruleName={}",
                savedAnomaly.getId(),
                equipment.getId(),
                sensorType,
                savedAnomaly.getSeverity(),
                savedAnomaly.getRuleName()
        );

        AnomalyCreatedPayload payload = AnomalyCreatedPayload.builder()
                .equipmentId(equipment.getId())
                .equipmentName(equipment.getName())
                .recipeParameter(sensorType)
                .severity(savedAnomaly.getSeverity().name())
                .occurredTime(savedAnomaly.getLastDetectedAt())
                .causeRule(savedAnomaly.getRuleName().name())
                .build();

        if (eventPublishEnabled) {
            Event<AnomalyCreatedPayload> event = domainEventFactory.create(
                    AnomalyEventType.ANOMALY_CREATED,
                    "Anomaly",
                    String.valueOf(savedAnomaly.getId()),
                    payload
            );
            publishAfterCommit(event);
        } else {
            log.info(
                    "Skip anomaly event publishing. reason=EVENT_PUBLISH_DISABLED, logId={}",
                    savedAnomaly.getId()
            );
        }

        return Optional.of(savedAnomaly);
    }

    private void publishAfterCommit(Event<?> event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            eventPublisher.publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        eventPublisher.publish(event);
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
