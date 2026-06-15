package com.factory.anomaly.service;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.event.payload.consumer.SensorViolationPayload;
import com.factory.anomaly.event.payload.producer.AnomalyCreatedPayload;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.factory.common.event.domain.Event;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.kafka.publisher.EventPublisher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnomalyCreationServiceImpl implements AnomalyCreationService {

    private final SensorRedisRepository sensorRedisRepository;
    private final AnomalyRepository anomalyRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Optional<Anomaly> create(SensorViolationPayload violation) {
        log.info(
                "Start anomaly detection from Flink violation. equipmentId={}, sensorType={}, ruleName={}, anomalyType={}, detectedAt={}",
                violation.getEquipmentId(),
                violation.getSensorType(),
                violation.getRuleName(),
                violation.getAnomalyType(),
                violation.getDetectedAt()
        );

        Long equipmentId = violation.getEquipmentId();
        String sensorType = violation.getSensorType();
        String ruleNameStr = violation.getRuleName().name();
        String anomalyTypeStr = violation.getAnomalyType().name();

        // 1. Acquire Distributed Lock (with spin-lock retry)
        boolean isLocked = false;
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            isLocked = sensorRedisRepository.acquireLock(equipmentId, sensorType, ruleNameStr, anomalyTypeStr, 5); // 5s TTL
            if (isLocked) {
                break;
            }
            retryCount++;
            try {
                Thread.sleep(50); // wait 50ms before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Distributed lock retry interrupted", e);
                return Optional.empty();
            }
        }

        if (!isLocked) {
            log.error(
                    "Failed to acquire distributed lock for anomaly detection. equipmentId={}, sensorType={}, ruleName={}, anomalyType={}",
                    equipmentId,
                    sensorType,
                    ruleNameStr,
                    anomalyTypeStr
            );
            return Optional.empty();
        }

        try {
            // 2. Check Redis Cache for Deduplication
            Long cachedAnomalyId = sensorRedisRepository.getAnomalyCache(
                    equipmentId,
                    sensorType,
                    ruleNameStr,
                    anomalyTypeStr
            );

            if (cachedAnomalyId != null) {
                log.info(
                        "Duplicate anomaly detected (cached). updating existing anomaly logId={}, equipmentId={}, sensorType={}, ruleName={}, anomalyType={}",
                        cachedAnomalyId,
                        equipmentId,
                        sensorType,
                        violation.getRuleName(),
                        anomalyTypeStr
                );

                Optional<Anomaly> existingAnomalyOpt = anomalyRepository.findById(cachedAnomalyId);
                if (existingAnomalyOpt.isPresent()) {
                    Anomaly existingAnomaly = existingAnomalyOpt.get();
                    existingAnomaly.update(
                            violation.getDetectedAt(),
                            violation.getSampleCount() != null ? violation.getSampleCount() : existingAnomaly.getSampleCount(),
                            violation.getSeverity()
                    );
                    Anomaly updatedAnomaly = anomalyRepository.save(existingAnomaly);
                    return Optional.of(updatedAnomaly);
                }
                log.warn("Cached anomaly ID {} not found in database, recreating cache", cachedAnomalyId);
            }

            // 3. Equipment Projection Lookup
            EquipmentProjection equipment = null;
            try {
                equipment = equipmentProjectionRepository.findById(equipmentId).orElse(null);
            } catch (NumberFormatException e) {
                log.debug("equipmentId is not numeric ID, falling back to name search: {}", equipmentId);
            }

            if (equipment == null) {
                log.warn(
                        "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentId={}",
                        equipmentId
                );
                return Optional.empty();
            }

            // 4. Create and save new Anomaly
            Anomaly anomaly = Anomaly.builder()
                    .name("Anomaly_" + equipment.getName() + "_" + sensorType)
                    .equipmentId(violation.getEquipmentId())
                    .recipeParameter(sensorType)
                    .severity(violation.getSeverity())
                    .lastDetectedAt(violation.getDetectedAt())
                    .ruleName(violation.getRuleName())
                    .anomalyType(violation.getAnomalyType())
                    .logType(LogType.SENSOR)
                    .firstDetectedAt(violation.getDetectedAt())
                    .sampleCount(violation.getSampleCount())
                    .detectionReason(violation.getReason())
                    .measuredValue(violation.getMeasuredValue())
                    .referenceValue(violation.getReferenceValue())
                    .deviation(violation.getDeviation())
                    .deviationRate(violation.getDeviationRate())
                    .min(violation.getMin())
                    .max(violation.getMax())
                    .relatedLogIds(null)
                    .build();

            Anomaly savedAnomaly = anomalyRepository.save(anomaly);

            log.info(
                    "New anomaly log saved. logId={}, equipmentId={}, sensorType={}, severity={}, ruleName={}, anomalyType={}",
                    savedAnomaly.getId(),
                    savedAnomaly.getEquipmentId(),
                    sensorType,
                    savedAnomaly.getSeverity(),
                    savedAnomaly.getRuleName(),
                    savedAnomaly.getAnomalyType()
            );

            // 5. Cache the anomaly ID in Redis (TTL = 300 seconds)
            sensorRedisRepository.setAnomalyCache(
                    equipmentId,
                    sensorType,
                    ruleNameStr,
                    anomalyTypeStr,
                    savedAnomaly.getId(),
                    300
            );

            // 6. Publish Event via Transactional Outbox (EventPublisher)
            if (eventPublishEnabled) {
                AnomalyCreatedPayload payload = AnomalyCreatedPayload.builder()
                        .anomalyLogId(savedAnomaly.getId())
                        .equipmentId(equipment.getId())
                        .equipmentName(equipment.getName())
                        .recipeParameter(sensorType)
                        .severity(savedAnomaly.getSeverity().name())
                        .occurredTime(savedAnomaly.getLastDetectedAt())
                        .causeRule(savedAnomaly.getRuleName().name())
                        .detectionReason(savedAnomaly.getDetectionReason())
                        .build();

                Event<AnomalyCreatedPayload> event = domainEventFactory.create(
                        AnomalyEventType.ANOMALY_CREATED,
                        "Anomaly",
                        String.valueOf(savedAnomaly.getId()),
                        payload
                );
                eventPublisher.publish(event);
            } else {
                log.info(
                        "Skip anomaly event publishing. reason=EVENT_PUBLISH_DISABLED, logId={}",
                        savedAnomaly.getId()
                );
            }

            return Optional.of(savedAnomaly);

        } finally {
            // 7. Always release the lock
            sensorRedisRepository.releaseLock(equipmentId, sensorType, ruleNameStr, anomalyTypeStr);
        }
    }
}
