package com.factory.anomaly.dto.response;

import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;

import java.time.Instant;

public record AnomalyDetectionResponse(
        Long logId,
        Long equipmentId,
        Long equipmentRecipeId,
        String recipeParameter,
        Severity severity,
        RuleName ruleName,
        AnomalyType anomalyType,
        Instant occurredTime,
        String detectionReason
) {
    public static AnomalyDetectionResponse from(AnomalyLog entity) {
        return new AnomalyDetectionResponse(
                entity.getId(),
                entity.getEquipment() != null ? entity.getEquipment().getId() : null,
                entity.getEquipmentRecipe() != null ? entity.getEquipmentRecipe().getId() : null,
                entity.getRecipeParameter(),
                entity.getSeverity(),
                entity.getRuleName(),
                entity.getAnomalyType(),
                entity.getLastDetectedAt(),
                entity.getDetectionReason()
        );
    }
}
