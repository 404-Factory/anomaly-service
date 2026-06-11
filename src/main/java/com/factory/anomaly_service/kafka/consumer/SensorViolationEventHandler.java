package com.factory.anomaly_service.kafka.consumer;

import com.factory.anomaly_service.kafka.dto.SensorViolationPayload;
import com.factory.anomaly_service.kafka.service.AnomalyLogCreateService;
import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SensorViolationEventHandler implements EventHandler<SensorViolationPayload> {

    private final AnomalyLogCreateService anomalyLogCreateService;

    @Override
    public String getEventType() {
        return "SensorViolation";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<SensorViolationPayload> event) {
        anomalyLogCreateService.create(event.getPayload());
    }
}
