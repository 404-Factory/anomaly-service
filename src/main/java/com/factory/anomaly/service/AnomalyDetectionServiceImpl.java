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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

    @Override
    public Optional<Anomaly> detect(SensorViolationDto violation) {
        log.info(
                "Start anomaly detection from Flink violation. equipmentCode={}, sensorType={}, ruleName={}, detectedAt={}",
                violation.equipmentId(),
                violation.sensorType(),
                violation.ruleName(),
                violation.detectedAt()
        );

        // 1. Check Redis Cache for Deduplication
        Long cachedAnomalyId = sensorRedisRepository.getAnomalyCache(
                violation.equipmentId(),
                violation.sensorType(),
                violation.ruleName().name()
        );

        if (cachedAnomalyId != null) {
            log.info(
                    "Duplicate anomaly detected (cached). updating existing anomaly logId={}, equipmentCode={}, sensorType={}, ruleName={}",
                    cachedAnomalyId,
                    violation.equipmentId(),
                    violation.sensorType(),
                    violation.ruleName()
            );

            Optional<Anomaly> existingAnomalyOpt = anomalyRepository.findById(cachedAnomalyId);
            if (existingAnomalyOpt.isPresent()) {
                Anomaly existingAnomaly = existingAnomalyOpt.get();
                existingAnomaly.update(
                        violation.detectedAt(),
                        violation.sampleCount() != null ? violation.sampleCount() : existingAnomaly.getSampleCount()
                );
                Anomaly updatedAnomaly = anomalyRepository.save(existingAnomaly);
                return Optional.of(updatedAnomaly);
            }
            log.warn("Cached anomaly ID {} not found in database, recreating cache", cachedAnomalyId);
        }

        // 2. Equipment Projection Lookup
        Long equipmentId = null;
        EquipmentProjection equipment = null;
        try {
            equipmentId = Long.parseLong(violation.equipmentId());
            equipment = equipmentProjectionRepository.findById(equipmentId).orElse(null);
        } catch (NumberFormatException e) {
            log.debug("equipmentCode is not numeric ID, falling back to name search: {}", violation.equipmentId());
        }

        if (equipment == null) {
            equipment = equipmentProjectionRepository.findByName(violation.equipmentId()).orElse(null);
        }

        if (equipment == null) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentCode={}",
                    violation.equipmentId()
            );
            return Optional.empty();
        }

        // 3. Create and save new Anomaly
        Anomaly anomaly = Anomaly.builder()
                .name("Anomaly_" + equipment.getName() + "_" + violation.sensorType())
                .equipmentId(equipment.getId())
                .recipeParameter(violation.sensorType())
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
                "New anomaly log saved. logId={}, equipmentId={}, sensorType={}, severity={}, ruleName={}",
                savedAnomaly.getId(),
                equipment.getId(),
                violation.sensorType(),
                savedAnomaly.getSeverity(),
                savedAnomaly.getRuleName()
        );

        // 4. Cache the anomaly ID in Redis (TTL = 300 seconds)
        sensorRedisRepository.setAnomalyCache(
                violation.equipmentId(),
                violation.sensorType(),
                violation.ruleName().name(),
                savedAnomaly.getId(),
                300
        );

        // 5. Publish Event via Transactional Outbox (EventPublisher)
        if (eventPublishEnabled) {
            AnomalyCreatedPayload payload = AnomalyCreatedPayload.builder()
                    .equipmentId(equipment.getId())
                    .equipmentName(equipment.getName())
                    .recipeParameter(violation.sensorType())
                    .severity(savedAnomaly.getSeverity().name())
                    .occurredTime(savedAnomaly.getLastDetectedAt())
                    .causeRule(savedAnomaly.getRuleName().name())
                    .build();

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
}

