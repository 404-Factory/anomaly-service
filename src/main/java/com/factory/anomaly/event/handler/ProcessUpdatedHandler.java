package com.factory.anomaly.event.handler;

import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import com.factory.anomaly.event.payload.ProcessPayload;
import com.factory.anomaly.infrastructure.entity.Process;
import com.factory.anomaly.infrastructure.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessUpdatedHandler implements EventHandler<ProcessPayload> {

    private final ProcessRepository processRepository;

    @Override
    public String getEventType() {
        return "ProcessUpdated";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<ProcessPayload> event) {
        ProcessPayload payload = event.getPayload();
        log.info("[CQRS] Processing ProcessUpdated event. id={}, name={}", payload.getProcessId(), payload.getName());
        Process process = Process.builder()
                .processId(payload.getProcessId())
                .processName(payload.getName())
                .build();
        processRepository.save(process);
    }
}
