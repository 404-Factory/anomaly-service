package com.factory.anomaly_service.infrastructure.redis;

import org.springframework.stereotype.Component;

@Component
public class SensorRedisKeyResolver {

    private static final String SENSOR_KEY_PREFIX = "sensor";

    public String buildSensorKeyPattern(String equipmentCode, String sensorType) {
        return String.format("%s:%s:*:%s", SENSOR_KEY_PREFIX, equipmentCode, sensorType);
    }
}