package com.factory.anomaly_service.domain.entity;

import com.factory.anomaly_service.domain.type.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ALERT")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "log_id", nullable = false)
    private AnomalyLogEntity anomalyLog;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "is_read", nullable = false)
    private Boolean isRead;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 30, nullable = false)
    private Severity severity;

    @Column(name = "occurrence_count", nullable = false)
    private Integer occurrenceCount;
}