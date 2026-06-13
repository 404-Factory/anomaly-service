package com.factory.anomaly.event.payload;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import java.time.Instant;

public record SensorViolationDto(
    Long equipmentId,
    String sensorId,
    String sensorType,
    RuleName ruleName,
    AnomalyType anomalyType,
    Severity severity,
    Double measuredValue,
    Double referenceValue,
    Double deviation,
    Double deviationRate,
    Double min,
    Double max,
    Instant detectedAt,
    Integer sampleCount,
    String reason
) {}
