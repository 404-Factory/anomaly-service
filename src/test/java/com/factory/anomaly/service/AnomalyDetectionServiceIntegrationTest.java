package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.common.outbox.jpa.entity.OutboxEntity;
import com.factory.common.outbox.jpa.repository.JpaOutboxRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.event.publish-enabled=true")
class AnomalyDetectionServiceIntegrationTest {

    @MockBean
    private SensorRedisRepository sensorRedisRepository;

    @Autowired
    private AnomalyDetectionService anomalyDetectionService;

    @Autowired
    private JpaOutboxRepository jpaOutboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jpaOutboxRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM anomalies");
        jdbcTemplate.execute("DELETE FROM equipment_projection");
        jdbcTemplate.execute(
            "INSERT INTO equipment_projection (id, name, process_id, process_name) VALUES (1, 'EQP-01', 100, 'PHOTO')"
        );
    }

    @AfterEach
    void tearDown() {
        jpaOutboxRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM anomalies");
        jdbcTemplate.execute("DELETE FROM equipment_projection");
    }

    @Test
    void detect_shouldPersistOutboxEventInSameTransaction() {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
            equipmentCode, sensorType, RuleName.NELSON_RULE_3, AnomalyType.HIGH, Severity.WARNING,
            15.0, 12.0, 3.0, 25.0, 10.0, 50.0, detectedAt, 10, "Nelson Rule 3 Violation"
        );

        when(sensorRedisRepository.acquireLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong()))
            .thenReturn(true);
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH"))
            .thenReturn(null);

        // When
        anomalyDetectionService.detect(violation);

        // Then — outbox record must exist after detect() commits
        List<OutboxEntity> outboxEvents = jpaOutboxRepository.findAll();
        assertThat(outboxEvents).hasSize(1);

        OutboxEntity saved = outboxEvents.get(0);
        assertThat(saved.getEventType()).isEqualTo("AnomalyCreated");
        assertThat(saved.getAggregateType()).isEqualTo("Anomaly");
        assertThat(saved.getPayload()).contains("EQP-01");
        assertThat(saved.getPayload()).contains("TEMP");
        assertThat(saved.getPayload()).contains("WARNING");
        assertThat(saved.getPayload()).contains("NELSON_RULE_3");
    }
}
