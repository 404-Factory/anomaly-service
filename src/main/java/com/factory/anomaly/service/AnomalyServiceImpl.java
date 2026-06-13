package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyServiceImpl implements AnomalyService {

    private final AnomalyRepository anomalyRepository;
    private final AnalysisRepository analysisRepository;
    private final EquipmentProjectionRepository equipmentProjectionRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Override
    public Page<AnomalyResponse> getAnomalies(Long processId, Long equipmentId, String keyword,
        Pageable pageable) {
        return anomalyRepository.fetchAnomaliesWithCondition(processId, equipmentId, keyword,
            pageable);
    }


    @Override
    public AnomalyDetailResponse getAnomaly(Long anomalyId) {
        Anomaly anomaly = anomalyRepository.findById(anomalyId)
            .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));
        AnomalyDetailResponse response = anomalyRepository.fetchAnomaly(anomalyId);
        if (response != null) {
            List<AnomalyDetailResponse.ViolationResponse> violationDtos = anomaly.getViolations().stream()
                .map(AnomalyDetailResponse.ViolationResponse::new)
                .toList();
            response.setViolations(violationDtos);
        }
        return response;
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
            domainEventFactory.create(AnalysisEventType.ANALYSIS_REQUESTED, "Analysis",
                String.valueOf(anomaly.getId()), payload));
    }

    private AnalysisRequestedPayload buildAnalysisPayload(Anomaly anomaly) {
        EquipmentProjection equipment = equipmentProjectionRepository.findById(anomaly.getEquipmentId())
                .orElse(null);
        String equipmentName = equipment != null ? equipment.getName() : "알 수 없는 설비";

        List<Anomaly> relatedLogs = List.of();
        if (anomaly.getRelatedLogIds() != null && !anomaly.getRelatedLogIds().isBlank()) {
            List<Long> relatedIds = Arrays.stream(anomaly.getRelatedLogIds().split(","))
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(Long::valueOf)
                    .toList();
            if (!relatedIds.isEmpty()) {
                relatedLogs = anomalyRepository.findAllById(relatedIds)
                        .stream()
                        .sorted(Comparator.comparing(Anomaly::getLastDetectedAt))
                        .toList();
            }
        }

        String summaryText;
        if (anomaly.getLogType() == LogType.COMPOSITE) {
            summaryText = String.format(
                    "%s 설비에서 %d개의 관련 센서 이상이 함께 감지되어 %s 수준의 복합 이상으로 분류되었습니다.",
                    equipmentName,
                    relatedLogs.size(),
                    anomaly.getSeverity()
            );
        } else if (anomaly.getMeasuredValue() != null && anomaly.getReferenceValue() != null) {
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

        String recommendedAnalysisType = anomaly.getLogType() == LogType.COMPOSITE
                ? "MULTI_SENSOR_COMPOSITE_CONTEXT"
                : "RULE_VIOLATION_WITH_RECIPE_CONTEXT";

        List<String> analysisFocus;
        if (anomaly.getLogType() == LogType.COMPOSITE) {
            analysisFocus = List.of(
                    "복수 센서 이상 간 시간적 연관성",
                    "동일 설비 내 반복 이상 여부",
                    "현재 적용 레시피와 센서 기준값의 적합성",
                    "불량 정보와의 연관 가능성"
            );
        } else {
            analysisFocus = List.of(
                    "레시피 기준값 초과 여부",
                    "측정값과 기준값의 편차",
                    "동일 설비의 최근 반복 이상 여부",
                    "관련 센서의 동시 이상 여부"
            );
        }

        String llmPromptHint = anomaly.getLogType() == LogType.COMPOSITE
                ? "관련 센서 로그들을 함께 비교해 공정 조건 불안정 가능성, 레시피 점검 필요성, 권장 조치를 설명하세요."
                : "레시피 기준값, 측정값 편차, 감지 룰, 최근 관련 이상 로그를 함께 고려해 원인 가능성과 권장 조치를 설명하세요.";

        return AnalysisRequestedPayload.builder()
                .anomalyId(anomaly.getId())
                .equipmentId(anomaly.getEquipmentId())
                .recipeParameter(anomaly.getRecipeParameter())
                .summaryText(summaryText)
                .recommendedAnalysisType(recommendedAnalysisType)
                .analysisFocus(analysisFocus)
                .llmPromptHint(llmPromptHint)
                .build();
    }
}

