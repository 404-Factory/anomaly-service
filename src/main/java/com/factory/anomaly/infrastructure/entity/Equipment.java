package com.factory.anomaly.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "equipments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Equipment {

    @Id
    @Column(name = "equipment_id")
    private Long equipmentId;

    @Column(name = "name", nullable = false, length = 100)
    private String equipmentName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id")
    private Process process;
}
