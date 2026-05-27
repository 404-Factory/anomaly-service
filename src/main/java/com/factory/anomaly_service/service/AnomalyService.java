package com.factory.anomaly_service.service;

import com.factory.anomaly_service.domain.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly_service.domain.dto.response.AnomalyLogResponse;
import com.factory.anomaly_service.domain.entity.EquipmentRecipeDetailEntity;
import com.factory.anomaly_service.exception.AnomalyErrorCode;
import com.factory.anomaly_service.exception.AnomalyException;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import com.factory.anomaly_service.repository.EquipmentRecipeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyService {

    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;

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

    public AnomalyLogDetailResponse getAnomalyLogDetail(Long anomalyId) {
        var anomalyLog = anomalyLogRepository.findById(anomalyId)
                .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        EquipmentRecipeDetailEntity recipeDetail = null;

        if (anomalyLog.getEquipmentRecipe() != null && anomalyLog.getRecipeParameter() != null) {
            recipeDetail = equipmentRecipeDetailRepository
                    .findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
                            anomalyLog.getEquipmentRecipe().getEquipmentRecipeId(),
                            anomalyLog.getRecipeParameter()
                    )
                    .orElse(null);
        }

        return AnomalyLogDetailResponse.of(anomalyLog, recipeDetail);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return keyword.trim();
    }
}