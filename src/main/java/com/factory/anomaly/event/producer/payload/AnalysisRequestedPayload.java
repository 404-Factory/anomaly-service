package com.factory.anomaly.event.producer.payload;

import com.factory.common.event.domain.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AnalysisRequestedPayload implements EventPayload {
    // 필요한 필드 말하기
    private Long anomalyId;
    private Long equipmentId;
    private String recipeParameter;
}
