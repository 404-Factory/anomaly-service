package com.factory.anomaly.service;

import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import java.time.LocalDateTime;
import java.util.Optional;

public interface AnomalyDetectionService {
    Optional<AnomalyLog> detect(String equipmentCode, String sensorType);
    Optional<AnomalyLog> detect(String equipmentCode, String sensorType, LocalDateTime detectedAt);
}
