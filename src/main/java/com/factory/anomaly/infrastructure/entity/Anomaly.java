package com.factory.anomaly.infrastructure.entity;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "anomalies")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "recipe_parameter", nullable = false)
    private String recipeParameter;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", nullable = false)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false)
    private LogType logType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_name", nullable = false)
    private RuleName ruleName;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "reference_value")
    private Double referenceValue;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "deviation_rate")
    private Double deviationRate;

    @Column(name = "min")
    private Double min;

    @Column(name = "max")
    private Double max;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "first_detected_at")
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at")
    private Instant lastDetectedAt;

    @Column(name = "detection_reason", columnDefinition = "TEXT")
    private String detectionReason;

    @OneToMany(mappedBy = "anomaly", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    public void update(Instant lastDetectedAt, Integer sampleCount, Severity severity) {
        this.lastDetectedAt = lastDetectedAt;
        this.sampleCount = sampleCount;
        if (severity != null && (this.severity == null
            || severity.ordinal() > this.severity.ordinal())) {
            this.severity = severity;
        }
    }

    public void resolve() {
        this.status = "RESOLVED";
    }

    public void addViolation(Violation violation) {
        this.violations.add(violation);
        violation.setAnomaly(this);
    }
}

