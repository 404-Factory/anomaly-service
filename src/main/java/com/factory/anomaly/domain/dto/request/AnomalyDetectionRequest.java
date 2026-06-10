package com.factory.anomaly.domain.dto.request;

import java.time.LocalDateTime;

public record AnomalyDetectionRequest(
        String equipmentCode,
        String sensorType,
        LocalDateTime detectedAt
) {
}
