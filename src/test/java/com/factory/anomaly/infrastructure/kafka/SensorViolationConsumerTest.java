package com.factory.anomaly.infrastructure.kafka;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.service.AnomalyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SensorViolationConsumerTest {

    private SensorViolationConsumer consumer;

    @Mock
    private AnomalyService anomalyService;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        consumer = new SensorViolationConsumer(objectMapper, anomalyService);
    }

    @Test
    void consume_DirectDtoFormat() throws Exception {
        String json = "{\n" +
                "  \"equipmentId\": 1,\n" +
                "  \"sensorId\": \"1-TEMP\",\n" +
                "  \"sensorType\": \"TEMP\",\n" +
                "  \"ruleName\": \"NELSON_RULE_3\",\n" +
                "  \"anomalyType\": \"HIGH\",\n" +
                "  \"severity\": \"WARNING\",\n" +
                "  \"measuredValue\": 15.0,\n" +
                "  \"referenceValue\": 12.0,\n" +
                "  \"deviation\": 3.0,\n" +
                "  \"deviationRate\": 25.0,\n" +
                "  \"min\": 10.0,\n" +
                "  \"max\": 50.0,\n" +
                "  \"detectedAt\": \"2026-06-10T12:00:59Z\",\n" +
                "  \"sampleCount\": 10,\n" +
                "  \"reason\": \"Nelson Rule 3 Violation\"\n" +
                "}";

        consumer.consume(json);

        ArgumentCaptor<SensorViolationDto> captor = ArgumentCaptor.forClass(SensorViolationDto.class);
        verify(anomalyService).detect(captor.capture());

        SensorViolationDto dto = captor.getValue();
        assertThat(dto.equipmentId()).isEqualTo(1L);
        assertThat(dto.sensorId()).isEqualTo("1-TEMP");
        assertThat(dto.ruleName()).isEqualTo(RuleName.NELSON_RULE_3);
        assertThat(dto.detectedAt()).isEqualTo(Instant.parse("2026-06-10T12:00:59Z"));
    }

    @Test
    void consume_EnvelopeFormat() throws Exception {
        String json = "{\n" +
                "  \"idempotencyKey\": \"test-id-123\",\n" +
                "  \"eventType\": \"SensorViolation\",\n" +
                "  \"envelopeType\": \"domain\",\n" +
                "  \"aggregateType\": \"Equipment\",\n" +
                "  \"aggregateId\": \"1\",\n" +
                "  \"timestamp\": \"2026-06-14T07:42:07Z\",\n" +
                "  \"payload\": {\n" +
                "    \"equipmentId\": 1,\n" +
                "    \"sensorId\": \"1-TEMP\",\n" +
                "    \"sensorType\": \"TEMP\",\n" +
                "    \"ruleName\": \"NELSON_RULE_3\",\n" +
                "    \"anomalyType\": \"HIGH\",\n" +
                "    \"severity\": \"WARNING\",\n" +
                "    \"measuredValue\": 15.0,\n" +
                "    \"referenceValue\": 12.0,\n" +
                "    \"deviation\": 3.0,\n" +
                "    \"deviationRate\": 25.0,\n" +
                "    \"min\": 10.0,\n" +
                "    \"max\": 50.0,\n" +
                "    \"detectedAt\": \"2026-06-10T12:00:59Z\",\n" +
                "    \"sampleCount\": 10,\n" +
                "    \"reason\": \"Nelson Rule 3 Violation\"\n" +
                "  }\n" +
                "}";

        consumer.consume(json);

        ArgumentCaptor<SensorViolationDto> captor = ArgumentCaptor.forClass(SensorViolationDto.class);
        verify(anomalyService).detect(captor.capture());

        SensorViolationDto dto = captor.getValue();
        assertThat(dto.equipmentId()).isEqualTo(1L);
        assertThat(dto.sensorId()).isEqualTo("1-TEMP");
        assertThat(dto.ruleName()).isEqualTo(RuleName.NELSON_RULE_3);
        assertThat(dto.detectedAt()).isEqualTo(Instant.parse("2026-06-10T12:00:59Z"));
    }
}
