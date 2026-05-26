package com.factory.anomaly_service.domain.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EquipmentRecipeDetailId implements Serializable {

    private Long equipmentRecipe;
    private String recipeParameter;
}