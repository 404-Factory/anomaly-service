package com.factory.anomaly.infrastructure.redis;

import java.time.OffsetDateTime;

public record SensorSample(
        OffsetDateTime timestamp,
        Double value,
        Double min,
        Double max
) {
}
