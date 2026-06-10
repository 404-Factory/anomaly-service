package com.factory.anomaly.service;

public interface AnalysisService {

    void updateAnalysis(Long anomalyId, String status, String summary);
}
