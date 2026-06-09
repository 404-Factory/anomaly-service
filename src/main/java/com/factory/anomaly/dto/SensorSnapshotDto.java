package com.factory.anomaly.dto;

import java.time.Instant;
import java.util.List;

public record SensorSnapshotDto(
        String sensorId,
        String sensorType,
        String unit,
        Double recipeMin,
        Double recipeMax,
        List<Point> values
) {
    public record Point(
            Instant ts,
            Double val
    ) {
    }
}
