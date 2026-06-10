package com.factory.anomaly.service;

import org.springframework.beans.factory.annotation.Value;
import com.factory.anomaly.engine.RuleEngine;
import com.factory.anomaly.engine.RuleResult;
import com.factory.anomaly.engine.RuleSensorSample;
import com.factory.anomaly.event.payload.producer.AnomalyCreatedPayload;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.redis.SensorSample;
import com.factory.anomaly.infrastructure.repository.AnomalyLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.common.event.domain.EventEnvelope;
import com.factory.common.kafka.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Optional<AnomalyLog> detect(String equipmentCode, String sensorType) {
        return detect(equipmentCode, sensorType, LocalDateTime.now());
    }

    // 여기서 말하는 equipmentCode는 equipmentName, sensorType은 param, detectedAt은 latestTimestamp
    // 한 batch에 대해서
    // param 별로 latestTimestamp 줄 거니까, detect 해주세요
    /// 왜 Optional<AnomalyLog>를 리턴해? 쓰지도 않는데?
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

        /// 뭐하는 애일까요...?
        List<RuleSensorSample> fiveMinuteSamples = toRuleSamples(fiveMinuteRedisSamples);
        List<RuleSensorSample> oneMinuteSamples = toRuleSamples(oneMinuteRedisSamples);

        // equipmentName으로 equipment 가져와
        /// 여기서 대체 뭘 쓰나 보자
        Optional<Equipment> equipmentOptional = equipmentRepository.findByName(equipmentCode);

        if (equipmentOptional.isEmpty()) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentCode={}",
                    equipmentCode
            );
            return Optional.empty();
        }

        Equipment equipment = equipmentOptional.get();

        // equipment Id로 최신 recipe 가져와
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

        // (recipeId, param)으로 recipeDetail 가져와
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

        // recipeDetail이 필요한 이유가 나오겠네?
        // recipeDetail 필요한 1번 이유: recipe min, max를 얻기 위함임
        ///  min, max
        // equipment 1개의 sensor 당 1분에 최대 1개 anomaly 발생 (즉, equipment 1개당 1분 최대 2개 이상)
        // 이건 그냥 business logic 문제임
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

        // 측정된 시간 (latestTimestamp)
        Instant detectedInstant = detectedAt.toInstant(ZoneOffset.UTC);

        // sliding window start네
        Instant firstDetectedAt;
        int sampleCount;
        if (ruleResult.ruleName() == RuleName.NELSON_RULE_3) {
            firstDetectedAt = detectedInstant.minusSeconds(60L);
            sampleCount = oneMinuteSamples.size();
        } else {
            firstDetectedAt = detectedInstant.minusSeconds(300L);
            sampleCount = fiveMinuteSamples.size();
        }

        // snapshot -> 중요한 건 Point
        List<SensorSnapshotDto> snapshots = new ArrayList<>();

        // 현재 선택된 recipe를 가지고 detail을 가져와, 즉 모든 param에 대한 정보 가져와
        List<EquipmentRecipeDetail> recipeDetails = equipmentRecipeDetailRepository.findByEquipmentRecipe_Id(equipmentRecipe.getId());
        // (recipe_equipment_id, param) 별로 순회하게 될 거임 (정확히는 recipe detail이지만, 저게 PK니까)
        for (EquipmentRecipeDetail detail : recipeDetails) {
            // param 가져와 (이건, sensorType에 대응됨)
            String param = detail.getId().getParam();
            // (equipment, param)에 대응되는 key 값 다 가져와
            List<String> keys = sensorRedisRepository.findKeys(equipmentCode, param);
            // 없다고?
            /// DTO 뭘 의미하는 거지?
            if (keys.isEmpty()) {
                snapshots.add(new SensorSnapshotDto(
                        param,
                        param,
                        null,
                        detail.getMin(),
                        detail.getMax(),
                        List.of()
                ));
            } else {
                // 아까 그 key들을 순회
                // sensor:equipment:*:param -> 그냥 해당하는 모든 센서 보겠다는 이야기네?
                for (String key : keys) {
                    String[] parts = key.split(":");
                    ///  자꾸 sensorId와 param이 혼동되네
                    // 그 이유가 놀라운게 진짜 sensorId(name)가 대입되기도, param이 대입되기도 함
                    String sensorId = parts.length > 2 ? parts[2] : param;
                    // lastTimestamp 기준으로 5분전부터 측정값 다 가져와
                    List<SensorSample> samples = sensorRedisRepository.findSamplesByKey(key, detectedAt, FIVE_MINUTES, 0);
                    List<SensorSnapshotDto.Point> points = samples.stream()
                            .map(s -> new SensorSnapshotDto.Point(s.timestamp().toInstant(), s.value()))
                            .toList();
                    // 의도하는 바는 일단 알겠음
                    // 센서별로, 해당 장비 recipe의 min, max, 측정값 전부 드릴게요
                    snapshots.add(new SensorSnapshotDto(
                            sensorId,
                            param,
                            null,
                            detail.getMin(),
                            detail.getMax(),
                            points
                    ));
                }
            }
        }

        // equipment 당 param이 최대 2개
        // param 100개
        // param 별로 detect를 했는데, param 100개에 대한 5분치 데이터가 들어가
        // param1은 param1~param100 까지의 5분치 data json을 가져
        // param2은 param1~param100 까지의 5분치 data json을 가져
        // param3은 param1~param100 까지의 5분치 data json을 가져
        // param4은 param1~param100 까지의 5분치 data json을 가져
        // ...
        // param100은 param1~param100 까지의 5분치 data json을 가져

        String snapshotDataJson = null;
        try {
            snapshotDataJson = objectMapper.writeValueAsString(snapshots);
        } catch (Exception e) {
            log.error("Failed to serialize sensor snapshot data", e);
        }

        // 로그 드릴게요
        // referenceValue는 기준치 (이상의 기준, 예를 들어 min, max를 넘었다던가, 표준편차 관련 값을 넘었다던가) -> threshold
        AnomalyLog anomalyLog = AnomalyLog.builder()
                .equipment(equipment)
                .equipmentRecipe(equipmentRecipe)
                .recipeParameter(sensorType)
                .severity(ruleResult.severity())
                .lastDetectedAt(detectedInstant)
                .ruleName(ruleResult.ruleName())
                .anomalyType(ruleResult.anomalyType())
                .logType(LogType.SENSOR)
                .firstDetectedAt(firstDetectedAt)
                .sampleCount(sampleCount)
                .detectionReason(ruleResult.reason())
                .relatedLogIds(null)
                .measuredValue(ruleResult.measuredValue())
                .referenceValue(ruleResult.referenceValue())
                .deviation(ruleResult.deviation())
                .deviationRate(ruleResult.deviationRate())
                .snapshotData(snapshotDataJson)
                .build();

        AnomalyLog savedAnomalyLog = anomalyLogRepository.save(anomalyLog);

        log.info(
                "Anomaly log saved. logId={}, equipmentId={}, equipmentRecipeId={}, sensorType={}, severity={}, ruleName={}",
                savedAnomalyLog.getId(),
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
                .occurredTime(savedAnomalyLog.getLastDetectedAt())
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
                    savedAnomalyLog.getId()
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

    /// 얘는, 왜 바꿔?
    private List<RuleSensorSample> toRuleSamples(List<SensorSample> redisSamples) {
        return redisSamples.stream()
                .map(sample -> new RuleSensorSample(
                        sample.timestamp(),
                        sample.value()
                ))
                .toList();
    }
}
