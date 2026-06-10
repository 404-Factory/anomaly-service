package com.factory.anomaly.domain.dto.response;

import com.factory.anomaly.domain.enums.Severity;
import java.time.Instant;
import lombok.Getter;

@Getter
public class AnomalyResponse {

    private Long id;
    private String name;
    private String logType;
    private String severity;
    private String statusLabel;
    private String processName;
    private String equipmentName;
    private String recipeParameter;
    private String ruleName;
    private String anomalyType;
    private Instant occurredTime;
    private String detectionReason;
    private String relatedLogIds;


    public AnomalyResponse(Long id, String name, String logType, Severity severity,
        String processName, String equipmentName, String recipeParameter, String ruleName,
        String anomalyType, Instant occurredTime, String detectionReason, String relatedLogIds) {
        this.id = id;
        this.name = name;
        this.logType = logType;
        this.severity = severity.name();
        this.statusLabel = severity.getLabel();
        this.processName = processName;
        this.equipmentName = equipmentName;
        this.recipeParameter = recipeParameter;
        this.ruleName = ruleName;
        this.anomalyType = anomalyType;
        this.occurredTime = occurredTime;
        this.detectionReason = detectionReason;
        this.relatedLogIds = relatedLogIds;
    }
}
