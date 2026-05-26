package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "MASTER_RECIPE")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class MasterRecipeEntity {

    @Id
    @Column(name = "master_recipe_id", nullable = false)
    private Long masterRecipeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id")
    private ProcessEntity process;
}