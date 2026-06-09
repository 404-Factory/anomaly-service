package com.factory.anomaly.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "equipment_recipe_details")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Getter
public class EquipmentRecipeDetail {

    @EmbeddedId
    private EquipmentRecipeDetailId id;

    @MapsId("equipmentRecipeId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_recipe_id")
    private EquipmentRecipe equipmentRecipe;

    @Column(name = "min")
    private Double min;

    @Column(name = "max")
    private Double max;

    public void update(Double min, Double max) {
        this.min = min;
        this.max = max;
    }
}
