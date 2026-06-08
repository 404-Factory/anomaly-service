package com.factory.anomaly_service.engine;

import java.time.OffsetDateTime;

public record RuleSensorSample(
        OffsetDateTime timestamp,
        Double value
) {
}