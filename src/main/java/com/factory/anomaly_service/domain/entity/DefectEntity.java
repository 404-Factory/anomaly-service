package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "DEFECT_INFO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class DefectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defect_id", nullable = false)
    private Long defectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private LotEntity lot;

    @Column(name = "defect_type", length = 50)
    private String defectType;

    @Column(name = "defect_code", length = 50)
    private String defectCode;

    @Column(name = "detected_time")
    private LocalDateTime detectedTime;

    @Column(name = "occurred_time")
    private LocalDateTime occurredTime;

    @Column(name = "cause_process_id")
    private Long causeProcessId;

    @Column(name = "cause_process_name", length = 100)
    private String causeProcessName;

    @Column(name = "cause_equipment_id")
    private Long causeEquipmentId;

    @Column(name = "cause_equipment_name", length = 100)
    private String causeEquipmentName;
}