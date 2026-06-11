package com.factory.anomaly_service.kafka.dto;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SensorViolationPayload implements EventPayload {
    private String equipmentId;
    private String sensorType;
    private String ruleName;
    private String anomalyType;
    private String severity;
    private Double measuredValue;
    private Double referenceValue;
    private Double deviation;
    private Double deviationRate;
    private Double min;
    private Double max;
    private Instant detectedAt;
    private Integer sampleCount;
    private String reason;
}
