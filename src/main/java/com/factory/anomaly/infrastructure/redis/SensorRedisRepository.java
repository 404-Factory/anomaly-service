package com.factory.anomaly.infrastructure.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class SensorRedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SensorRedisKeyResolver sensorRedisKeyResolver;

    @Value("${app.time-zone:Asia/Seoul}")
    private String timeZone;

    public List<SensorSample> findSamples(
            String equipmentCode,
            String sensorType,
            LocalDateTime occurredTime,
            int beforeMinutes,
            int afterMinutes
    ) {
        String keyPattern = sensorRedisKeyResolver.buildSensorKeyPattern(equipmentCode, sensorType);
        List<String> keys = scanKeys(keyPattern);

        if (keys.isEmpty()) {
            return List.of();
        }

        double startScore = toEpochSecond(occurredTime.minusMinutes(beforeMinutes));
        double endScore = toEpochSecond(occurredTime.plusMinutes(afterMinutes));

        return keys.stream()
                .flatMap(key -> findSamplesByKeyAndScoreRange(key, startScore, endScore).stream())
                .sorted(Comparator.comparing(SensorSample::timestamp))
                .toList();
    }

    private List<SensorSample> findSamplesByKeyAndScoreRange(
            String key,
            double startScore,
            double endScore
    ) {
        Set<String> rawValues = redisTemplate.opsForZSet()
                .rangeByScore(key, startScore, endScore);

        if (rawValues == null || rawValues.isEmpty()) {
            return List.of();
        }

        return rawValues.stream()
                .map(this::parseSensorSample)
                .toList();
    }

    private SensorSample parseSensorSample(String rawValue) {
        try {
            RedisSensorValue redisSensorValue = objectMapper.readValue(rawValue, RedisSensorValue.class);

            return new SensorSample(
                    OffsetDateTime.parse(redisSensorValue.ts()),
                    redisSensorValue.value()
            );
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Redis 센서 데이터 파싱에 실패했습니다. rawValue=" + rawValue, e);
        }
    }

    private List<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisConnection connection) -> {
            List<String> keys = new ArrayList<>();

            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                cursor.forEachRemaining(key ->
                        keys.add(new String(key, StandardCharsets.UTF_8))
                );
            }

            return keys;
        });
    }

    private double toEpochSecond(LocalDateTime dateTime) {
        return dateTime.atZone(ZoneId.of(timeZone)).toEpochSecond();
    }
}
