package com.factory.anomaly.infrastructure.kafka.handler;

import com.factory.anomaly.event.payload.consumer.AnalysisCompletedPayload;
import com.factory.anomaly.event.type.AnalysisEventType;
import com.factory.anomaly.service.AnalysisService;
import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AnalysisCompletedHandler implements EventHandler<AnalysisCompletedPayload> {

    private final AnalysisService analysisService;

    @Override
    public String getEventType() {
        return AnalysisEventType.ANALYSIS_COMPLETED.getName();
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<AnalysisCompletedPayload> event) {
        AnalysisCompletedPayload payload = event.getPayload();
        Long anomalyId = payload.getAnomalyId();
        String status = payload.getStatus();
        String summary = payload.getSummary();
        analysisService.updateAnalysis(anomalyId, status, summary);
    }
}
