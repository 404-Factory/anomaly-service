package com.factory.anomaly.event.payload;

import com.factory.common.event.domain.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentRecipeDetailPayload implements EventPayload {
    private Long equipmentRecipeId;
    private String recipeParameter;
    private Double minValue;
    private Double maxValue;
}
