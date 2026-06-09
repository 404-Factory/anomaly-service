package com.factory.anomaly.infrastructure.entity;

import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "anomalies")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnomalyLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private Equipment equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_recipe_id")
    private EquipmentRecipe equipmentRecipe;

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

    @Column(name = "ai_analysis", columnDefinition = "TEXT")
    private String aiAnalysis;
}
