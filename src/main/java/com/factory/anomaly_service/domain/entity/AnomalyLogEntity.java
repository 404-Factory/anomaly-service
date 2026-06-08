package com.factory.anomaly_service.domain.entity;

import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import com.factory.anomaly_service.domain.type.LogType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnomalyLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "equipment_id")
    private String equipmentId;

    @Column(name = "equipment_recipe_id")
    private String equipmentRecipeId;

    @Column(name = "recipe_parameter", length = 50)
    private String recipeParameter;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_name", nullable = false)
    private RuleName ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false)
    private AnomalyType anomalyType;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "detection_reason", columnDefinition = "TEXT")
    private String detectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", length = 30)
    private LogType logType;

    @Column(name = "related_log_ids", length = 500)
    private String relatedLogIds;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "reference_value")
    private Double referenceValue;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "deviation_rate")
    private Double deviationRate;

    @Column(name = "first_detected_at")
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at")
    private Instant lastDetectedAt;
}