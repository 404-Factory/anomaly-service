package com.factory.anomaly_service.kafka.service;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import com.factory.anomaly_service.kafka.dto.AnomalyCreatedPayload;
import com.factory.anomaly_service.kafka.dto.SensorViolationPayload;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import com.factory.common.event.support.DomainEventFactory;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyLogCreateService {

    private final AnomalyLogRepository anomalyLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Transactional
    public void create(SensorViolationPayload payload) {
        AnomalyLogEntity anomalyLog = AnomalyLogEntity.builder()
            .recipeParameter(payload.sensorType())
            .severity(payload.severity() != null ? Severity.valueOf(payload.severity()) : null)
            .occurredTime(payload.detectedAt() != null
                ? LocalDateTime.ofInstant(payload.detectedAt(), ZoneOffset.UTC) : null)
            .ruleName(payload.ruleName() != null ? RuleName.valueOf(payload.ruleName()) : null)
            .anomalyType(payload.anomalyType() != null ? AnomalyType.valueOf(payload.anomalyType()) : null)
            .sampleCount(payload.sampleCount())
            .detectionReason(payload.reason())
            .build();

        AnomalyLogEntity saved = anomalyLogRepository.save(anomalyLog);
        log.info("AnomalyLog saved: logId={}", saved.getLogId());

        AnomalyCreatedPayload eventPayload = AnomalyCreatedPayload.builder()
            .anomalyLogId(saved.getLogId())
            .equipmentId(payload.equipmentId())
            .recipeParameter(payload.sensorType())
            .severity(payload.severity())
            .occurredTime(payload.detectedAt())
            .causeRule(payload.ruleName())
            .anomalyType(payload.anomalyType())
            .sampleCount(payload.sampleCount())
            .detectionReason(payload.reason())
            .build();

        eventPublisher.publishEvent(
            domainEventFactory.create(
                () -> "AnomalyCreated",
                "AnomalyLog",
                saved.getLogId().toString(),
                eventPayload
            )
        );
    }
}
