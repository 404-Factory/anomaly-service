package com.factory.anomaly.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.service.AnomalyService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("이상 감지 컨트롤러 테스트")
class AnomalyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnomalyService anomalyService;

    @MockitoBean
    private SensorRedisRepository sensorRedisRepository;

    @Test
    @DisplayName("이상 목록을 조회한다")
    void getAnomalies() throws Exception {
        AnomalyResponse response = new AnomalyResponse(
            1L, "Anomaly_EQP-01_TEMP", "SENSOR", Severity.WARNING,
            "PHOTO", "EQP-01", "TEMP", "NELSON_RULE_3",
            "HIGH", Instant.parse("2026-06-10T12:00:00Z"), "Nelson Rule 3 Violation", null
        );

        when(anomalyService.getAnomalies(eq(1L), eq(null), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/anomalies")
                .param("processId", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].id").value(1L))
            .andExpect(jsonPath("$.data.content[0].severity").value("WARNING"))
            .andExpect(jsonPath("$.data.content[0].equipmentName").value("EQP-01"))
            .andExpect(jsonPath("$.data.content[0].recipeParameter").value("TEMP"));

        verify(anomalyService).getAnomalies(eq(1L), eq(null), eq(null), any(Pageable.class));
    }

    @Test
    @DisplayName("이상 단건을 조회한다")
    void getAnomalyDetail() throws Exception {
        AnomalyDetailResponse response = new AnomalyDetailResponse(
            1L, "Anomaly_EQP-01_TEMP", Severity.WARNING,
            "PHOTO", "EQP-01", "TEMP", "NELSON_RULE_3", "HIGH",
            10, null, 10.0, 50.0, 15.0, 12.0, 3.0, 25.0,
            "Nelson Rule 3 Violation",
            Instant.parse("2026-06-10T12:00:00Z"), Instant.parse("2026-06-10T12:01:00Z"),
            "COMPLETED", "분석 요약"
        );

        when(anomalyService.getAnomaly(1L)).thenReturn(response);

        mockMvc.perform(get("/api/anomalies/{id}", 1L))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(1L))
            .andExpect(jsonPath("$.data.severity").value("WARNING"))
            .andExpect(jsonPath("$.data.equipmentName").value("EQP-01"))
            .andExpect(jsonPath("$.data.sampleCount").value(10));

        verify(anomalyService).getAnomaly(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이상 단건 조회 시 404를 반환한다")
    void getAnomalyDetail_notFound() throws Exception {
        when(anomalyService.getAnomaly(999L))
            .thenThrow(new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        mockMvc.perform(get("/api/anomalies/{id}", 999L))
            .andExpect(status().isNotFound());

        verify(anomalyService).getAnomaly(999L);
    }

    @Test
    @DisplayName("분석을 트리거한다")
    void triggerAnalysis() throws Exception {
        mockMvc.perform(get("/api/anomalies/{id}/analysis", 1L))
            .andExpect(status().isNoContent());

        verify(anomalyService).triggerAnalysis(1L);
    }

    @Test
    @DisplayName("존재하지 않는 이상 분석 트리거 시 404를 반환한다")
    void triggerAnalysis_notFound() throws Exception {
        doThrow(new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND))
            .when(anomalyService).triggerAnalysis(999L);

        mockMvc.perform(get("/api/anomalies/{id}/analysis", 999L))
            .andExpect(status().isNotFound());

        verify(anomalyService).triggerAnalysis(999L);
    }
}
