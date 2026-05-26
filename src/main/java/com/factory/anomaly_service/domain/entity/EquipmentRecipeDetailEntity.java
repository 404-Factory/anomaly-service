package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "EQUIPMENT_RECIPE_DETAIL")
@IdClass(EquipmentRecipeDetailId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class EquipmentRecipeDetailEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_rec_id", nullable = false)
    private EquipmentRecipeEntity equipmentRecipe;

    @Id
    @Column(name = "param", length = 50, nullable = false)
    private String recipeParameter;

    @Column(name = "min")
    private Double minValue;

    @Column(name = "max")
    private Double maxValue;
}