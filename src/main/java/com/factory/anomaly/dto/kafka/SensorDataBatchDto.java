package com.factory.anomaly.dto.kafka;

import java.util.List;

public record SensorDataBatchDto(
    String batchId,
    String deviceId,
    String equipmentId,
    List<MeasurementDto> measurements,
    String createdAt,
    Integer intervalSec
) {}
