package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AnomalyService {

    Page<AnomalyResponse> getAnomalies(Long processId, Long equipmentId, String keyword, Pageable pageable);
    AnomalyDetailResponse getAnomaly(Long anomalyId);
    long countAnomalies(String equipmentName, LocalDateTime since);
    Optional<Anomaly> detect(SensorViolationDto violation);
    Optional<Anomaly> processAnomalyDetectionInTransaction(
        SensorViolationDto violation,
        Long equipmentId,
        String equipmentCode,
        String sensorId,
        String ruleNameStr,
        String anomalyTypeStr
    );
}
