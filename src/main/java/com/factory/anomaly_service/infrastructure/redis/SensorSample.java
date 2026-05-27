package com.factory.anomaly_service.infrastructure.redis;

import java.time.OffsetDateTime;

public record SensorSample(
        OffsetDateTime timestamp,
        Double value
) {
}