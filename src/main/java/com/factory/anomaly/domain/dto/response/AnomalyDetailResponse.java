package com.factory.anomaly.domain.dto.response;

import com.factory.anomaly.domain.enums.Severity;
import java.time.Instant;
import lombok.Getter;

@Getter
public class AnomalyDetailResponse {

    private Long id;
    private String name;
    private String severity;
    private String statusLabel;
    private String processName;
    private String equipmentName;
    private String recipeParameter;
    private String ruleName;
    private String anomalyType;
    private Integer sampleCount;
    private String relatedLogIds;
    private Double minValue;
    private Double maxValue;
    private Double measuredValue;
    private Double referenceValue;
    private Double deviation;
    private Double deviationRate;
    private String detectionReason;
    private Instant firstDetectedAt;
    private Instant lastDetectedAt;
    private String analysisStatus;
    private String summary;


    private java.util.List<ViolationResponse> violations;

    public void setViolations(java.util.List<ViolationResponse> violations) {
        this.violations = violations;
    }

    public AnomalyDetailResponse(Long id, String name, Severity severity, String processName,
        String equipmentName, String recipeParameter, String ruleName, String anomalyType,
        Integer sampleCount, String relatedLogIds, Double minValue, Double maxValue,
        Double measuredValue, Double referenceValue, Double deviation, Double deviationRate,
        String detectionReason, Instant firstDetectedAt, Instant lastDetectedAt,
        String analysisStatus, String summary) {
        this.id = id;
        this.name = name;
        this.severity = severity.name();
        this.statusLabel = severity.getLabel();
        this.processName = processName;
        this.equipmentName = equipmentName;
        this.recipeParameter = recipeParameter;
        this.ruleName = ruleName;
        this.anomalyType = anomalyType;
        this.sampleCount = sampleCount;
        this.relatedLogIds = relatedLogIds;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.measuredValue = measuredValue;
        this.referenceValue = referenceValue;
        this.deviation = deviation;
        this.deviationRate = deviationRate;
        this.detectionReason = detectionReason;
        this.firstDetectedAt = firstDetectedAt;
        this.lastDetectedAt = lastDetectedAt;
        this.analysisStatus = analysisStatus;
        this.summary = summary;
    }

    @Getter
    public static class ViolationResponse {
        private Long id;
        private Long equipmentId;
        private String sensorId;
        private String severity;
        private Instant detectedAt;
        private Double value;
        private Double referenceValue;
        private Double deviation;
        private Double deviationRate;
        private String description;

        public ViolationResponse(com.factory.anomaly.infrastructure.entity.Violation violation) {
            this.id = violation.getId();
            this.equipmentId = violation.getEquipmentId();
            this.sensorId = violation.getSensorId();
            this.severity = violation.getSeverity();
            this.detectedAt = violation.getDetectedAt();
            this.value = violation.getValue();
            this.referenceValue = violation.getReferenceValue();
            this.deviation = violation.getDeviation();
            this.deviationRate = violation.getDeviationRate();
            this.description = violation.getDescription();
        }
    }
}

//public record AnomalyLogDetailResponse(
//    Instant occurredTime,
//    Instant windowStartTime,
//    Integer sampleCount,
//    String detectionReason,
//    // viewModel 관리 후보 : 설비 id, 설비 이름, process id, process name
//    // 설비랑 프로세스는 묶인다?

