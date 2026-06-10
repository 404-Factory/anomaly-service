package com.factory.anomaly.domain.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
public class AnomalySearchCondition {

    private Long processId;
    private Long equipmentId;
    private String keyword;
}
