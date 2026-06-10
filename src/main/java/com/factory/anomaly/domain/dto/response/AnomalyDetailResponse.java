package com.factory.anomaly.domain.dto.response;

import com.factory.anomaly.domain.enums.Severity;
import com.factory.common.inbox.jpa.autoconfigure.InboxJpaAutoConfiguration;
import com.factory.common.kafka.consumer.CommonKafkaConsumer;
import com.factory.common.kafka.support.EventDispatcher;
import java.time.Instant;
import lombok.Getter;

@Getter
public class AnomalyDetailResponse {

    private Long id;
    private String name;
    private String severity;
    private String statusLabel;
    private Long processId;
    private String processName;
    private Long equipmentId;
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


    public AnomalyDetailResponse(Long id, String name, Severity severity,
        Long processId, String processName, Long equipmentId, String equipmentName,
        String recipeParameter, String ruleName, String anomalyType, Integer sampleCount,
        String relatedLogIds, Double minValue, Double maxValue, Double measuredValue,
        Double referenceValue, Double deviation, Double deviationRate, String detectionReason,
        Instant firstDetectedAt, Instant lastDetectedAt, String analysisStatus, String summary) {
        this.id = id;
        this.name = name;
        this.severity = severity.name();
        this.statusLabel = severity.getLabel();
        this.processId = processId;
        this.processName = processName;
        this.equipmentId = equipmentId;
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
}

//public record AnomalyLogDetailResponse(
//    Instant occurredTime,
//    Instant windowStartTime,
//    Integer sampleCount,
//    String detectionReason,
//    // viewModel 관리 후보 : 설비 id, 설비 이름, process id, process name
//    // 설비랑 프로세스는 묶인다?

