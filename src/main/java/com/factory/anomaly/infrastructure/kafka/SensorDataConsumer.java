package com.factory.anomaly.infrastructure.kafka;

import com.factory.anomaly.event.payload.MeasurementDto;
import com.factory.anomaly.event.payload.SensorDataBatchDto;
import com.factory.anomaly.event.payload.SensorReadingDto;
import com.factory.anomaly.service.AnomalyDetectionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
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
    // equipment별 batch 하나를 보고 있음
    // 아마 equipment가 key가 될 거거든
    public void consume(String message) {
        log.info("Received sensor batch message from Kafka");
        try {
            // SensorDataBatchDto 변환
            SensorDataBatchDto batch = objectMapper.readValue(message, SensorDataBatchDto.class);
            // 측정 정보가 없어 -> 끝
            if (batch.measurements() == null || batch.measurements().isEmpty()) {
                log.warn("Received empty or invalid measurements in batch. batchId={}",
                    batch.batchId());
                return;
            }

            // 1. Flatten and store in Redis using pipeline

            /// OffsetDateTime.now()는 왜 있지? -> batch.createdAt 비교 예정
            // OffsetDateTime.now()는 현재시간-5분을 기준으로 5분간의 데이터를 보기 위한 값
            // batch에 createdAt이라는 정보가 있는데 왜 굳이 이걸 썼어야만 하나?
            // 근데 중요하진 않음
            double currentEpochSec = OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond();
            double cutoff = currentEpochSec - REDIS_WINDOW_SEC;

            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (MeasurementDto measurement : batch.measurements()) {
                    String measuredAt = measurement.measuredAt();
                    double score;
                    try {
                        /// 1781008800.123 형태 -> 왜? 그냥 EpochMilli만 쓰지?
                        // 근데 일단 중요하진 않음 그냥 따라
                        score =
                            OffsetDateTime.parse(measuredAt).toInstant().toEpochMilli() / 1000.0;
                    } catch (Exception e) {
                        log.error("Failed to parse measuredAt: {}", measuredAt, e);
                        continue;
                    }

                    // 센서 없으니 다음 measurement
                    if (measurement.sensors() == null) {
                        continue;
                    }

                    // 아 참고로 sensorType이 param임...
                    /// key = sensor:EQP-DEPOSITION-001:EQP-DEPOSITION-001-SENSOR-01:Spin Speed
                    /// 이건 진짜 중요할 듯, 각각 PK를 쓰는 게 맞지 않나?
                    // 이러면 일단 PK는 없네?
                    // 첫 시작 이후 3분이 지났어.
                    // 설비 이름 및 센서 이름이 바뀜
                    // 다른 key에 들어가네?
                    // PK를 써야하는 거 아닌가?
                    /// 애초에 device에서 넘어올 때, equipment PK, sensor PK가 같이 와야하는 거 같은데
                    for (SensorReadingDto sensor : measurement.sensors()) {
                        String key = String.format("sensor:%s:%s:%s",
                            batch.equipmentId(),
                            sensor.sensorId(),
                            sensor.sensorType()
                        );

                        Map<String, Object> valueMap = Map.of(
                            "min", sensor.recipeMin(),
                            "max", sensor.recipeMax(),
                            "value", sensor.value(),
                            "ts", measuredAt
                        );

                        String jsonValue;
                        try {
                            jsonValue = objectMapper.writeValueAsString(valueMap);
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize Redis value map for key: {}", key, e);
                            continue;
                        }

                        /// RedisConnection을 사용하기 위한 변환 : API 요구사항임 -> 변경 불가
                        byte[] rawKey = key.getBytes(StandardCharsets.UTF_8);
                        byte[] rawValue = jsonValue.getBytes(StandardCharsets.UTF_8);

                        connection.zSetCommands().zAdd(rawKey, score, rawValue);
                        connection.zSetCommands()
                            .zRemRangeByScore(rawKey, Double.NEGATIVE_INFINITY, cutoff);
                        connection.keyCommands().expire(rawKey, REDIS_WINDOW_SEC + 60);
                    }
                }
                return null;
            });

            log.info("Successfully stored batch measurements to Redis. batchId={}, equipmentId={}",
                batch.batchId(), batch.equipmentId());

            // 2. Trigger anomaly detection for each unique sensor type with the latest timestamp
            // param별 최신 시간을 가진 Map 생성
            Map<String, OffsetDateTime> latestTimestamps = getStringOffsetDateTimeMap(
                batch);

            for (Map.Entry<String, OffsetDateTime> entry : latestTimestamps.entrySet()) {
                String sensorType = entry.getKey();
                /// 꼭 LocalDateTime이 필요한가?
                // 그래도 굴러가 냅둬
                LocalDateTime detectedAtLocal = entry.getValue()
                    .toLocalDateTime(); // standard JVM time is UTC
                log.info(
                    "Triggering real-time anomaly detection. equipmentId={}, sensorType={}, detectedAt={}",
                    batch.equipmentId(), sensorType, detectedAtLocal);
                try {
                    // 이후 AnomalyDetectionService에 위임
                    // 즉, param 별로 latest timestamp 출 거니까, detect 해주세요
                    // 일단 저 arguments 들이 어떻게 쓰이는지는 들어가서 보자
                    anomalyDetectionService.detect(batch.equipmentId(), sensorType,
                        detectedAtLocal);
                } catch (Exception e) {
                    log.error(
                        "Failed to run real-time anomaly detection for equipmentId={}, sensorType={}",
                        batch.equipmentId(), sensorType, e);
                }
            }

        } catch (Exception e) {
            log.error("Error processing Kafka sensor batch message: {}", message, e);
        }
    }

    /// param별 최신 시간을 가진 Map 생성
    private @NonNull Map<String, OffsetDateTime> getStringOffsetDateTimeMap(
        SensorDataBatchDto batch) {
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
        return latestTimestamps;
    }
}
