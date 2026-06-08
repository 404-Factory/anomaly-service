package com.factory.anomaly.service;

import com.factory.anomaly.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly.dto.response.AnomalyLogResponse;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.repository.AnomalyLogRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyServiceImpl implements AnomalyService {

    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;

    @Override
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

    @Override
    public AnomalyLogDetailResponse getAnomalyLogDetail(Long anomalyId) {
        var anomalyLog = anomalyLogRepository.findById(anomalyId)
                .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        EquipmentRecipeDetail recipeDetail = null;

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
