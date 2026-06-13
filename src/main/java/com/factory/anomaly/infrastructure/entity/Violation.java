package com.factory.anomaly.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "violations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @ManyToOne
    @JoinColumn(name = "anomaly_id", nullable = false)
    private Anomaly anomaly;

    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "sensor_id", nullable = false)
    private String sensorId;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "value", nullable = false)
    private Double value;

    @Column(name = "reference_value")
    private Double referenceValue;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "deviation_rate")
    private Double deviationRate;

    @Column(name = "description", length = 500)
    private String description;
}
