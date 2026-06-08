package com.factory.anomaly.service;

import com.factory.anomaly.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly.dto.response.AnomalyLogResponse;
import java.util.List;

public interface AnomalyService {
    List<AnomalyLogResponse> getAnomalyLogs(Long processId, Long equipmentId, String keyword);
    AnomalyLogDetailResponse getAnomalyLogDetail(Long anomalyId);
}
