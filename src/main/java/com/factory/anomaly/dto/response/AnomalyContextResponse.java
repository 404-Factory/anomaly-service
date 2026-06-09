package com.factory.anomaly.dto.response;

import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;

import java.time.Instant;
import java.util.List;

public record AnomalyContextResponse(
        AnomalyInfo anomaly,
        EquipmentInfo equipment,
        RecipeInfo recipe,
        RuleContext ruleContext,
        List<RelatedLogInfo> relatedLogs,
        List<SensorSeries> sensorSeries,
        AiInputSummary aiInputSummary
) {

    public record AnomalyInfo(
            Long anomalyId,
            LogType logType,
            Severity severity,
            String displaySeverity,
            Instant occurredTime,
            RuleName ruleName,
            AnomalyType anomalyType,
            String relatedLogIds
    ) {
    }

    public record EquipmentInfo(
            Long equipmentId,
            String equipmentName,
            Long processId,
            String processName
    ) {
    }

    public record RecipeInfo(
            Long equipmentRecipeId,
            Long masterRecipeId,
            String recipeVersion,
            String recipeParameter,
            String recipeParameterName,
            Double recipeMin,
            Double recipeMax,
            Double centerValue
    ) {
    }

    public record RuleContext(
            RuleName ruleName,
            String description,
            Double measuredValue,
            Double referenceValue,
            Double deviation,
            Double deviationRate,
            String ruleReason,
            Instant windowStartTime,
            Instant windowEndTime,
            Integer expectedSampleCount,
            Integer actualSampleCount
    ) {
    }

    public record RelatedLogInfo(
            Long anomalyId,
            LogType logType,
            String recipeParameter,
            Severity severity,
            String displaySeverity,
            RuleName ruleName,
            AnomalyType anomalyType,
            Instant occurredTime,
            String detectionReason
    ) {
    }

    public record SensorSeries(
            String sensorName,
            String sensorType,
            String unit,
            Double recipeMin,
            Double recipeMax,
            List<SensorPoint> points
    ) {
    }

    public record SensorPoint(
            Instant timestamp,
            Double value
    ) {
    }

    public record AiInputSummary(
            String summaryText,
            String recommendedAnalysisType,
            List<String> analysisFocus,
            String llmPromptHint
    ) {
    }
}
