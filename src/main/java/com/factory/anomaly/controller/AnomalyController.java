package com.factory.anomaly.controller;

import com.factory.anomaly.domain.dto.request.AnomalySearchCondition;
import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.infrastructure.entity.Analysis;
import com.factory.anomaly.service.AnalysisSseService;
import com.factory.anomaly.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;
    private final AnalysisSseService analysisSseService;

    @GetMapping
    public Page<AnomalyResponse> getAnomalies(@ModelAttribute AnomalySearchCondition condition,
        @PageableDefault(sort = "") Pageable pageable) {
        Long processId = condition.getProcessId();
        Long equipmentId = condition.getEquipmentId();
        String keyword = condition.getKeyword();
        return anomalyService.getAnomalies(processId, equipmentId, keyword, pageable);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnomalyDetailResponse> getAnomalyLogDetail(
        @PathVariable(name = "id") Long id
    ) {
        return null;
        // return anomalyService.getAnomalyLogDetail(anomalyId);
    }

    @GetMapping(value = "/{id}/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable(name = "id") Long id) {
        return analysisSseService.subscribe(id);
    }
}
