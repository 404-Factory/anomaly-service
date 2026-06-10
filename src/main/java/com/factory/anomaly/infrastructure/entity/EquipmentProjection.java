package com.factory.anomaly.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Entity
@Table(name = "equipment_projection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class EquipmentProjection {

    @Id
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "process_id")
    private Long processId;

    @Column(name = "process_name")
    private String processName;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
