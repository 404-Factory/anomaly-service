package com.factory.anomaly.event.publisher;

import com.factory.common.event.domain.Event;
import com.factory.common.kafka.publisher.EventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher implements EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${sigma.event.producer.topic:management-event}")
    private String topic;

    @Override
    public void publish(Event<?> event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = event.getIdempotencyKey();
            log.info("[Kafka] Publishing event to topic {}. key={}, eventType={}", topic, key, event.getEventType());
            kafkaTemplate.send(topic, key, message);
        } catch (Exception e) {
            log.error("[Kafka] Failed to publish event. eventType={}", event.getEventType(), e);
        }
    }
}
