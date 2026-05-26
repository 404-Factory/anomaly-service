package com.factory.anomaly_service.controller;

import com.factory.anomaly_service.domain.dto.response.AnomalyLogResponse;
import com.factory.anomaly_service.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;

    @GetMapping
    public List<AnomalyLogResponse> getAnomalyLogs() {
        return anomalyService.getAnomalyLogs();
    }
}