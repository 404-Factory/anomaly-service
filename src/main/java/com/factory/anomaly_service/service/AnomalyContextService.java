package com.factory.anomaly_service.service;

import com.factory.anomaly_service.domain.dto.response.AnomalyContextResponse;
import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.domain.type.LogType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import com.factory.anomaly_service.exception.AnomalyErrorCode;
import com.factory.anomaly_service.exception.AnomalyException;
import com.factory.anomaly_service.repository.AnomalyLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyContextService {

    private static final int EXPECTED_SAMPLE_PER_MINUTE = 60;
    private static final int DEFAULT_CONTEXT_WINDOW_MINUTES = 5;

    private final AnomalyLogRepository anomalyLogRepository;
    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;

    /**
     * 이상 로그 상세 화면과 AI 분석 서비스가 함께 사용할 수 있는 context를 구성한다.
     * anomaly-service는 AI를 직접 수행하지 않고, RuleEngine 판단 결과를 AI가 소비하기 좋은 형태로 제공한다.
     */
    public AnomalyContextResponse getAnomalyContext(Long anomalyId) {
        AnomalyLogEntity anomalyLog = anomalyLogRepository.findById(anomalyId)
                .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        EquipmentRecipeDetailEntity recipeDetail = findRecipeDetail(anomalyLog);
        List<AnomalyLogEntity> relatedLogs = findRelatedLogs(anomalyLog);

        return new AnomalyContextResponse(
                toAnomalyInfo(anomalyLog),
                toEquipmentInfo(anomalyLog),
                toRecipeInfo(anomalyLog, recipeDetail),
                toRuleContext(anomalyLog),
                relatedLogs.stream()
                        .map(this::toRelatedLogInfo)
                        .toList(),
                List.of(), // 1차 MVP에서는 Redis 시계열은 제외하고, Rule context 중심으로 제공한다.
                toAiInputSummary(anomalyLog, recipeDetail, relatedLogs)
        );
    }

    /**
     * SENSOR 로그는 해당 파라미터의 레시피 기준값을 조회한다.
     * COMPOSITE 로그는 여러 SENSOR 로그를 묶은 결과이므로 단일 recipe detail을 조회하지 않는다.
     */
    private EquipmentRecipeDetailEntity findRecipeDetail(AnomalyLogEntity anomalyLog) {
        if (anomalyLog.getEquipmentRecipe() == null || anomalyLog.getRecipeParameter() == null) {
            return null;
        }

        if (anomalyLog.getLogType() == LogType.COMPOSITE) {
            return null;
        }

        if ("MULTI_SENSOR".equalsIgnoreCase(anomalyLog.getRecipeParameter())) {
            return null;
        }

        return equipmentRecipeDetailRepository
                .findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
                        anomalyLog.getEquipmentRecipe().getEquipmentRecipeId(),
                        anomalyLog.getRecipeParameter()
                )
                .orElse(null);
    }

    /**
     * COMPOSITE 로그의 relatedLogIds를 파싱해 원본 SENSOR 로그들을 함께 제공한다.
     */
    private List<AnomalyLogEntity> findRelatedLogs(AnomalyLogEntity anomalyLog) {
        if (anomalyLog.getRelatedLogIds() == null || anomalyLog.getRelatedLogIds().isBlank()) {
            return List.of();
        }

        List<Long> relatedIds = Arrays.stream(anomalyLog.getRelatedLogIds().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(Long::valueOf)
                .toList();

        if (relatedIds.isEmpty()) {
            return List.of();
        }

        return anomalyLogRepository.findAllById(relatedIds)
                .stream()
                .sorted(Comparator.comparing(AnomalyLogEntity::getOccurredTime))
                .toList();
    }

    private AnomalyContextResponse.AnomalyInfo toAnomalyInfo(AnomalyLogEntity anomalyLog) {
        return new AnomalyContextResponse.AnomalyInfo(
                anomalyLog.getLogId(),
                anomalyLog.getLogType(),
                anomalyLog.getSeverity(),
                toStatusLabel(anomalyLog.getSeverity()),
                anomalyLog.getOccurredTime(),
                anomalyLog.getRuleName(),
                anomalyLog.getAnomalyType(),
                anomalyLog.getRelatedLogIds()
        );
    }

    private AnomalyContextResponse.EquipmentInfo toEquipmentInfo(AnomalyLogEntity anomalyLog) {
        var equipment = anomalyLog.getEquipment();
        var process = equipment != null ? equipment.getProcess() : null;

        return new AnomalyContextResponse.EquipmentInfo(
                equipment != null ? equipment.getEquipmentId() : null,
                equipment != null ? equipment.getEquipmentName() : null,
                process != null ? process.getProcessId() : null,
                process != null ? process.getProcessName() : null
        );
    }

    private AnomalyContextResponse.RecipeInfo toRecipeInfo(
            AnomalyLogEntity anomalyLog,
            EquipmentRecipeDetailEntity recipeDetail
    ) {
        var equipmentRecipe = anomalyLog.getEquipmentRecipe();
        var masterRecipe = equipmentRecipe != null ? equipmentRecipe.getMasterRecipe() : null;

        Double recipeMin = recipeDetail != null ? recipeDetail.getMinValue() : null;
        Double recipeMax = recipeDetail != null ? recipeDetail.getMaxValue() : null;

        return new AnomalyContextResponse.RecipeInfo(
                equipmentRecipe != null ? equipmentRecipe.getEquipmentRecipeId() : null,
                masterRecipe != null ? masterRecipe.getMasterRecipeId() : null,
                equipmentRecipe != null && equipmentRecipe.getVersion() != null
                        ? String.valueOf(equipmentRecipe.getVersion())
                        : null,
                anomalyLog.getRecipeParameter(),
                anomalyLog.getRecipeParameter(),
                recipeMin,
                recipeMax,
                calculateCenterValue(recipeMin, recipeMax)
        );
    }

    /**
     * RuleEngine이 저장한 판단 수치를 AI가 바로 해석할 수 있는 형태로 정리한다.
     */
    private AnomalyContextResponse.RuleContext toRuleContext(AnomalyLogEntity anomalyLog) {
        LocalDateTime windowEndTime = anomalyLog.getOccurredTime();

        return new AnomalyContextResponse.RuleContext(
                anomalyLog.getRuleName(),
                ruleDescription(anomalyLog.getRuleName()),
                anomalyLog.getMeasuredValue(),
                anomalyLog.getReferenceValue(),
                anomalyLog.getDeviation(),
                anomalyLog.getDeviationRate(),
                anomalyLog.getDetectionReason(),
                anomalyLog.getWindowStartTime(),
                windowEndTime,
                expectedSampleCount(anomalyLog),
                anomalyLog.getSampleCount()
        );
    }

    private AnomalyContextResponse.RelatedLogInfo toRelatedLogInfo(AnomalyLogEntity relatedLog) {
        return new AnomalyContextResponse.RelatedLogInfo(
                relatedLog.getLogId(),
                relatedLog.getLogType(),
                relatedLog.getRecipeParameter(),
                relatedLog.getSeverity(),
                toStatusLabel(relatedLog.getSeverity()),
                relatedLog.getRuleName(),
                relatedLog.getAnomalyType(),
                relatedLog.getOccurredTime(),
                relatedLog.getDetectionReason()
        );
    }

    /**
     * AI 서비스가 프롬프트를 구성할 때 참고할 수 있는 요약 문장과 분석 관점을 제공한다.
     */
    private AnomalyContextResponse.AiInputSummary toAiInputSummary(
            AnomalyLogEntity anomalyLog,
            EquipmentRecipeDetailEntity recipeDetail,
            List<AnomalyLogEntity> relatedLogs
    ) {
        String recommendedAnalysisType = anomalyLog.getLogType() == LogType.COMPOSITE
                ? "MULTI_SENSOR_COMPOSITE_CONTEXT"
                : "RULE_VIOLATION_WITH_RECIPE_CONTEXT";

        return new AnomalyContextResponse.AiInputSummary(
                buildSummaryText(anomalyLog, relatedLogs),
                recommendedAnalysisType,
                buildAnalysisFocus(anomalyLog),
                buildPromptHint(anomalyLog)
        );
    }

    private String buildSummaryText(
            AnomalyLogEntity anomalyLog,
            List<AnomalyLogEntity> relatedLogs
    ) {
        var equipment = anomalyLog.getEquipment();
        String equipmentName = equipment != null ? equipment.getEquipmentName() : "알 수 없는 설비";

        if (anomalyLog.getLogType() == LogType.COMPOSITE) {
            return String.format(
                    "%s 설비에서 %d개의 관련 센서 이상이 함께 감지되어 %s 수준의 복합 이상으로 분류되었습니다.",
                    equipmentName,
                    relatedLogs.size(),
                    anomalyLog.getSeverity()
            );
        }

        if (anomalyLog.getMeasuredValue() != null && anomalyLog.getReferenceValue() != null) {
            return String.format(
                    "%s 설비의 %s 값이 %s에 기준값 %.3f 대비 %.3f으로 측정되어 %s 이상으로 분류되었습니다.",
                    equipmentName,
                    anomalyLog.getRecipeParameter(),
                    anomalyLog.getOccurredTime(),
                    anomalyLog.getReferenceValue(),
                    anomalyLog.getMeasuredValue(),
                    anomalyLog.getSeverity()
            );
        }

        return String.format(
                "%s 설비의 %s 항목에서 %s 룰에 의해 %s 이상이 감지되었습니다.",
                equipmentName,
                anomalyLog.getRecipeParameter(),
                anomalyLog.getRuleName(),
                anomalyLog.getSeverity()
        );
    }

    private List<String> buildAnalysisFocus(AnomalyLogEntity anomalyLog) {
        if (anomalyLog.getLogType() == LogType.COMPOSITE) {
            return List.of(
                    "복수 센서 이상 간 시간적 연관성",
                    "동일 설비 내 반복 이상 여부",
                    "현재 적용 레시피와 센서 기준값의 적합성",
                    "불량 정보와의 연관 가능성"
            );
        }

        return List.of(
                "레시피 기준값 초과 여부",
                "측정값과 기준값의 편차",
                "동일 설비의 최근 반복 이상 여부",
                "관련 센서의 동시 이상 여부"
        );
    }

    private String buildPromptHint(AnomalyLogEntity anomalyLog) {
        if (anomalyLog.getLogType() == LogType.COMPOSITE) {
            return "관련 센서 로그들을 함께 비교해 공정 조건 불안정 가능성, 레시피 점검 필요성, 권장 조치를 설명하세요.";
        }

        return "레시피 기준값, 측정값 편차, 감지 룰, 최근 관련 이상 로그를 함께 고려해 원인 가능성과 권장 조치를 설명하세요.";
    }

    private Integer expectedSampleCount(AnomalyLogEntity anomalyLog) {
        if (anomalyLog.getLogType() == LogType.COMPOSITE) {
            return null;
        }

        return DEFAULT_CONTEXT_WINDOW_MINUTES * EXPECTED_SAMPLE_PER_MINUTE;
    }

    private String ruleDescription(RuleName ruleName) {
        if (ruleName == null) {
            return null;
        }

        return switch (ruleName) {
            case NELSON_RULE_1 -> "Recipe Min/Max 기준값 초과 감지";
            case NELSON_RULE_3 -> "최근 센서 데이터의 증가 또는 감소 추세 감지";
            case BIAS_RATIO_RULE -> "최근 센서 데이터의 한쪽 방향 편향 감지";
            case COMPOSITE_RULE -> "동일 설비 내 복수 센서 이상 조합 감지";
        };
    }

    private Double calculateCenterValue(Double minValue, Double maxValue) {
        if (minValue == null || maxValue == null) {
            return null;
        }

        return (minValue + maxValue) / 2.0;
    }

    private String toStatusLabel(Severity severity) {
        if (severity == null) {
            return null;
        }

        return switch (severity) {
            case CRITICAL -> "긴급";
            case WARNING -> "경고";
            case CAUTION -> "주의";
        };
    }
}