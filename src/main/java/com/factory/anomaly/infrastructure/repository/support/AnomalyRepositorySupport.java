package com.factory.anomaly.infrastructure.repository.support;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnomalyRepositorySupport {

    Page<AnomalyResponse> fetchAnomaliesWithCondition(Long processId, Long equipmentId,
        String keyword, Pageable pageable);

    AnomalyDetailResponse fetchAnomaly(Long id);
}
