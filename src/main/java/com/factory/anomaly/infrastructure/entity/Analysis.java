package com.factory.anomaly.infrastructure.entity;

import com.factory.anomaly.domain.enums.AnalysisStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anomaly_id")
    private Long anomalyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnalysisStatus status;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Builder
    public Analysis(Long id, Long anomalyId, AnalysisStatus status, String summary) {
        this.id = id;
        this.anomalyId = anomalyId;
        this.status = status;
        this.summary = summary;
    }

    public void update(AnalysisStatus status, String summary) {
        this.status = status;
        this.summary = summary;
    }
}
