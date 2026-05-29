package com.factory.anomaly_service.service;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.entity.EquipmentEntity;
import com.factory.anomaly_service.domain.entity.EquipmentRecipeDetailEntity;
import com.factory.anomaly_service.domain.entity.EquipmentRecipeEntity;
import com.factory.anomaly_service.engine.RuleEngine;
import com.factory.anomaly_service.engine.RuleResult;
import com.factory.anomaly_service.engine.RuleSensorSample;
import com.factory.anomaly_service.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly_service.infrastructure.redis.SensorSample;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import com.factory.anomaly_service.repository.EquipmentRecipeDetailRepository;
import com.factory.anomaly_service.repository.EquipmentRecipeRepository;
import com.factory.anomaly_service.repository.EquipmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    public Optional<AnomalyLogEntity> detect(String equipmentCode, String sensorType) {
        return detect(equipmentCode, sensorType, LocalDateTime.now());
    }

    public Optional<AnomalyLogEntity> detect(
            String equipmentCode,
            String sensorType,
            LocalDateTime detectedAt
    ) {
        List<SensorSample> fiveMinuteRedisSamples = sensorRedisRepository.findSamples(
                equipmentCode,
                sensorType,
                detectedAt,
                FIVE_MINUTES,
                0
        );

        if (fiveMinuteRedisSamples.isEmpty()) {
            return Optional.empty();
        }

        List<SensorSample> oneMinuteRedisSamples = sensorRedisRepository.findSamples(
                equipmentCode,
                sensorType,
                detectedAt,
                ONE_MINUTE,
                0
        );

        List<RuleSensorSample> fiveMinuteSamples = toRuleSamples(fiveMinuteRedisSamples);
        List<RuleSensorSample> oneMinuteSamples = toRuleSamples(oneMinuteRedisSamples);

        Optional<EquipmentEntity> equipmentOptional = equipmentRepository.findByEquipmentName(equipmentCode);

        if (equipmentOptional.isEmpty()) {
            return Optional.empty();
        }

        EquipmentEntity equipment = equipmentOptional.get();

        Optional<EquipmentRecipeEntity> equipmentRecipeOptional = equipmentRecipeRepository
                .findTopByEquipment_EquipmentIdOrderByVersionDesc(equipment.getEquipmentId());

        if (equipmentRecipeOptional.isEmpty()) {
            return Optional.empty();
        }

        EquipmentRecipeEntity equipmentRecipe = equipmentRecipeOptional.get();

        Optional<EquipmentRecipeDetailEntity> recipeDetailOptional = equipmentRecipeDetailRepository
                .findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
                        equipmentRecipe.getEquipmentRecipeId(),
                        sensorType
                );

        if (recipeDetailOptional.isEmpty()) {
            return Optional.empty();
        }

        EquipmentRecipeDetailEntity recipeDetail = recipeDetailOptional.get();

        RuleResult ruleResult = ruleEngine.evaluate(
                fiveMinuteSamples,
                oneMinuteSamples,
                recipeDetail.getMinValue(),
                recipeDetail.getMaxValue()
        );

        if (!ruleResult.detected()) {
            return Optional.empty();
        }

        AnomalyLogEntity anomalyLog = AnomalyLogEntity.builder()
                .equipment(equipment)
                .equipmentRecipe(equipmentRecipe)
                .recipeParameter(sensorType)
                .severity(ruleResult.severity())
                .occurredTime(detectedAt)
                .ruleName(ruleResult.ruleName())
                .anomalyType(ruleResult.anomalyType())
                .windowStartTime(detectedAt.minusMinutes(FIVE_MINUTES))
                .sampleCount(fiveMinuteSamples.size())
                .detectionReason(ruleResult.reason())
                .build();

        return Optional.of(anomalyLogRepository.save(anomalyLog));
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