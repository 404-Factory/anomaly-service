package com.factory.anomaly_service.domain.dto.response;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;

import java.time.LocalDateTime;

public record AnomalyLogResponse(
        Long logId,
        Long equipmentId,
        Long equipmentRecipeId,
        String recipeParameter,
        Severity severity,
        LocalDateTime occurredTime,
        RuleName ruleName,
        AnomalyType anomalyType,
        LocalDateTime windowStartTime,
        Integer sampleCount,
        String detectionReason
) {
    public static AnomalyLogResponse from(AnomalyLogEntity entity) {
        return new AnomalyLogResponse(
                entity.getLogId(),
                entity.getEquipment() != null ? entity.getEquipment().getEquipmentId() : null,
                entity.getEquipmentRecipe() != null ? entity.getEquipmentRecipe().getEquipmentRecipeId() : null,
                entity.getRecipeParameter(),
                entity.getSeverity(),
                entity.getOccurredTime(),
                entity.getRuleName(),
                entity.getAnomalyType(),
                entity.getWindowStartTime(),
                entity.getSampleCount(),
                entity.getDetectionReason()
        );
    }
}