package com.factory.anomaly.event.type;
import com.factory.common.event.domain.EventType;

public enum FlinkEventType implements EventType {
    SENSOR_VIOLATION("SensorViolation");

    private final String name;

    FlinkEventType(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
