package com.factory.anomaly.controller;

import com.factory.anomaly.domain.dto.request.AnomalySearchCondition;
import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.service.AnomalyService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/anomalies")
@RequiredArgsConstructor
public class AnomalyController {

    private final AnomalyService anomalyService;

    @GetMapping
    public Page<AnomalyResponse> getAnomalies(@ModelAttribute AnomalySearchCondition condition,
        @PageableDefault Pageable pageable) {
        Long processId = condition.getProcessId();
        Long equipmentId = condition.getEquipmentId();
        String keyword = condition.getKeyword();
        return anomalyService.getAnomalies(processId, equipmentId, keyword, pageable);
    }

    @GetMapping({"/{id}", "/{id}/context"})
    public ResponseEntity<AnomalyDetailResponse> getAnomalyLogDetail(
        @PathVariable(name = "id") Long id
    ) {
        return ResponseEntity.ok(anomalyService.getAnomaly(id));
    }

    @GetMapping("/{id}/analysis")
    public ResponseEntity<Void> triggerAnalysis(@PathVariable(name = "id") Long id) {
        anomalyService.triggerAnalysis(id);
        return ResponseEntity.noContent().build();
    }
}
