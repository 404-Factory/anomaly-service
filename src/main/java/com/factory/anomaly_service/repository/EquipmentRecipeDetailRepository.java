package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.EquipmentRecipeDetailEntity;
import com.factory.anomaly_service.domain.entity.EquipmentRecipeDetailId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRecipeDetailRepository extends JpaRepository<EquipmentRecipeDetailEntity, EquipmentRecipeDetailId> {

    Optional<EquipmentRecipeDetailEntity> findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
            Long equipmentRecipeId,
            String recipeParameter
    );
}