package com.factory.anomaly.domain.enums;

import lombok.Getter;

@Getter
public enum Severity {
    NORMAL("정상"),
    CAUTION("주의"),
    WARNING("경고"),
    CRITICAL("긴급");

    private final String label;

    Severity(String label) {
        this.label = label;
    }
}
