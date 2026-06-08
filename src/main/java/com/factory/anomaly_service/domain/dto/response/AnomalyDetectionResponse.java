package com.factory.anomaly_service.domain.dto.response;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;

import java.time.LocalDateTime;

public record AnomalyDetectionResponse(
        Long logId,
        Long equipmentId,
        Long equipmentRecipeId,
        String recipeParameter,
        Severity severity,
        RuleName ruleName,
        AnomalyType anomalyType,
        LocalDateTime occurredTime,
        String detectionReason
) {
    public static AnomalyDetectionResponse from(AnomalyLogEntity entity) {
        return new AnomalyDetectionResponse(
                entity.getLogId(),
                entity.getEquipment() != null ? entity.getEquipment().getEquipmentId() : null,
                entity.getEquipmentRecipe() != null ? entity.getEquipmentRecipe().getEquipmentRecipeId() : null,
                entity.getRecipeParameter(),
                entity.getSeverity(),
                entity.getRuleName(),
                entity.getAnomalyType(),
                entity.getOccurredTime(),
                entity.getDetectionReason()
        );
    }
}