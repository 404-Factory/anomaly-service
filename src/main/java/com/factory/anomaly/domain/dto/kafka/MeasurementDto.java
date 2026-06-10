package com.factory.anomaly.domain.dto.kafka;

import java.util.List;

public record MeasurementDto(
    Integer sequence,
    String measuredAt,
    List<SensorReadingDto> sensors,
    String status
) {}
