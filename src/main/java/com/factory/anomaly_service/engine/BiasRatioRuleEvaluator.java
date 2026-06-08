package com.factory.anomaly_service.engine;

import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BiasRatioRuleEvaluator {

    private static final int MIN_REQUIRED_SAMPLES = 240;
    private static final double SIGMA_RATIO = 0.167;
    private static final double BIAS_RATIO_THRESHOLD = 0.8;

    public RuleResult evaluate(
            List<RuleSensorSample> fiveMinuteSamples,
            Double recipeMin,
            Double recipeMax
    ) {
        List<Double> values = extractValues(fiveMinuteSamples);

        if (values.size() < MIN_REQUIRED_SAMPLES) {
            return RuleResult.normal("최근 5분 유효 데이터 수가 부족합니다.");
        }

        if (recipeMin == null || recipeMax == null || recipeMax <= recipeMin) {
            return RuleResult.normal("Recipe Min/Max 기준값이 유효하지 않습니다.");
        }

        double mean = (recipeMin + recipeMax) / 2.0;
        double sigma = (recipeMax - recipeMin) * SIGMA_RATIO;

        BiasStats oneSigmaStats = calculateBiasStats(values, mean, sigma);
        BiasStats twoSigmaStats = calculateBiasStats(values, mean, sigma * 2);

        if (twoSigmaStats.upperRatio() >= BIAS_RATIO_THRESHOLD) {
            return detected(
                    Severity.CRITICAL,
                    AnomalyType.BIAS_UP,
                    values,
                    mean + sigma * 2,
                    twoSigmaStats.upperRatio()
            );
        }

        if (twoSigmaStats.lowerRatio() >= BIAS_RATIO_THRESHOLD) {
            return detected(
                    Severity.CRITICAL,
                    AnomalyType.BIAS_DOWN,
                    values,
                    mean - sigma * 2,
                    twoSigmaStats.lowerRatio()
            );
        }

        if (oneSigmaStats.upperRatio() >= BIAS_RATIO_THRESHOLD) {
            return detected(
                    Severity.CAUTION,
                    AnomalyType.BIAS_UP,
                    values,
                    mean + sigma,
                    oneSigmaStats.upperRatio()
            );
        }

        if (oneSigmaStats.lowerRatio() >= BIAS_RATIO_THRESHOLD) {
            return detected(
                    Severity.CAUTION,
                    AnomalyType.BIAS_DOWN,
                    values,
                    mean - sigma,
                    oneSigmaStats.lowerRatio()
            );
        }

        return RuleResult.normal("최근 5분 데이터에서 편향 조건을 만족하지 않습니다.");
    }

    private List<Double> extractValues(List<RuleSensorSample> samples) {
        if (samples == null) {
            return List.of();
        }

        return samples.stream()
                .map(RuleSensorSample::value)
                .filter(value -> value != null)
                .toList();
    }

    private BiasStats calculateBiasStats(List<Double> values, double mean, double sigma) {
        long upperCount = values.stream()
                .filter(value -> value > mean + sigma)
                .count();

        long lowerCount = values.stream()
                .filter(value -> value < mean - sigma)
                .count();

        int totalCount = values.size();

        return new BiasStats(
                (double) upperCount / totalCount,
                (double) lowerCount / totalCount
        );
    }

    private RuleResult detected(
            Severity severity,
            AnomalyType anomalyType,
            List<Double> values,
            Double referenceValue,
            Double biasRatio
    ) {
        Double latestValue = values.get(values.size() - 1);
        Double deviation = Math.abs(latestValue - referenceValue);

        return RuleResult.detected(
                RuleName.BIAS_RATIO_RULE,
                severity,
                anomalyType,
                latestValue,
                referenceValue,
                deviation,
                biasRatio * 100,
                String.format(
                        "최근 5분 데이터 중 %.2f%%가 Recipe 중심값 기준 한쪽으로 편향되었습니다.",
                        biasRatio * 100
                )
        );
    }

    private record BiasStats(
            double upperRatio,
            double lowerRatio
    ) {
    }
}