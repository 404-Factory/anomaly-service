package com.factory.anomaly_service.kafka.dto;

import com.factory.common.event.domain.Event;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensorViolationPayloadDeserializationTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    @Test
    void payload_필드가_정상적으로_역직렬화된다() throws Exception {
        String json = """
            {
              "idempotencyKey": "e8dda1a4-67cb-4600-937e-91c013d64085",
              "eventType": "SensorViolation",
              "envelopeType": "domain",
              "aggregateType": "Equipment",
              "aggregateId": "EQP-CLEANING-001",
              "traceId": null,
              "timestamp": "2026-06-12T00:15:27.068883762Z",
              "payload": {
                "equipmentId": "EQP-CLEANING-001",
                "sensorType": "Chemical Temperature",
                "ruleName": "NELSON_RULE_1",
                "anomalyType": "HIGH",
                "severity": "CAUTION",
                "measuredValue": 31.118,
                "referenceValue": 31.0,
                "deviation": 0.118,
                "deviationRate": 0.38,
                "min": 29.0,
                "max": 31.0,
                "detectedAt": "2026-06-12T00:15:26.591Z",
                "sampleCount": 240,
                "reason": "최근 5분 내 Recipe 기준 이탈 2회 발생"
              }
            }
            """;

        JavaType type = mapper.getTypeFactory()
            .constructParametricType(Event.class, SensorViolationPayload.class);

        Event<SensorViolationPayload> event = mapper.readValue(json, type);
        SensorViolationPayload payload = event.getPayload();

        assertThat(payload.ruleName()).isEqualTo("NELSON_RULE_1");
        assertThat(payload.anomalyType()).isEqualTo("HIGH");
        assertThat(payload.severity()).isEqualTo("CAUTION");
        assertThat(payload.sensorType()).isEqualTo("Chemical Temperature");
        assertThat(payload.equipmentId()).isEqualTo("EQP-CLEANING-001");
        assertThat(payload.sampleCount()).isEqualTo(240);
    }
}
