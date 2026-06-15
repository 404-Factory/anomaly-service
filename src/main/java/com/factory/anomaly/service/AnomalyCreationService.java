package com.factory.anomaly.service;

import java.util.Optional;

import com.factory.anomaly.event.payload.consumer.SensorViolationPayload;
import com.factory.anomaly.infrastructure.entity.Anomaly;

public interface AnomalyCreationService {
    Optional<Anomaly> create(SensorViolationPayload violation);
    
}
