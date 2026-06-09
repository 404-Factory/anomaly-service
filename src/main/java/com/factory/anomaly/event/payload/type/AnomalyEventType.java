package com.factory.anomaly.event.payload.type;

import com.factory.common.event.domain.EventType;

public enum AnomalyEventType implements EventType {
    ANOMALY_CREATED("AnomalyCreated");

    private final String name;

    AnomalyEventType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
