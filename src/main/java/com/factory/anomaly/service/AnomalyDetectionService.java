package com.factory.anomaly.service;

import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import java.util.Optional;

public interface AnomalyDetectionService {
    Optional<Anomaly> detect(SensorViolationDto violation);
}
