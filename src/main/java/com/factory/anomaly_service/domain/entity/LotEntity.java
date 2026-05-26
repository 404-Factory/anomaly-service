package com.factory.anomaly_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "LOT_INFO")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class LotEntity {

    @Id
    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_id")
    private ProcessEntity process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id")
    private EquipmentEntity equipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_recipe_id")
    private MasterRecipeEntity masterRecipe;

    @Column(name = "lot_grade", length = 50)
    private String lotGrade;

    @Column(name = "product_qty")
    private Integer productQty;

    @Column(name = "production_type", length = 50)
    private String productionType;
}