package com.factory.anomaly.engine;

import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;

public record RuleResult(
        boolean detected,
        RuleName ruleName,
        Severity severity,
        AnomalyType anomalyType,
        Double measuredValue,
        Double referenceValue,
        Double deviation,
        Double deviationRate,
        String reason
) {

    public static RuleResult normal(String reason) {
        return new RuleResult(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                reason
        );
    }

    public static RuleResult detected(
            RuleName ruleName,
            Severity severity,
            AnomalyType anomalyType,
            Double measuredValue,
            Double referenceValue,
            Double deviation,
            Double deviationRate,
            String reason
    ) {
        return new RuleResult(
                true,
                ruleName,
                severity,
                anomalyType,
                measuredValue,
                referenceValue,
                deviation,
                deviationRate,
                reason
        );
    }
}
