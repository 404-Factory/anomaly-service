package com.factory.anomaly.dto.response;

import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;

import java.time.LocalDateTime;

public record AnomalyLogResponse(
        Long logId,

        LogType logType,

        Severity severity,
        String statusLabel,

        Long processId,
        String processName,

        Long equipmentId,
        String equipmentName,

        Long equipmentRecipeId,

        String recipeParameter,
        RuleName ruleName,
        AnomalyType anomalyType,

        LocalDateTime occurredTime,
        String detectionReason,

        String relatedLogIds
) {

    public static AnomalyLogResponse from(AnomalyLog entity) {
        var equipment = entity.getEquipment();
        var process = equipment != null ? equipment.getProcess() : null;
        var equipmentRecipe = entity.getEquipmentRecipe();

        return new AnomalyLogResponse(
                entity.getLogId(),

                entity.getLogType(),

                entity.getSeverity(),
                toStatusLabel(entity.getSeverity()),

                process != null ? process.getProcessId() : null,
                process != null ? process.getProcessName() : null,

                equipment != null ? equipment.getEquipmentId() : null,
                equipment != null ? equipment.getEquipmentName() : null,

                equipmentRecipe != null ? equipmentRecipe.getEquipmentRecipeId() : null,

                entity.getRecipeParameter(),
                entity.getRuleName(),
                entity.getAnomalyType(),

                entity.getOccurredTime(),
                entity.getDetectionReason(),

                entity.getRelatedLogIds()
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
