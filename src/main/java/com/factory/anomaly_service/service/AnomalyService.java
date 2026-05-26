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

    public List<AnomalyLogResponse> getAnomalyLogs(
            Long processId,
            Long equipmentId,
            String keyword
    ) {
        String normalizedKeyword = normalizeKeyword(keyword);

        return anomalyLogRepository.findAnomalyLogs(processId, equipmentId, normalizedKeyword)
                .stream()
                .map(AnomalyLogResponse::from)
                .toList();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}