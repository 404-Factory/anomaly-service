package com.factory.anomaly.infrastructure.kafka;

import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.service.AnomalyDetectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorViolationConsumer {

    private final ObjectMapper objectMapper;
    private final AnomalyDetectionService anomalyDetectionService;

    @KafkaListener(
        topics = "${app.kafka.consumer.violation-topic:sensor-violations}",
        groupId = "${spring.kafka.consumer.violation-group-id:anomaly-violation-group}"
    )
    public void consume(String message) {
        log.info("Received sensor violation message from Kafka: {}", message);
        try {
            SensorViolationDto violation = objectMapper.readValue(message, SensorViolationDto.class);
            anomalyDetectionService.detect(violation);
        } catch (Exception e) {
            log.error("Error processing Kafka sensor violation message: {}", message, e);
        }
    }
}
