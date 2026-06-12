package com.factory.anomaly_service.kafka.dto;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;

public record SensorViolationPayload(
    String equipmentId,
    String sensorType,
    String ruleName,
    String anomalyType,
    String severity,
    Double measuredValue,
    Double referenceValue,
    Double deviation,
    Double deviationRate,
    Double min,
    Double max,
    Instant detectedAt,
    Integer sampleCount,
    String reason
) implements EventPayload {}
