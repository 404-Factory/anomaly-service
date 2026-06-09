package com.factory.anomaly.engine;

import java.time.OffsetDateTime;

public record RuleSensorSample(
        OffsetDateTime timestamp,
        Double value
) {
}
