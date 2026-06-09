package com.factory.anomaly.service;

import com.factory.anomaly.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly.dto.response.AnomalyLogResponse;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.client.ChatbotServiceClient;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import com.factory.anomaly.infrastructure.repository.AnomalyLogRepository;
import com.factory.anomaly.infrastructure.repository.DefectRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyServiceImpl implements AnomalyService {

    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;
    private final DefectRepository defectRepository;
    private final ChatbotServiceClient chatbotServiceClient;

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
    @Transactional
    public AnomalyLogDetailResponse getAnomalyLogDetail(Long anomalyId) {
        var anomalyLog = anomalyLogRepository.findById(anomalyId)
                .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        String currentAiAnalysis = anomalyLog.getAiAnalysis();
        boolean isError = currentAiAnalysis != null && (currentAiAnalysis.startsWith("AI 분석 호출 실패") || currentAiAnalysis.startsWith("AI 분석 리포트 생성 중 예외"));
        if (currentAiAnalysis == null || currentAiAnalysis.isBlank() || isError) {
            System.out.println("[DEBUG] aiAnalysis is empty or error. Fetching correlated defects and requesting AI analysis...");
            try {
                Long equipmentId = anomalyLog.getEquipment() != null ? anomalyLog.getEquipment().getId() : null;
                Instant anomalyTime = anomalyLog.getLastDetectedAt();

                List<ChatbotServiceClient.DefectDto> defectDtos = List.of();
                if (equipmentId != null && anomalyTime != null) {
                    Instant endTime = anomalyTime.plus(30, ChronoUnit.MINUTES);
                    var defects = defectRepository.findCorrelatedDefects(equipmentId, anomalyTime, endTime);
                    System.out.println("[DEBUG] Found " + defects.size() + " correlated defects");
                    defectDtos = defects.stream()
                            .map(d -> ChatbotServiceClient.DefectDto.builder()
                                    .lotId(d.getLotId())
                                    .defectType(d.getDefectType())
                                    .defectCode(d.getDefectCode())
                                    .occurredTime(d.getOccurredTime())
                                    .detectedTime(d.getDetectedTime())
                                    .build())
                            .collect(Collectors.toList());
                }

                ChatbotServiceClient.AnomalyAnalysisRequest analysisRequest = ChatbotServiceClient.AnomalyAnalysisRequest.builder()
                        .equipmentName(anomalyLog.getEquipment() != null ? anomalyLog.getEquipment().getName() : "N/A")
                        .recipeParameter(anomalyLog.getRecipeParameter())
                        .ruleName(anomalyLog.getRuleName() != null ? anomalyLog.getRuleName().name() : "N/A")
                        .anomalyType(anomalyLog.getAnomalyType() != null ? anomalyLog.getAnomalyType().name() : "N/A")
                        .detectionReason(anomalyLog.getDetectionReason())
                        .occurredTime(anomalyTime)
                        .defects(defectDtos)
                        .build();

                String aiResult = chatbotServiceClient.getAnomalyAnalysis(analysisRequest);
                anomalyLog.setAiAnalysis(aiResult);

                boolean newResultIsError = aiResult != null && (aiResult.startsWith("AI 분석 호출 실패") || aiResult.startsWith("AI 분석 리포트 생성 중 예외"));
                if (!newResultIsError) {
                    anomalyLogRepository.save(anomalyLog);
                    System.out.println("[DEBUG] AI Analysis generated and saved successfully.");
                } else {
                    System.out.println("[WARN] AI Analysis generation failed, not saving to DB: " + aiResult);
                }
            } catch (Exception e) {
                System.err.println("[ERROR] Failed during AI Anomaly Analysis generation: " + e.getMessage());
                e.printStackTrace();
            }
        }

        EquipmentRecipeDetail recipeDetail = null;

        if (anomalyLog.getEquipmentRecipe() != null && anomalyLog.getRecipeParameter() != null) {
            recipeDetail = equipmentRecipeDetailRepository
                    .findById(new EquipmentRecipeDetailId(
                            anomalyLog.getEquipmentRecipe().getId(),
                            anomalyLog.getRecipeParameter()
                    ))
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

