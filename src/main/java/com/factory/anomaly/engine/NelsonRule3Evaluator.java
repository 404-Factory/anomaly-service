package com.factory.anomaly.engine;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class NelsonRule3Evaluator {

    private static final int MIN_REQUIRED_SAMPLES = 42;
    private static final double DIRECTION_RATIO_THRESHOLD = 0.7;
    private static final double CHANGE_RATE_THRESHOLD = 5.0;
    private static final double LIMIT_PROXIMITY_THRESHOLD = 0.9;

    public RuleResult evaluate(
            List<RuleSensorSample> oneMinuteSamples,
            Double recipeMin,
            Double recipeMax
    ) {
        List<RuleSensorSample> samples = normalize(oneMinuteSamples);

        if (samples.size() < MIN_REQUIRED_SAMPLES) {
            return RuleResult.normal("최근 1분 유효 데이터 수가 부족합니다.");
        }

        DirectionStats directionStats = calculateDirectionStats(samples);

        RuleSensorSample first = samples.get(0);
        RuleSensorSample last = samples.get(samples.size() - 1);

        if (first.value() == null || last.value() == null || first.value() == 0.0) {
            return RuleResult.normal("추세 판단을 위한 시작값 또는 종료값이 유효하지 않습니다.");
        }

        double changeRate = Math.abs((last.value() - first.value()) / first.value()) * 100;

        boolean trendUp = directionStats.increaseRatio() >= DIRECTION_RATIO_THRESHOLD;
        boolean trendDown = directionStats.decreaseRatio() >= DIRECTION_RATIO_THRESHOLD;

        if ((!trendUp && !trendDown) || changeRate < CHANGE_RATE_THRESHOLD) {
            return RuleResult.normal("증가/감소 추세 조건을 만족하지 않습니다.");
        }

        AnomalyType anomalyType = trendUp ? AnomalyType.TREND_UP : AnomalyType.TREND_DOWN;

        Severity severity = isNearLimit(trendUp, last.value(), recipeMin, recipeMax)
                ? Severity.CRITICAL
                : Severity.CAUTION;

        Double referenceValue = trendUp ? recipeMax : recipeMin;

        return RuleResult.detected(
                RuleName.NELSON_RULE_3,
                severity,
                anomalyType,
                last.value(),
                referenceValue,
                Math.abs(last.value() - first.value()),
                changeRate,
                String.format(
                        "최근 1분 데이터에서 %s 추세가 감지되었습니다. 방향 비율 %.2f, 변화율 %.2f%%",
                        trendUp ? "증가" : "감소",
                        trendUp ? directionStats.increaseRatio() : directionStats.decreaseRatio(),
                        changeRate
                )
        );
    }

    private List<RuleSensorSample> normalize(List<RuleSensorSample> samples) {
        if (samples == null) {
            return List.of();
        }

        return samples.stream()
                .filter(sample -> sample.value() != null)
                .sorted(Comparator.comparing(RuleSensorSample::timestamp))
                .toList();
    }

    private DirectionStats calculateDirectionStats(List<RuleSensorSample> samples) {
        int increaseCount = 0;
        int decreaseCount = 0;
        int totalIntervals = 0;

        for (int i = 1; i < samples.size(); i++) {
            double previous = samples.get(i - 1).value();
            double current = samples.get(i).value();

            if (current > previous) {
                increaseCount++;
            } else if (current < previous) {
                decreaseCount++;
            }

            totalIntervals++;
        }

        if (totalIntervals == 0) {
            return new DirectionStats(0.0, 0.0);
        }

        return new DirectionStats(
                (double) increaseCount / totalIntervals,
                (double) decreaseCount / totalIntervals
        );
    }

    private boolean isNearLimit(
            boolean trendUp,
            Double currentValue,
            Double recipeMin,
            Double recipeMax
    ) {
        if (currentValue == null || recipeMin == null || recipeMax == null) {
            return false;
        }

        double range = recipeMax - recipeMin;

        if (range <= 0.0) {
            return false;
        }

        if (trendUp) {
            return (currentValue - recipeMin) / range >= LIMIT_PROXIMITY_THRESHOLD;
        }

        return (recipeMax - currentValue) / range >= LIMIT_PROXIMITY_THRESHOLD;
    }

    private record DirectionStats(
            double increaseRatio,
            double decreaseRatio
    ) {
    }
}
