package com.factory.anomaly_service.domain.dto.request;

import java.time.LocalDateTime;

public record AnomalyDetectionRequest(
        String equipmentCode,
        String sensorType,
        LocalDateTime detectedAt
) {
}