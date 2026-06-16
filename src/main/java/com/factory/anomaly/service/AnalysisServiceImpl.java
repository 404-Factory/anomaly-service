package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnalysisResponseDto;
import com.factory.anomaly.domain.enums.AnalysisStatus;
import com.factory.anomaly.domain.enums.LogType;
import com.factory.anomaly.event.payload.producer.AnalysisRequestedPayload;
import com.factory.anomaly.event.type.AnalysisEventType;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.entity.Analysis;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.repository.AnalysisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.kafka.publisher.EventPublisher;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisServiceImpl implements AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final AnomalyRepository anomalyRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Override
    @Transactional
    public void updateAnalysis(Long anomalyId, String status, String summary) {
        Analysis analysis = analysisRepository.findByAnomalyId(anomalyId)
            .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANALYSIS_NOT_FOUND));
        AnalysisStatus analysisStatus;
        if ("SUCCESS".equalsIgnoreCase(status)) {
            analysisStatus = AnalysisStatus.COMPLETED;
        } else if ("FAILURE".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)) {
            analysisStatus = AnalysisStatus.FAILED;
        } else {
            try {
                analysisStatus = AnalysisStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                analysisStatus = AnalysisStatus.FAILED;
            }
        }
        analysis.update(analysisStatus, summary);
        analysisRepository.save(analysis);
    }

    @Override
    @Transactional
    public void triggerAnalysis(Long anomalyId) {
        Anomaly anomaly = anomalyRepository.findById(anomalyId)
            .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        Analysis analysis = analysisRepository.findByAnomalyId(anomalyId)
            .orElse(null);

        if (analysis == null) {
            analysisRepository.save(Analysis.builder()
                .anomalyId(anomalyId)
                .status(AnalysisStatus.RUNNING)
                .summary(null)
                .build());
        } else {
            analysis.update(AnalysisStatus.RUNNING, null);
            analysisRepository.save(analysis);
        }

        AnalysisRequestedPayload payload = buildAnalysisPayload(anomaly);

        eventPublisher.publish(
            domainEventFactory.create(AnalysisEventType.ANALYSIS_REQUESTED, "Anomaly",
                String.valueOf(anomaly.getId()), payload));
    }

    @Override
    public AnalysisResponseDto getAnalysis(Long anomalyId) {
        return analysisRepository.findByAnomalyId(anomalyId)
            .map(analysis -> new AnalysisResponseDto(analysis.getStatus().name(), analysis.getSummary()))
            .orElseGet(() -> new AnalysisResponseDto(null, null));
    }

    private AnalysisRequestedPayload buildAnalysisPayload(Anomaly anomaly) {
        EquipmentProjection equipment = equipmentProjectionRepository.findById(anomaly.getEquipmentId())
                .orElse(null);
        String equipmentName = equipment != null ? equipment.getName() : "알 수 없는 설비";

        String summaryText;
        if (anomaly.getMeasuredValue() != null && anomaly.getReferenceValue() != null) {
            summaryText = String.format(
                    "%s 설비의 %s 값이 %s에 기준값 %.3f 대비 %.3f으로 측정되어 %s 이상으로 분류되었습니다.",
                    equipmentName,
                    anomaly.getRecipeParameter(),
                    anomaly.getLastDetectedAt(),
                    anomaly.getReferenceValue(),
                    anomaly.getMeasuredValue(),
                    anomaly.getSeverity()
            );
        } else {
            summaryText = String.format(
                    "%s 설비의 %s 항목에서 %s 룰에 의해 %s 이상이 감지되었습니다.",
                    equipmentName,
                    anomaly.getRecipeParameter(),
                    anomaly.getRuleName(),
                    anomaly.getSeverity()
            );
        }

        String recommendedAnalysisType = "RULE_VIOLATION_WITH_RECIPE_CONTEXT";

        List<String> analysisFocus = List.of(
                "레시피 기준값 초과 여부",
                "측정값과 기준값의 편차",
                "동일 설비의 최근 반복 이상 여부",
                "관련 센서의 동시 이상 여부"
        );

        String llmPromptHint = "레시피 기준값, 측정값 편차, 감지 룰, 최근 관련 이상 로그를 함께 고려해 원인 가능성과 권장 조치를 설명하세요.";

        return AnalysisRequestedPayload.builder()
                .anomalyId(anomaly.getId())
                .equipmentId(anomaly.getEquipmentId())
                .recipeParameter(anomaly.getRecipeParameter())
                .ruleName(anomaly.getRuleName() != null ? anomaly.getRuleName().name() : null)
                .anomalyType(anomaly.getAnomalyType() != null ? anomaly.getAnomalyType().name() : null)
                .detectionReason(anomaly.getDetectionReason())
                .firstDetectedAt(anomaly.getFirstDetectedAt())
                .summaryText(summaryText)
                .recommendedAnalysisType(recommendedAnalysisType)
                .analysisFocus(analysisFocus)
                .llmPromptHint(llmPromptHint)
                .build();
    }
}
