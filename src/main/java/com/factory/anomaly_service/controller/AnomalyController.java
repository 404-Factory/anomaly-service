package com.factory.anomaly_service.controller;

import com.factory.anomaly_service.domain.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly_service.domain.dto.response.AnomalyLogResponse;
import com.factory.anomaly_service.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;

    @GetMapping
    public List<AnomalyLogResponse> getAnomalyLogs(
            @RequestParam(required = false) Long processId,
            @RequestParam(required = false) Long equipmentId,
            @RequestParam(required = false) String keyword
    ) {
        return anomalyService.getAnomalyLogs(processId, equipmentId, keyword);
    }

    @GetMapping("/{anomalyId}")
    public AnomalyLogDetailResponse getAnomalyLogDetail(
            @PathVariable Long anomalyId
    ) {
        return anomalyService.getAnomalyLogDetail(anomalyId);
    }
}