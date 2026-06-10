package com.factory.anomaly.event.payload.producer;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyCreatedPayload implements EventPayload {
    private Long equipmentId;
    private String equipmentName;
    private String recipeParameter;
    private String severity;
    private Instant occurredTime;
    private String causeRule;
}
