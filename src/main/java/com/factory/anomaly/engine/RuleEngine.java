package com.factory.anomaly.engine;

import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RuleEngine {

    private final NelsonRule1Evaluator nelsonRule1Evaluator;
    private final NelsonRule3Evaluator nelsonRule3Evaluator;
    private final BiasRatioRuleEvaluator biasRatioRuleEvaluator;
    private final SeverityRanker severityRanker;

    // 5분간 redis 조회 -> nelson 1, bias 평가
    // 1분간 redis 조회 -> nelson 3 평가
    // 가장 높은 priority 값과, 가장 높은 severity 값을 가져올 건데...
    // 최종 결과는? 그게 짬뽕된 하나임
    // 예시 nelson 1과 bias가 발생
    // nelson 1이 개심각했어.
    // 근데 bias가 더 우선도가 높거든?
    // 그러면 bias를 이상 정보로 하고 심각도는 nelson 1에서 가져와
    public RuleResult evaluate(
            List<RuleSensorSample> fiveMinuteSamples,
            List<RuleSensorSample> oneMinuteSamples,
            Double recipeMin,
            Double recipeMax
    ) {
        // 각 rule에 의해서 List<RuleResult> 뽑아 // 정상도 나올 수 있음
        List<RuleResult> ruleResults = List.of(
                nelsonRule1Evaluator.evaluate(fiveMinuteSamples, recipeMin, recipeMax),
                nelsonRule3Evaluator.evaluate(oneMinuteSamples, recipeMin, recipeMax),
                biasRatioRuleEvaluator.evaluate(fiveMinuteSamples, recipeMin, recipeMax)
        );

        List<RuleResult> detectedResults = new ArrayList<>();

        // 감지된 것만 뽑음
        for (RuleResult ruleResult : ruleResults) {
            if (ruleResult.detected()) {
                detectedResults.add(ruleResult);
            }
        }

        if (detectedResults.isEmpty()) {
            return RuleResult.normal("감지된 이상 룰이 없습니다.");
        }

        // 가장 높은 priority, 가장 높은 severity 찾아내
        RuleResult causeRuleResult = findHighestPriorityRuleResult(detectedResults);
        Severity highestSeverity = findHighestSeverity(detectedResults);

        // 감지가 됐는데, 이 rule에 의해서 감지되었고, 이 심각도를 가져
        return RuleResult.detected(
                causeRuleResult.ruleName(),
                highestSeverity,
                causeRuleResult.anomalyType(),
                causeRuleResult.measuredValue(),
                causeRuleResult.referenceValue(),
                causeRuleResult.deviation(),
                causeRuleResult.deviationRate(),
                buildCombinedReason(detectedResults, causeRuleResult, highestSeverity)
        );
    }

    // nelson 1 < nelson 3 < bias < composite 순서로 priority가 높은가봄
    // 결과중 가장 높은 detect 뽑는 거네 그냥
    // 이거는 자료구조를 개편하던가 아니면 List 순서 생각하면 순회 필요 없음
    private RuleResult findHighestPriorityRuleResult(List<RuleResult> detectedResults) {
        RuleResult highestPriorityResult = detectedResults.get(0);

        // 리스트 순회할 거야
        for (RuleResult ruleResult : detectedResults) {
            if (rulePriority(ruleResult.ruleName()) < rulePriority(highestPriorityResult.ruleName())) {
                highestPriorityResult = ruleResult;
            }
        }

        return highestPriorityResult;
    }

    private Severity findHighestSeverity(List<RuleResult> detectedResults) {
        Severity highestSeverity = detectedResults.get(0).severity();

        for (RuleResult ruleResult : detectedResults) {
            highestSeverity = severityRanker.higher(highestSeverity, ruleResult.severity());
        }

        return highestSeverity;
    }

    // COMPOSITE이 애초에 쓰이긴 함?
    private int rulePriority(RuleName ruleName) {
        return switch (ruleName) {
            case NELSON_RULE_1 -> 1;
            case NELSON_RULE_3 -> 2;
            case BIAS_RATIO_RULE -> 3;
            case COMPOSITE_RULE -> 4;
        };
    }

    private String buildCombinedReason(
            List<RuleResult> detectedResults,
            RuleResult causeRuleResult,
            Severity highestSeverity
    ) {
        List<String> detectedRuleNames = new ArrayList<>();

        for (RuleResult ruleResult : detectedResults) {
            detectedRuleNames.add(ruleResult.ruleName().name());
        }

        return String.format(
                "감지 룰=%s, 대표 룰=%s, 최종 심각도=%s. %s",
                detectedRuleNames,
                causeRuleResult.ruleName(),
                highestSeverity,
                causeRuleResult.reason()
        );
    }
}
