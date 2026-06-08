package com.factory.anomaly_service.engine;

import com.factory.anomaly_service.domain.type.AnomalyType;
import com.factory.anomaly_service.domain.type.RuleName;
import com.factory.anomaly_service.domain.type.Severity;

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