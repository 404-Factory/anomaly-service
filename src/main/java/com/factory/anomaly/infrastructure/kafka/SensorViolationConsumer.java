package com.factory.anomaly.infrastructure.kafka;

import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.service.AnomalyService;
import com.fasterxml.jackson.databind.JsonNode;
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
    private final AnomalyService anomalyService;

    @KafkaListener(
        topics = "${app.kafka.consumer.violation-topic:sensor-violations}",
        groupId = "${app.kafka.consumer.violation-group-id:anomaly-violation-group}",
        containerFactory = "flinkKafkaListenerContainerFactory"
    )
    public void consume(String message) {
        log.info("Received sensor violation message from Kafka: {}", message);
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            SensorViolationDto violation;
            if (rootNode != null && rootNode.has("payload")) {
                violation = objectMapper.treeToValue(rootNode.get("payload"), SensorViolationDto.class);
            } else {
                violation = objectMapper.readValue(message, SensorViolationDto.class);
            }
            anomalyService.detect(violation);
        } catch (Exception e) {
            log.error("Error processing Kafka sensor violation message: {}", message, e);
        }
    }
}
