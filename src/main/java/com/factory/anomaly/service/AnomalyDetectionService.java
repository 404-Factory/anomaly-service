package com.factory.anomaly.service;

import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AnomalyDetectionService {
    Optional<Anomaly> detect(String equipmentCode, String sensorType);
    Optional<Anomaly> detect(String equipmentCode, String sensorType, LocalDateTime detectedAt);
    Optional<Anomaly> detect(SensorViolationDto violation);
}

