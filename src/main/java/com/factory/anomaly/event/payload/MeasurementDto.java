package com.factory.anomaly.event.payload;

import java.util.List;

public record MeasurementDto(
    Integer sequence,
    String measuredAt,
    List<SensorReadingDto> sensors,
    String status
) {}
