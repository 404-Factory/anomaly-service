package com.factory.anomaly_service.service;

import com.factory.anomaly_service.domain.dto.response.AnomalyLogResponse;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyService {

    private final AnomalyLogRepository anomalyLogRepository;

    public List<AnomalyLogResponse> getAnomalyLogs() {
        return anomalyLogRepository.findAll()
                .stream()
                .map(AnomalyLogResponse::from)
                .toList();
    }
}