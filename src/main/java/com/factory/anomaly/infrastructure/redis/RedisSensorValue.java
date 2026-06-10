package com.factory.anomaly.infrastructure.redis;

public record RedisSensorValue(
        String ts,
        Double value,
        Double min,
        Double max
) {
}
