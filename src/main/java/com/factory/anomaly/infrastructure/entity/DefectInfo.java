package com.factory.anomaly.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(schema = "management_db", name = "defects")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DefectInfo {

    @Id
    private Long id;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "cause_process_id")
    private Long causeProcessId;

    @Column(name = "cause_equipment_id")
    private Long causeEquipmentId;

    @Column(name = "cause_process_name", length = 100)
    private String causeProcessName;

    @Column(name = "cause_equipment_name", length = 100)
    private String causeEquipmentName;

    @Column(name = "defect_type", length = 50)
    private String defectType;

    @Column(name = "defect_code", length = 50)
    private String defectCode;

    @Column(name = "occurred_time")
    private Instant occurredTime;

    @Column(name = "detected_time")
    private Instant detectedTime;
}
