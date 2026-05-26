package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "EQUIPMENT_INFO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EquipmentEntity {

    @Id
    @Column(name = "equipment_id", nullable = false)
    private Long equipmentId;

    @Column(name = "equipment_name", length = 100)
    private String equipmentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id")
    private ProcessEntity process;
}