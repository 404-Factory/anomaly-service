package com.factory.anomaly.event.handler;

import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import com.factory.anomaly.event.payload.EquipmentRecipePayload;
import com.factory.anomaly.infrastructure.entity.Equipment;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipe;
import com.factory.anomaly.infrastructure.entity.MasterRecipe;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRepository;
import com.factory.anomaly.infrastructure.repository.MasterRecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentRecipeUpdatedHandler implements EventHandler<EquipmentRecipePayload> {

    private final EquipmentRecipeRepository equipmentRecipeRepository;
    private final EquipmentRepository equipmentRepository;
    private final MasterRecipeRepository masterRecipeRepository;

    @Override
    public String getEventType() {
        return "EquipmentRecipeUpdated";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<EquipmentRecipePayload> event) {
        EquipmentRecipePayload payload = event.getPayload();
        log.info("[CQRS] Processing EquipmentRecipeUpdated event. id={}, equipmentId={}, masterRecipeId={}, version={}",
                payload.getEquipmentRecipeId(), payload.getEquipmentId(), payload.getMasterRecipeId(), payload.getVersion());

        Equipment equipment = null;
        if (payload.getEquipmentId() != null) {
            equipment = equipmentRepository.findById(payload.getEquipmentId()).orElse(null);
        }

        MasterRecipe masterRecipe = null;
        if (payload.getMasterRecipeId() != null) {
            masterRecipe = masterRecipeRepository.findById(payload.getMasterRecipeId())
                    .orElseGet(() -> {
                        MasterRecipe newMaster = MasterRecipe.builder()
                                .masterRecipeId(payload.getMasterRecipeId())
                                .build();
                        return masterRecipeRepository.save(newMaster);
                    });
        }

        EquipmentRecipe equipmentRecipe = EquipmentRecipe.builder()
                .equipmentRecipeId(payload.getEquipmentRecipeId())
                .equipment(equipment)
                .masterRecipe(masterRecipe)
                .version(payload.getVersion())
                .build();
        equipmentRecipeRepository.save(equipmentRecipe);
    }
}
