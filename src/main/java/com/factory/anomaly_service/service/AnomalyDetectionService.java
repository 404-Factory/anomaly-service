package com.factory.anomaly_service.service;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.engine.RuleEngine;
import com.factory.anomaly_service.engine.RuleResult;
import com.factory.anomaly_service.engine.RuleSensorSample;
import com.factory.anomaly_service.domain.type.LogType;
import com.factory.anomaly_service.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly_service.infrastructure.redis.SensorSample;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import com.factory.anomaly_service.event.AnomalyDetectedEvent;
import com.factory.anomaly_service.event.AnomalyDetectedEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Redis 센서 시계열 데이터를 조회해 RuleEngine으로 이상 여부를 판단하고,
 * 감지 결과를 센서 단위 ANOMALY_LOG로 저장한다.
 *
 * ALERT 생성, 중복 알림 방지, 설비 단위 대표 심각도 계산은 alert-service의 책임이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnomalyDetectionService {

    private static final int FIVE_MINUTES = 5;
    private static final int ONE_MINUTE = 1;

    private final SensorRedisRepository sensorRedisRepository;
    private final RuleEngine ruleEngine;
    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRepository equipmentRepository;
    private final EquipmentRecipeRepository equipmentRecipeRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;
    private final AnomalyDetectedEventPublisher anomalyDetectedEventPublisher;

    public Optional<AnomalyLogEntity> detect(String equipmentCode, String sensorType) {
        return detect(equipmentCode, sensorType, LocalDateTime.now());
    }

    public Optional<AnomalyLogEntity> detect(
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

        // Redis 조회 DTO를 RuleEngine 전용 입력 모델로 변환해 외부 저장소 의존성을 분리한다.
        List<RuleSensorSample> fiveMinuteSamples = toRuleSamples(fiveMinuteRedisSamples);
        List<RuleSensorSample> oneMinuteSamples = toRuleSamples(oneMinuteRedisSamples);

        Optional<EquipmentEntity> equipmentOptional = equipmentRepository.findByEquipmentName(equipmentCode);

        if (equipmentOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentCode={}",
                    equipmentCode
            );
            return Optional.empty();
        }

        EquipmentEntity equipment = equipmentOptional.get();

        // 현재 DB에는 active recipe 컬럼이 없으므로 가장 높은 version을 현재 적용 Recipe로 간주한다.
        Optional<EquipmentRecipeEntity> equipmentRecipeOptional = equipmentRecipeRepository
                .findTopByEquipment_EquipmentIdOrderByVersionDesc(equipment.getEquipmentId());

        if (equipmentRecipeOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_RECIPE_NOT_FOUND, equipmentId={}, equipmentCode={}",
                    equipment.getEquipmentId(),
                    equipmentCode
            );
            return Optional.empty();
        }

        EquipmentRecipeEntity equipmentRecipe = equipmentRecipeOptional.get();

        Optional<EquipmentRecipeDetailEntity> recipeDetailOptional = equipmentRecipeDetailRepository
                .findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
                        equipmentRecipe.getEquipmentRecipeId(),
                        sensorType
                );

        if (recipeDetailOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=RECIPE_DETAIL_NOT_FOUND, equipmentRecipeId={}, sensorType={}",
                    equipmentRecipe.getEquipmentRecipeId(),
                    sensorType
            );
            return Optional.empty();
        }

        EquipmentRecipeDetailEntity recipeDetail = recipeDetailOptional.get();

        log.info(
                "Recipe threshold loaded. equipmentRecipeId={}, sensorType={}, minValue={}, maxValue={}",
                equipmentRecipe.getEquipmentRecipeId(),
                sensorType,
                recipeDetail.getMinValue(),
                recipeDetail.getMaxValue()
        );

        RuleResult ruleResult = ruleEngine.evaluate(
                fiveMinuteSamples,
                oneMinuteSamples,
                recipeDetail.getMinValue(),
                recipeDetail.getMaxValue()
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

        AnomalyLogEntity anomalyLog = AnomalyLogEntity.builder()
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

        AnomalyLogEntity savedAnomalyLog = anomalyLogRepository.save(anomalyLog);

        log.info(
                "Anomaly log saved. logId={}, equipmentId={}, equipmentRecipeId={}, sensorType={}, severity={}, ruleName={}",
                savedAnomalyLog.getLogId(),
                equipment.getEquipmentId(),
                equipmentRecipe.getEquipmentRecipeId(),
                sensorType,
                savedAnomalyLog.getSeverity(),
                savedAnomalyLog.getRuleName()
        );

        // Kafka AWS/MSK 설정 전까지 로컬 단일 감지 테스트에서는 비활성화
        // anomaly_log -> kafka messaging
//        AnomalyDetectedEvent event = new AnomalyDetectedEvent(
//                savedAnomalyLog.getLogId(),
//                String.valueOf(equipment.getEquipmentId()),
//                savedAnomalyLog.getRecipeParameter(),
//                savedAnomalyLog.getRuleName().name(),
//                savedAnomalyLog.getAnomalyType().name(),
//                savedAnomalyLog.getSeverity().name(),
//                savedAnomalyLog.getOccurredTime().toString()
//        );
//
//        publishAfterCommit(event);

        return Optional.of(savedAnomalyLog);
    }

    private void publishAfterCommit(AnomalyDetectedEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            anomalyDetectedEventPublisher.publish(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        anomalyDetectedEventPublisher.publish(event);
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