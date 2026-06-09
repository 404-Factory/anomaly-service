package com.factory.anomaly.consumer;

import com.factory.anomaly.dto.kafka.SensorDataBatchDto;
import com.factory.anomaly.dto.kafka.MeasurementDto;
import com.factory.anomaly.dto.kafka.SensorReadingDto;
import com.factory.anomaly.service.AnomalyDetectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorDataConsumer {

    private static final int REDIS_WINDOW_SEC = 300;

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final AnomalyDetectionService anomalyDetectionService;

    @KafkaListener(
            topics = "${app.kafka.consumer.sensor-topic:fab-semiconductor-001}",
            groupId = "${spring.kafka.consumer.group-id:anomaly-consumer-group}"
    )
    public void consume(String message) {
        log.info("Received sensor batch message from Kafka");
        try {
            SensorDataBatchDto batch = objectMapper.readValue(message, SensorDataBatchDto.class);
            if (batch.measurements() == null || batch.measurements().isEmpty()) {
                log.warn("Received empty or invalid measurements in batch. batchId={}", batch.batchId());
                return;
            }

            // 1. Flatten and store in Redis using pipeline
            double currentEpochSec = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();
            double cutoff = currentEpochSec - REDIS_WINDOW_SEC;

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (MeasurementDto measurement : batch.measurements()) {
                    String measuredAt = measurement.measuredAt();
                    double score;
                    try {
                        score = OffsetDateTime.parse(measuredAt).toInstant().toEpochMilli() / 1000.0;
                    } catch (Exception e) {
                        log.error("Failed to parse measuredAt: {}", measuredAt, e);
                        continue;
                    }

                    if (measurement.sensors() == null) {
                        continue;
                    }

                    for (SensorReadingDto sensor : measurement.sensors()) {
                        String key = String.format("sensor:%s:%s:%s",
                                batch.equipmentId(),
                                sensor.sensorId(),
                                sensor.sensorType()
                        );

                        Map<String, Object> valueMap = Map.of(
                                "ts", measuredAt,
                                "value", sensor.value()
                        );

                        String jsonValue;
                        try {
                            jsonValue = objectMapper.writeValueAsString(valueMap);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize Redis value map for key: {}", key, e);
                            continue;
                        }

                        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
                        byte[] rawValue = jsonValue.getBytes(StandardCharsets.UTF_8);

                        connection.zSetCommands().zAdd(rawKey, score, rawValue);
                        connection.zSetCommands().zRemRangeByScore(rawKey, Double.NEGATIVE_INFINITY, cutoff);
                        connection.keyCommands().expire(rawKey, REDIS_WINDOW_SEC + 60);
                    }
                }
                return null;
            });

            log.info("Successfully stored batch measurements to Redis. batchId={}, equipmentId={}",
                    batch.batchId(), batch.equipmentId());

            // 2. Trigger anomaly detection for each unique sensor type with the latest timestamp
            Map<String, OffsetDateTime> latestTimestamps = new HashMap<>();

            for (MeasurementDto measurement : batch.measurements()) {
                OffsetDateTime measuredAtTime;
                try {
                    measuredAtTime = OffsetDateTime.parse(measurement.measuredAt());
                } catch (Exception e) {
                    continue;
                }

                if (measurement.sensors() == null) {
                    continue;
                }

                for (SensorReadingDto sensor : measurement.sensors()) {
                    String sensorType = sensor.sensorType();
                    OffsetDateTime existing = latestTimestamps.get(sensorType);
                    if (existing == null || measuredAtTime.isAfter(existing)) {
                        latestTimestamps.put(sensorType, measuredAtTime);
                    }
                }
            }

            for (Map.Entry<String, OffsetDateTime> entry : latestTimestamps.entrySet()) {
                String sensorType = entry.getKey();
                LocalDateTime detectedAtLocal = entry.getValue().toLocalDateTime(); // standard JVM time is UTC
                log.info("Triggering real-time anomaly detection. equipmentId={}, sensorType={}, detectedAt={}",
                        batch.equipmentId(), sensorType, detectedAtLocal);
                try {
                    anomalyDetectionService.detect(batch.equipmentId(), sensorType, detectedAtLocal);
                } catch (Exception e) {
                    log.error("Failed to run real-time anomaly detection for equipmentId={}, sensorType={}",
                            batch.equipmentId(), sensorType, e);
                }
            }

        } catch (Exception e) {
            log.error("Error processing Kafka sensor batch message: {}", message, e);
        }
    }
}
