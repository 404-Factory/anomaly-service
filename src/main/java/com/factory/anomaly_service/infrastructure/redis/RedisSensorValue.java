package com.factory.anomaly_service.infrastructure.redis;

public record RedisSensorValue(
        String ts,
        Double value
) {
}