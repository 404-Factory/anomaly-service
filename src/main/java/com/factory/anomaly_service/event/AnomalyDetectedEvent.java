package com.factory.anomaly_service.event;

public record AnomalyDetectedEvent(
        Long logId,
        String equipmentId,
        String recipeParameter,
        String ruleName,
        String anomalyType,
        String severity,
        String occurredTime
) {
}