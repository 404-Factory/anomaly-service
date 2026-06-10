package com.factory.anomaly.event.type;

import com.factory.common.event.domain.EventType;

public enum AnalysisEventType implements EventType {
    ANALYSIS_REQUESTED("AnalysisRequested"),
    ANALYSIS_COMPLETED("AnalysisCompleted");

    private final String name;

    AnalysisEventType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
