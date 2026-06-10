package com.factory.anomaly.event.payload.producer;

import com.factory.common.event.domain.EventPayload;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class AnalysisRequestedPayload implements EventPayload {
    private Long anomalyId;
    private Long equipmentId;
    private String recipeParameter;
    private String summaryText;
    private String recommendedAnalysisType;
    private List<String> analysisFocus;
    private String llmPromptHint;
}
