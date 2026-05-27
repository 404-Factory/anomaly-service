package com.factory.anomaly_service.domain.dto.response;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.entity.EquipmentRecipeDetailEntity;
import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;

import java.time.LocalDateTime;

public record AnomalyLogDetailResponse(
        Long logId,

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

        Double minValue,
        Double maxValue
) {

    public static AnomalyLogDetailResponse of(
            AnomalyLogEntity anomalyLog,
            EquipmentRecipeDetailEntity recipeDetail
    ) {
        var equipment = anomalyLog.getEquipment();
        var process = equipment != null ? equipment.getProcess() : null;
        var equipmentRecipe = anomalyLog.getEquipmentRecipe();
        var masterRecipe = equipmentRecipe != null ? equipmentRecipe.getMasterRecipe() : null;

        return new AnomalyLogDetailResponse(
                anomalyLog.getLogId(),

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

                recipeDetail != null ? recipeDetail.getMinValue() : null,
                recipeDetail != null ? recipeDetail.getMaxValue() : null
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