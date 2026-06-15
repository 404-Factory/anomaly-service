package com.factory.anomaly.infrastructure.kafka.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.factory.anomaly.event.payload.consumer.SensorViolationPayload;
import com.factory.anomaly.event.type.FlinkEventType;
import com.factory.anomaly.service.AnomalyCreationService;
import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SensorViolationHandler implements EventHandler<SensorViolationPayload> {
    private final AnomalyCreationService anomalyCreationService;

    @Override
    public String getEventType() {
        return FlinkEventType.SENSOR_VIOLATION.getName();
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<SensorViolationPayload> event) {
        SensorViolationPayload payload = event.getPayload();    
        anomalyCreationService.create(payload);
    }
}
