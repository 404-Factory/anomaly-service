package com.factory.anomaly.engine;

import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NelsonRule1Evaluator {

    private static final int CRITICAL_VIOLATION_COUNT = 3;

    public RuleResult evaluate(
            List<RuleSensorSample> samples,
            Double recipeMin,
            Double recipeMax
    ) {
        if (samples == null || samples.isEmpty()) {
            return RuleResult.normal("최근 5분 센서 데이터가 없습니다.");
        }

        List<RuleSensorSample> violatedSamples = samples.stream()
                .filter(sample -> sample.value() != null)
                .filter(sample -> isOutOfRecipeRange(sample.value(), recipeMin, recipeMax))
                .toList();

        if (violatedSamples.isEmpty()) {
            return RuleResult.normal("최근 5분 내 Recipe Min/Max 초과 데이터가 없습니다.");
        }

        RuleSensorSample latestViolatedSample = violatedSamples.get(violatedSamples.size() - 1);
        Double value = latestViolatedSample.value();

        boolean high = recipeMax != null && value > recipeMax;

        Double referenceValue = high ? recipeMax : recipeMin;
        Double deviation = high ? value - recipeMax : recipeMin - value;
        Double deviationRate = calculateDeviationRate(deviation, referenceValue);

        Severity severity = violatedSamples.size() >= CRITICAL_VIOLATION_COUNT
                ? Severity.CRITICAL
                : Severity.CAUTION;

        AnomalyType anomalyType = high ? AnomalyType.HIGH : AnomalyType.LOW;

        return RuleResult.detected(
                RuleName.NELSON_RULE_1,
                severity,
                anomalyType,
                value,
                referenceValue,
                deviation,
                deviationRate,
                String.format(
                        "최근 5분 내 Recipe 기준값 초과가 %d회 발생했습니다. 측정값 %.3f, 기준값 %.3f",
                        violatedSamples.size(),
                        value,
                        referenceValue
                )
        );
    }

    private boolean isOutOfRecipeRange(Double value, Double recipeMin, Double recipeMax) {
        if (recipeMin != null && value < recipeMin) {
            return true;
        }

        return recipeMax != null && value > recipeMax;
    }

    private double calculateDeviationRate(Double deviation, Double referenceValue) {
        if (referenceValue == null || referenceValue == 0.0) {
            return 0.0;
        }

        return Math.abs(deviation / referenceValue) * 100;
    }
}
