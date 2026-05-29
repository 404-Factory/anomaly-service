package com.factory.anomaly_service.engine;

import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
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

    public RuleResult evaluate(
            List<RuleSensorSample> fiveMinuteSamples,
            List<RuleSensorSample> oneMinuteSamples,
            Double recipeMin,
            Double recipeMax
    ) {
        List<RuleResult> ruleResults = List.of(
                nelsonRule1Evaluator.evaluate(fiveMinuteSamples, recipeMin, recipeMax),
                nelsonRule3Evaluator.evaluate(oneMinuteSamples, recipeMin, recipeMax),
                biasRatioRuleEvaluator.evaluate(fiveMinuteSamples, recipeMin, recipeMax)
        );

        List<RuleResult> detectedResults = new ArrayList<>();

        for (RuleResult ruleResult : ruleResults) {
            if (ruleResult.detected()) {
                detectedResults.add(ruleResult);
            }
        }

        if (detectedResults.isEmpty()) {
            return RuleResult.normal("감지된 이상 룰이 없습니다.");
        }

        RuleResult causeRuleResult = findHighestPriorityRuleResult(detectedResults);
        Severity highestSeverity = findHighestSeverity(detectedResults);

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

    private RuleResult findHighestPriorityRuleResult(List<RuleResult> detectedResults) {
        RuleResult highestPriorityResult = detectedResults.get(0);

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

    private int rulePriority(RuleName ruleName) {
        return switch (ruleName) {
            case NELSON_RULE_1 -> 1;
            case NELSON_RULE_3 -> 2;
            case BIAS_RATIO_RULE -> 3;
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