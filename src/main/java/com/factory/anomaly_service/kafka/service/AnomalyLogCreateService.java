package com.factory.anomaly_service.kafka.service;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.entity.EquipmentEntity;
import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import com.factory.anomaly_service.kafka.dto.AnomalyCreatedPayload;
import com.factory.anomaly_service.kafka.dto.SensorViolationPayload;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import com.factory.anomaly_service.repository.EquipmentRepository;
import com.factory.common.event.domain.DomainEvent;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
    private final EquipmentRepository equipmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void create(SensorViolationPayload payload) {
        Long equipmentId = Long.parseLong(payload.getEquipmentId());
        EquipmentEntity equipment = equipmentRepository.findById(equipmentId).orElse(null);

        AnomalyLogEntity anomalyLog = AnomalyLogEntity.builder()
            .equipment(equipment)
            .recipeParameter(payload.getSensorType())
            .severity(payload.getSeverity() != null ? Severity.valueOf(payload.getSeverity()) : null)
            .occurredTime(payload.getDetectedAt() != null
                ? LocalDateTime.ofInstant(payload.getDetectedAt(), ZoneOffset.UTC) : null)
            .ruleName(payload.getRuleName() != null ? RuleName.valueOf(payload.getRuleName()) : null)
            .anomalyType(payload.getAnomalyType() != null ? AnomalyType.valueOf(payload.getAnomalyType()) : null)
            .sampleCount(payload.getSampleCount())
            .detectionReason(payload.getReason())
            .build();

        AnomalyLogEntity saved = anomalyLogRepository.save(anomalyLog);
        log.info("AnomalyLog saved: logId={}", saved.getLogId());

        AnomalyCreatedPayload eventPayload = AnomalyCreatedPayload.builder()
            .anomalyLogId(saved.getLogId())
            .equipmentId(equipmentId)
            .equipmentName(equipment != null ? equipment.getEquipmentName() : null)
            .recipeParameter(payload.getSensorType())
            .severity(payload.getSeverity())
            .occurredTime(payload.getDetectedAt())
            .causeRule(payload.getRuleName())
            .anomalyType(payload.getAnomalyType())
            .sampleCount(payload.getSampleCount())
            .detectionReason(payload.getReason())
            .build();

        eventPublisher.publishEvent(
            DomainEvent.of(
                UUID.randomUUID().toString(),
                () -> "AnomalyCreated",
                "AnomalyLog",
                saved.getLogId().toString(),
                eventPayload,
                null
            )
        );
    }
}
