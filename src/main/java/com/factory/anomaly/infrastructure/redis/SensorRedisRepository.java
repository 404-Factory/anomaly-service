package com.factory.anomaly.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class SensorRedisRepository {

    private final StringRedisTemplate redisTemplate;

    public Long getAnomalyCache(String equipmentCode, String sensorType, String ruleName, String anomalyType) {
        String cacheKey = String.format("anomaly:cache:%s:%s:%s:%s", equipmentCode, sensorType, ruleName, anomalyType);
        String val = redisTemplate.opsForValue().get(cacheKey);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public void setAnomalyCache(String equipmentCode, String sensorType, String ruleName, String anomalyType, Long anomalyId, long ttlSeconds) {
        String cacheKey = String.format("anomaly:cache:%s:%s:%s:%s", equipmentCode, sensorType, ruleName, anomalyType);
        redisTemplate.opsForValue().set(cacheKey, String.valueOf(anomalyId), Duration.ofSeconds(ttlSeconds));
    }

    public boolean acquireLock(String equipmentCode, String sensorType, String ruleName, String anomalyType, long expireSeconds) {
        String lockKey = String.format("anomaly:lock:%s:%s:%s:%s", equipmentCode, sensorType, ruleName, anomalyType);
        Boolean success = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(expireSeconds));
        return success != null && success;
    }

    public void releaseLock(String equipmentCode, String sensorType, String ruleName, String anomalyType) {
        String lockKey = String.format("anomaly:lock:%s:%s:%s:%s", equipmentCode, sensorType, ruleName, anomalyType);
        redisTemplate.delete(lockKey);
    }
}
