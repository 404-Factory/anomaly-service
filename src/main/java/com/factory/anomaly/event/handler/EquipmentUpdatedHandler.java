package com.factory.anomaly.event.handler;

import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import com.factory.anomaly.event.payload.EquipmentPayload;
import com.factory.anomaly.infrastructure.entity.Equipment;
import com.factory.anomaly.infrastructure.entity.Process;
import com.factory.anomaly.infrastructure.repository.EquipmentRepository;
import com.factory.anomaly.infrastructure.repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentUpdatedHandler implements EventHandler<EquipmentPayload> {

    private final EquipmentRepository equipmentRepository;
    private final ProcessRepository processRepository;

    @Override
    public String getEventType() {
        return "EquipmentUpdated";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<EquipmentPayload> event) {
        EquipmentPayload payload = event.getPayload();
        log.info("[CQRS] Processing EquipmentUpdated event. id={}, name={}, processId={}", 
                payload.getEquipmentId(), payload.getName(), payload.getProcessId());

        Process process = null;
        if (payload.getProcessId() != null) {
            process = processRepository.findById(payload.getProcessId()).orElse(null);
        }

        Equipment equipment = Equipment.builder()
                .equipmentId(payload.getEquipmentId())
                .equipmentName(payload.getName())
                .process(process)
                .build();
        equipmentRepository.save(equipment);
    }
}
