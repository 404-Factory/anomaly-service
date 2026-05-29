package com.factory.anomaly_service.controller;

import com.factory.anomaly_service.domain.dto.request.AnomalyDetectionRequest;
import com.factory.anomaly_service.domain.dto.response.AnomalyDetectionResponse;
import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import com.factory.anomaly_service.service.AnomalyDetectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/anomalies")
public class AnomalyDetectionController {

    private final AnomalyDetectionService anomalyDetectionService;

    @PostMapping("/detect")
    public ResponseEntity<?> detect(@RequestBody AnomalyDetectionRequest request) {
        LocalDateTime detectedAt = request.detectedAt() != null
                ? request.detectedAt()
                : LocalDateTime.now();

        Optional<AnomalyLogEntity> anomalyLog = anomalyDetectionService.detect(
                request.equipmentCode(),
                request.sensorType(),
                detectedAt
        );

        if (anomalyLog.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "detected", false,
                    "message", "감지된 이상이 없거나 기준정보/센서 데이터가 없습니다."
            ));
        }

        return ResponseEntity.ok(Map.of(
                "detected", true,
                "data", AnomalyDetectionResponse.from(anomalyLog.get())
        ));
    }
}