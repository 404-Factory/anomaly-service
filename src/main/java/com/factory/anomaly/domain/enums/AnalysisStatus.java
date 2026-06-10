package com.factory.anomaly.domain.enums;

import lombok.Getter;

@Getter
public enum AnalysisStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED;
}
