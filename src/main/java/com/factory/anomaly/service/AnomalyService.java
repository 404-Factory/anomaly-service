package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnomalyService {

    Page<AnomalyResponse> getAnomalies(Long processId, Long equipmentId, String keyword, Pageable pageable);
    AnomalyDetailResponse getAnomaly(Long anomalyId);
    long countAnomalies(String equipmentName, LocalDateTime since);
    void triggerAnalysis(Long anomalyId);
}
