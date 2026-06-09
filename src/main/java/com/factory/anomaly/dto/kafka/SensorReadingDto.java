package com.factory.anomaly.dto.kafka;

public record SensorReadingDto(
    String sensorId,
    String sensorType,
    Double value,
    Double recipeMin,
    Double recipeMax,
    String unit
) {}
