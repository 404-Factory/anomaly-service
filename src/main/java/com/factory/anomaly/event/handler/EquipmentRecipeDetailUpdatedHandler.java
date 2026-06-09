package com.factory.anomaly.event.handler;

import com.factory.common.event.domain.Event;
import com.factory.common.inbox.jpa.aop.InboxProcessed;
import com.factory.common.kafka.support.EventHandler;
import com.factory.anomaly.event.payload.EquipmentRecipeDetailPayload;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipe;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeDetailRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class EquipmentRecipeDetailUpdatedHandler implements EventHandler<EquipmentRecipeDetailPayload> {

    private final EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;
    private final EquipmentRecipeRepository equipmentRecipeRepository;

    @Override
    public String getEventType() {
        return "EquipmentRecipeDetailUpdated";
    }

    @Override
    @Transactional
    @InboxProcessed
    public void process(Event<EquipmentRecipeDetailPayload> event) {
        EquipmentRecipeDetailPayload payload = event.getPayload();
        log.info("[CQRS] Processing EquipmentRecipeDetailUpdated event. recipeId={}, param={}, min={}, max={}",
                payload.getEquipmentRecipeId(), payload.getRecipeParameter(), payload.getMinValue(), payload.getMaxValue());

        final EquipmentRecipe equipmentRecipe = (payload.getEquipmentRecipeId() != null)
                ? equipmentRecipeRepository.findById(payload.getEquipmentRecipeId()).orElse(null)
                : null;

        EquipmentRecipeDetailId detailId = new EquipmentRecipeDetailId(payload.getEquipmentRecipeId(), payload.getRecipeParameter());

        EquipmentRecipeDetail detail = equipmentRecipeDetailRepository.findById(detailId)
                .orElseGet(() -> EquipmentRecipeDetail.builder()
                        .id(detailId)
                        .equipmentRecipe(equipmentRecipe)
                        .build());

        detail.setMin(payload.getMinValue());
        detail.setMax(payload.getMaxValue());
        equipmentRecipeDetailRepository.save(detail);
    }
}
