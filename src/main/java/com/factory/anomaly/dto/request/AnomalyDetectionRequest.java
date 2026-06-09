package com.factory.anomaly.dto.request;

import java.time.LocalDateTime;

public record AnomalyDetectionRequest(
        String equipmentCode,
        String sensorType,
        LocalDateTime detectedAt
) {
}
