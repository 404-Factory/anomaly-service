package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "PROCESS_INFO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProcessEntity {

    @Id
    @Column(name = "process_id", nullable = false)
    private Long processId;

    @Column(name = "process_name", length = 100)
    private String processName;
}