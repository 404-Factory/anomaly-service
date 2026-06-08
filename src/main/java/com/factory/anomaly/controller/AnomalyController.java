package com.factory.anomaly.controller;

import com.factory.anomaly.dto.response.AnomalyContextResponse;
import com.factory.anomaly.dto.response.AnomalyLogDetailResponse;
import com.factory.anomaly.dto.response.AnomalyLogResponse;
import com.factory.anomaly.service.AnomalyContextService;
import com.factory.anomaly.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;
    private final AnomalyContextService anomalyContextService;

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

    @GetMapping("/{anomalyId}/context")
    public AnomalyContextResponse getAnomalyContext(
            @PathVariable Long anomalyId
    ) {
        return anomalyContextService.getAnomalyContext(anomalyId);
    }
}
