package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.domain.enums.AnalysisStatus;
import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.event.payload.producer.AnomalyCreatedPayload;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.entity.Violation;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.factory.anomaly.infrastructure.repository.ViolationRepository;
import com.factory.common.event.domain.Event;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.kafka.publisher.EventPublisher;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyServiceImpl implements AnomalyService {

    private final AnomalyRepository anomalyRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final SensorRedisRepository sensorRedisRepository;
    private final ViolationRepository violationRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;
    private final AnalysisService analysisService;

    @Lazy
    @Autowired
    private AnomalyService self;

    @Value("${app.event.publish-enabled:false}")
    private boolean eventPublishEnabled;

    @Override
    public Page<AnomalyResponse> getAnomalies(Long processId, Long equipmentId, String keyword, Pageable pageable) {
        return anomalyRepository.fetchAnomaliesWithCondition(processId, equipmentId, keyword, pageable);
    }

    @Override
    @Transactional
    public AnomalyDetailResponse getAnomaly(Long anomalyId) {
        AnomalyDetailResponse response = anomalyRepository.fetchAnomaly(anomalyId);
        if (response == null) {
            throw new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND);
        }

        // Auto-trigger AI analysis if it does not exist
        if (response.getAnalysisStatus() == null) {
            log.info("Analysis record not found for anomaly {}. Auto-triggering AI analysis.", anomalyId);
            try {
                analysisService.triggerAnalysis(anomalyId);
                response.setAnalysisStatus(AnalysisStatus.RUNNING.name());
            } catch (Exception e) {
                log.error("Failed to auto-trigger AI analysis for anomaly {}", anomalyId, e);
            }
        }

        return response;
    }

    @Override
    public Optional<Anomaly> detect(SensorViolationDto violation) {
        log.info(
                "Start anomaly detection from Flink violation. equipmentId={}, sensorId={}, ruleName={}, anomalyType={}, detectedAt={}",
                violation.equipmentId(),
                violation.sensorId(),
                violation.ruleName(),
                violation.anomalyType(),
                violation.detectedAt()
        );

        Long equipmentId = violation.equipmentId();
        String equipmentCode = String.valueOf(equipmentId);
        String sensorId = violation.sensorId();
        String ruleNameStr = violation.ruleName().name();
        String anomalyTypeStr = violation.anomalyType().name();

        // 1. Acquire Distributed Lock (with spin-lock retry)
        boolean isLocked = false;
        int maxRetries = 10;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            isLocked = sensorRedisRepository.acquireLock(equipmentCode, sensorId, ruleNameStr, anomalyTypeStr, 5); // 5s TTL
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
                    "Failed to acquire distributed lock for anomaly detection. equipmentId={}, sensorId={}, ruleName={}, anomalyType={}",
                    equipmentId,
                    sensorId,
                    ruleNameStr,
                    anomalyTypeStr
            );
            return Optional.empty();
        }

        try {
            // 2. Delegate to REQUIRES_NEW transactional method so DB commits before lock release
            return self.processAnomalyDetectionInTransaction(
                    violation, equipmentId, equipmentCode, sensorId, ruleNameStr, anomalyTypeStr
            );
        } finally {
            // 3. Always release the lock
            sensorRedisRepository.releaseLock(equipmentCode, sensorId, ruleNameStr, anomalyTypeStr);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Anomaly> processAnomalyDetectionInTransaction(
            SensorViolationDto violation,
            Long equipmentId,
            String equipmentCode,
            String sensorId,
            String ruleNameStr,
            String anomalyTypeStr
    ) {
        // 1. Check Redis Cache for Deduplication
        Long cachedAnomalyId = sensorRedisRepository.getAnomalyCache(
                equipmentCode,
                sensorId,
                ruleNameStr,
                anomalyTypeStr
        );

        if (cachedAnomalyId != null) {
            log.info(
                    "Duplicate anomaly detected (cached). updating existing anomaly logId={}, equipmentId={}, sensorId={}, ruleName={}, anomalyType={}",
                    cachedAnomalyId,
                    equipmentId,
                    sensorId,
                    violation.ruleName(),
                    anomalyTypeStr
            );

            Optional<Anomaly> existingAnomalyOpt = anomalyRepository.findById(cachedAnomalyId);
            if (existingAnomalyOpt.isPresent()) {
                Anomaly existingAnomaly = existingAnomalyOpt.get();
                
                // Physical recovery: if Flink sends severity as null, it means the rules are no longer violated.
                if (violation.severity() == null) {
                    log.info("Anomaly {} is physically recovered (severity is null). Resolving anomaly.", cachedAnomalyId);
                    existingAnomaly.resolve();
                    Anomaly updatedAnomaly = anomalyRepository.save(existingAnomaly);
                    sensorRedisRepository.deleteAnomalyCache(equipmentCode, sensorId, ruleNameStr, anomalyTypeStr);
                    return Optional.of(updatedAnomaly);
                }
                
                // Safety filter in case cache is present but database state is already RESOLVED
                if ("RESOLVED".equals(existingAnomaly.getStatus())) {
                    log.info("Anomaly {} is already RESOLVED. Skipping update for trailing window event.", cachedAnomalyId);
                    return Optional.empty();
                }

                existingAnomaly.update(
                        violation.detectedAt(),
                        violation.sampleCount() != null ? violation.sampleCount() : existingAnomaly.getSampleCount(),
                        violation.severity()
                );

                // Add 1:N Violation record
                Violation violationEntity = Violation.builder()
                        .equipmentId(violation.equipmentId())
                        .sensorId(violation.sensorId())
                        .severity(violation.severity() != null ? violation.severity().name() : "NORMAL")
                        .detectedAt(violation.detectedAt())
                        .value(violation.measuredValue())
                        .referenceValue(violation.referenceValue())
                        .deviation(violation.deviation())
                        .deviationRate(violation.deviationRate())
                        .description(violation.reason())
                        .build();
                existingAnomaly.addViolation(violationEntity);

                Anomaly updatedAnomaly = anomalyRepository.save(existingAnomaly);
                return Optional.of(updatedAnomaly);
            }
            log.warn("Cached anomaly ID {} not found in database, recreating cache", cachedAnomalyId);
        }

        // [Timestamp Validation Filter] Check if there's a recently resolved anomaly for this sensor (using sensorType/recipeParameter)
        String sensorType = violation.sensorType();
        Optional<Anomaly> latestResolvedOpt = anomalyRepository
                .findFirstByEquipmentIdAndRecipeParameterAndStatusOrderByIdDesc(equipmentId, sensorType, "RESOLVED");

        if (latestResolvedOpt.isPresent()) {
            Anomaly latestResolved = latestResolvedOpt.get();
            Instant resolvedAt = latestResolved.getLastDetectedAt();
            if (violation.detectedAt().isBefore(resolvedAt) || violation.detectedAt().equals(resolvedAt)) {
                log.info(
                        "Skip trailing violation event from sliding window. detectedAt={}, resolvedAt={}, equipmentId={}, sensorType={}",
                        violation.detectedAt(),
                        resolvedAt,
                        equipmentId,
                        sensorType
                );
                return Optional.empty();
            }
        }

        // 2. Equipment Projection Lookup
        EquipmentProjection equipment = equipmentProjectionRepository.findById(equipmentId).orElse(null);
        if (equipment == null) {
            equipment = equipmentProjectionRepository.findByName(equipmentCode).orElse(null);
        }

        if (equipment == null) {
            log.warn(
                    "Skip anomaly detection. reason=EQUIPMENT_NOT_FOUND, equipmentId={}",
                    equipmentId
            );
            return Optional.empty();
        }

        // 3. Create and save new Anomaly
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
                .status("ACTIVE")
                .build();

        // Create and add initial Violation
        Violation violationEntity = Violation.builder()
                .equipmentId(violation.equipmentId())
                .sensorId(violation.sensorId())
                .severity(violation.severity().name())
                .detectedAt(violation.detectedAt())
                .value(violation.measuredValue())
                .referenceValue(violation.referenceValue())
                .deviation(violation.deviation())
                .deviationRate(violation.deviationRate())
                .description(violation.reason())
                .build();
        anomaly.addViolation(violationEntity);

        Anomaly savedAnomaly = anomalyRepository.save(anomaly);

        log.info(
                "New anomaly log saved. logId={}, equipmentId={}, sensorId={}, severity={}, ruleName={}, anomalyType={}",
                savedAnomaly.getId(),
                equipment.getId(),
                sensorId,
                savedAnomaly.getSeverity(),
                savedAnomaly.getRuleName(),
                savedAnomaly.getAnomalyType()
        );

        // 4. Cache the anomaly ID in Redis (TTL = 300 seconds)
        sensorRedisRepository.setAnomalyCache(
                equipmentCode,
                sensorId,
                ruleNameStr,
                anomalyTypeStr,
                savedAnomaly.getId(),
                300
        );

        // 5. Publish Event via Transactional Outbox (EventPublisher) directly in transaction
        if (eventPublishEnabled) {
            AnomalyCreatedPayload payload = AnomalyCreatedPayload.builder()
                    .equipmentId(equipment.getId())
                    .equipmentName(equipment.getName())
                    .recipeParameter(sensorType)
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
            eventPublisher.publish(event);
        } else {
            log.info(
                    "Skip anomaly event publishing. reason=EVENT_PUBLISH_DISABLED, logId={}",
                    savedAnomaly.getId()
            );
        }

        return Optional.of(savedAnomaly);
    }
}
