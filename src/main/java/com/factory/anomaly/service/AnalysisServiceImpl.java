package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.AnalysisStatus;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.entity.Analysis;
import com.factory.anomaly.infrastructure.repository.AnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnalysisServiceImpl implements AnalysisService {

    private final AnalysisRepository analysisRepository;

    @Override
    public void updateAnalysis(Long anomalyId, String status, String summary) {
        Analysis analysis = analysisRepository.findByAnomalyId(anomalyId)
            .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANALYSIS_NOT_FOUND));
        analysis.update(AnalysisStatus.valueOf(status.toUpperCase()), summary);
    }
}
