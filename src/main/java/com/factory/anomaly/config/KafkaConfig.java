package com.factory.anomaly.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    // 1. 타 서비스용 기본 Listener Container Factory (read_uncommitted, at-least-once)
    @Bean("kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(commonConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }

    private ConsumerFactory<String, String> commonConsumerFactory() {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        // 타 서비스와의 통신은 read_uncommitted
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_uncommitted");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    // 2. Flink 전용 Listener Container Factory (read_committed, exactly-once)
    @Bean("flinkKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> flinkKafkaListenerContainerFactory(
            @Value("${app.kafka.consumer.violation-group-id:anomaly-violation-group}") String groupId
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(flinkConsumerFactory(groupId));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
        return factory;
    }

    private ConsumerFactory<String, String> flinkConsumerFactory(String groupId) {
        Map<String, Object> props = kafkaProperties.buildConsumerProperties();
        // Flink 전용 격리수준 및 그룹 ID 강제 오버라이드
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        // exactly-once 보장을 위해 auto commit은 수동 제어로 비활성화 권장
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }
}
