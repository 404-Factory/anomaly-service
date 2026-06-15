package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnalysisResponseDto;

public interface AnalysisService {

    void updateAnalysis(Long anomalyId, String status, String summary);
    void triggerAnalysis(Long anomalyId);
    AnalysisResponseDto getAnalysis(Long anomalyId);
}
