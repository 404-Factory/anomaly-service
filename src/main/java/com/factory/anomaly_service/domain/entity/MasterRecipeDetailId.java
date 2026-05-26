package com.factory.anomaly_service.domain.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MasterRecipeDetailId implements Serializable {

    private Long masterRecipe;
    private String recipeParameter;
}