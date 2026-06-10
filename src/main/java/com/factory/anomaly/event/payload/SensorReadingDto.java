package com.factory.anomaly.event.payload;

public record SensorReadingDto(
    String sensorId,
    String sensorType,
    Double value,
    Double recipeMin,
    Double recipeMax,
    String unit
) {}
