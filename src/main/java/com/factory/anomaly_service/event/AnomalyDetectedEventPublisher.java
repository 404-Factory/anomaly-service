package com.factory.anomaly_service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnomalyDetectedEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic.anomaly-detected}")
    private String anomalyDetectedTopic;

    public void publish(AnomalyDetectedEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = String.valueOf(event.logId());

            kafkaTemplate.send(anomalyDetectedTopic, key, message)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            log.error(
                                    "[Kafka] anomaly.detected 이벤트 발행 실패. topic={}, logId={}",
                                    anomalyDetectedTopic,
                                    event.logId(),
                                    exception
                            );
                            return;
                        }

                        log.info(
                                "[Kafka] anomaly.detected 이벤트 발행 완료. topic={}, logId={}, equipmentId={}, severity={}",
                                anomalyDetectedTopic,
                                event.logId(),
                                event.equipmentId(),
                                event.severity()
                        );
                    });

        } catch (JsonProcessingException e) {
            log.error(
                    "[Kafka] anomaly.detected 이벤트 직렬화 실패. logId={}",
                    event.logId(),
                    e
            );
        }
    }
}