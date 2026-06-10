package com.factory.anomaly.engine;

import com.factory.anomaly.domain.enums.Severity;
import org.springframework.stereotype.Component;

@Component
public class SeverityRanker {

    public int rank(Severity severity) {
        if (severity == null) {
            return 0;
        }

        return switch (severity) {
            case CAUTION -> 1;
            case WARNING -> 2;
            case CRITICAL -> 3;
        };
    }

    public Severity higher(Severity left, Severity right) {
        return rank(left) >= rank(right) ? left : right;
    }
}
