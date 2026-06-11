package com.factory.anomaly_service.kafka.dto;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnomalyCreatedPayload implements EventPayload {
    private Long anomalyLogId;
    private String equipmentId;
    private Long equipmentRecipeId;
    private String recipeParameter;
    private String severity;
    private Instant occurredTime;
    private String causeRule;
    private String anomalyType;
    private Instant windowStartTime;
    private Integer sampleCount;
    private String detectionReason;
}
