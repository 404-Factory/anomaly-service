package com.factory.anomaly.dto.response;

import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;

import java.time.LocalDateTime;

public record AnomalyLogDetailResponse(
        Long logId,
        LogType logType,
        Severity severity,
        String statusLabel,
        Long processId,
        String processName,
        Long equipmentId,
        String equipmentName,
        Long equipmentRecipeId,
        Long masterRecipeId,
        String recipeParameter,
        RuleName ruleName,
        AnomalyType anomalyType,
        LocalDateTime occurredTime,
        LocalDateTime windowStartTime,
        Integer sampleCount,
        String detectionReason,

        String relatedLogIds,

        Double minValue,
        Double maxValue,

        Double measuredValue,
        Double referenceValue,
        Double deviation,
        Double deviationRate
) {

    public static AnomalyLogDetailResponse of(
            AnomalyLog anomalyLog,
            EquipmentRecipeDetail recipeDetail
    ) {
        var equipment = anomalyLog.getEquipment();
        var process = equipment != null ? equipment.getProcess() : null;
        var equipmentRecipe = anomalyLog.getEquipmentRecipe();
        var masterRecipe = equipmentRecipe != null ? equipmentRecipe.getMasterRecipe() : null;

        return new AnomalyLogDetailResponse(
                anomalyLog.getLogId(),

                anomalyLog.getLogType(),

                anomalyLog.getSeverity(),
                toStatusLabel(anomalyLog.getSeverity()),

                process != null ? process.getProcessId() : null,
                process != null ? process.getProcessName() : null,

                equipment != null ? equipment.getEquipmentId() : null,
                equipment != null ? equipment.getEquipmentName() : null,

                equipmentRecipe != null ? equipmentRecipe.getEquipmentRecipeId() : null,
                masterRecipe != null ? masterRecipe.getMasterRecipeId() : null,

                anomalyLog.getRecipeParameter(),
                anomalyLog.getRuleName(),
                anomalyLog.getAnomalyType(),

                anomalyLog.getOccurredTime(),
                anomalyLog.getWindowStartTime(),
                anomalyLog.getSampleCount(),
                anomalyLog.getDetectionReason(),

                anomalyLog.getRelatedLogIds(),

                recipeDetail != null ? recipeDetail.getMinValue() : null,
                recipeDetail != null ? recipeDetail.getMaxValue() : null,

                anomalyLog.getMeasuredValue(),
                anomalyLog.getReferenceValue(),
                anomalyLog.getDeviation(),
                anomalyLog.getDeviationRate()
        );
    }

    private static String toStatusLabel(Severity severity) {
        if (severity == null) {
            return null;
        }

        return switch (severity) {
            case CRITICAL -> "긴급";
            case WARNING -> "경고";
            case CAUTION -> "주의";
        };
    }
}
