package com.factory.anomaly.event.payload.consumer;

import com.factory.common.event.domain.EventPayload;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
public class AnalysisCompletedPayload implements EventPayload {
    Long anomalyId;
    String status;
    String summary;
    Instant completedAt;
}
