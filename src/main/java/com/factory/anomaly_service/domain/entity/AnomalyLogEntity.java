package com.factory.anomaly_service.domain.entity;

import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ANOMALY_LOG")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AnomalyLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id", nullable = false)
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private EquipmentEntity equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_rec_id")
    private EquipmentRecipeEntity equipmentRecipe;

    @Column(name = "recipe_parameter", length = 50)
    private String recipeParameter;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30)
    private Severity severity;

    @Column(name = "occurred_time")
    private LocalDateTime occurredTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_name", length = 50, nullable = false)
    private RuleName ruleName;

    @Enumerated(EnumType.STRING)
    @Column(name = "anomaly_type", length = 50, nullable = false)
    private AnomalyType anomalyType;

    @Column(name = "window_start_time")
    private LocalDateTime windowStartTime;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "detection_reason", length = 500)
    private String detectionReason;
}