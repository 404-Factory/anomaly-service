package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "MASTER_RECIPE_DETAIL")
@IdClass(MasterRecipeDetailId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MasterRecipeDetailEntity {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_recipe_id", nullable = false)
    private MasterRecipeEntity masterRecipe;

    @Id
    @Column(name = "param", length = 50, nullable = false)
    private String recipeParameter;

    @Column(name = "min")
    private Double minValue;

    @Column(name = "max")
    private Double maxValue;
}