package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.event.payload.SensorViolationDto;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final SensorRedisRepository sensorRedisRepository;
    private final AnomalyRepository anomalyRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Optional<Anomaly> detect(SensorViolationDto violation) {
        log.info(
                "Start anomaly detection from Flink violation. equipmentCode={}, sensorType={}, ruleName={}, anomalyType={}, detectedAt={}",
                violation.equipmentId(),
                violation.sensorType(),
                violation.ruleName(),
                violation.anomalyType(),
                violation.detectedAt()
        );

        String equipmentCode = violation.equipmentId();
        String sensorType = violation.sensorType();
        String ruleNameStr = violation.ruleName().name();
        String anomalyTypeStr = violation.anomalyType().name();

        // 1. Acquire Distributed Lock (with spin-lock retry)
        boolean isLocked = false;
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            isLocked = sensorRedisRepository.acquireLock(equipmentCode, sensorType, ruleNameStr, anomalyTypeStr, 5); // 5s TTL
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
                    "Failed to acquire distributed lock for anomaly detection. equipmentCode={}, sensorType={}, ruleName={}, anomalyType={}",
                    equipmentCode,
                    sensorType,
                    ruleNameStr,
                    anomalyTypeStr
            );
            return Optional.empty();
        }

        try {
            // 2. Check Redis Cache for Deduplication
            Long cachedAnomalyId = sensorRedisRepository.getAnomalyCache(
                    equipmentCode,
                    sensorType,
                    ruleNameStr,
                    anomalyTypeStr
            );

            if (cachedAnomalyId != null) {
                log.info(
                        "Duplicate anomaly detected (cached). updating existing anomaly logId={}, equipmentCode={}, sensorType={}, ruleName={}, anomalyType={}",
                        cachedAnomalyId,
                        equipmentCode,
                        sensorType,
                        violation.ruleName(),
                        anomalyTypeStr
                );

                Optional<Anomaly> existingAnomalyOpt = anomalyRepository.findById(cachedAnomalyId);
                if (existingAnomalyOpt.isPresent()) {
                    Anomaly existingAnomaly = existingAnomalyOpt.get();
                    existingAnomaly.update(
                            violation.detectedAt(),
                            violation.sampleCount() != null ? violation.sampleCount() : existingAnomaly.getSampleCount(),
                            violation.severity()
                    );
                    Anomaly updatedAnomaly = anomalyRepository.save(existingAnomaly);
                    return Optional.of(updatedAnomaly);
                }
                log.warn("Cached anomaly ID {} not found in database, recreating cache", cachedAnomalyId);
            }

            // 3. Equipment Projection Lookup
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

            // 4. Create and save new Anomaly
            Anomaly anomaly = Anomaly.builder()
                    .name("Anomaly_" + equipment.getName() + "_" + sensorType)
                    .equipmentId(equipment.getId())
                    .recipeParameter(sensorType)
                    .severity(violation.severity())
                    .lastDetectedAt(violation.detectedAt())
                    .ruleName(violation.ruleName())
                    .anomalyType(violation.anomalyType())
                    .logType(LogType.SENSOR)
                    .firstDetectedAt(violation.detectedAt())
                    .sampleCount(violation.sampleCount())
                    .detectionReason(violation.reason())
                    .measuredValue(violation.measuredValue())
                    .referenceValue(violation.referenceValue())
                    .deviation(violation.deviation())
                    .deviationRate(violation.deviationRate())
                    .min(violation.min())
                    .max(violation.max())
                    .relatedLogIds(null)
                    .build();

            Anomaly savedAnomaly = anomalyRepository.save(anomaly);

            log.info(
                    "New anomaly log saved. logId={}, equipmentId={}, sensorType={}, severity={}, ruleName={}, anomalyType={}",
                    savedAnomaly.getId(),
                    equipment.getId(),
                    sensorType,
                    savedAnomaly.getSeverity(),
                    savedAnomaly.getRuleName(),
                    savedAnomaly.getAnomalyType()
            );

            // 5. Cache the anomaly ID in Redis (TTL = 300 seconds)
            sensorRedisRepository.setAnomalyCache(
                    equipmentCode,
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
            sensorRedisRepository.releaseLock(equipmentCode, sensorType, ruleNameStr, anomalyTypeStr);
        }
    }
}

