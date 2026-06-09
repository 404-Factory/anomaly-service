package com.factory.anomaly.service;

import com.factory.anomaly.dto.response.AnomalyContextResponse;

public interface AnomalyContextService {
    AnomalyContextResponse getAnomalyContext(Long anomalyId);
}
