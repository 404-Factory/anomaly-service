package com.factory.anomaly.event.payload.consumer;

import java.time.Instant;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;

import com.factory.common.event.domain.EventEnvelope;
import com.factory.common.event.domain.EventPayload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The violation domain payload carried inside an {@link EventEnvelope}.
 *
 * <p>Mirrors {@link SensorViolationEvent} but flattens the enums to strings for a stable,
 * language-neutral JSON contract with the consumer (SensorViolationConsumer).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorViolationPayload implements EventPayload {
    private Long equipmentId;
    private String sensorType;
    private RuleName ruleName;
    private AnomalyType anomalyType;
    private Severity severity;
    private Double measuredValue;
    private Double referenceValue;
    private Double deviation;
    private Double deviationRate;
    private Double min;
    private Double max;
    private Instant detectedAt;
    private Instant windowStart;
    private Instant windowEnd;
    private Integer sampleCount;
    private String reason;
}

