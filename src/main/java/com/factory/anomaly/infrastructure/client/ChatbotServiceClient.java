package com.factory.anomaly.infrastructure.client;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ChatbotServiceClient {

    private final RestTemplate restTemplate;

    @Value("${app.chatbot-service.url:http://localhost:8085}")
    private String chatbotServiceUrl;

    public ChatbotServiceClient() {
        this.restTemplate = new RestTemplate();
    }

    public String getAnomalyAnalysis(AnomalyAnalysisRequest request) {
        String url = chatbotServiceUrl + "/api/internal/anomaly-analysis";
        try {
            System.out.println("[DEBUG] Calling chatbot-service at URL: " + url);
            AnomalyAnalysisResponse response = restTemplate.postForObject(url, request, AnomalyAnalysisResponse.class);
            if (response != null) {
                return response.getAnalysisResult();
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to get AI analysis from chatbot-service: " + e.getMessage());
            e.printStackTrace();
        }
        return "AI 분석 호출 실패: chatbot-service와의 통신 중 오류가 발생했습니다.";
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AnomalyAnalysisRequest {
        private String equipmentName;
        private String recipeParameter;
        private String ruleName;
        private String anomalyType;
        private String detectionReason;
        private Instant occurredTime;
        private List<DefectDto> defects;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DefectDto {
        private Long lotId;
        private String defectType;
        private String defectCode;
        private Instant occurredTime;
        private Instant detectedTime;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAnalysisResponse {
        private String analysisResult;
    }
}
