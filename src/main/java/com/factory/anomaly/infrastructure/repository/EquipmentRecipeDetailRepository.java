package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRecipeDetailRepository extends JpaRepository<EquipmentRecipeDetail, Long> {
    Optional<EquipmentRecipeDetail> findByEquipmentRecipe_EquipmentRecipeIdAndRecipeParameter(
            Long equipmentRecipeId, String recipeParameter);
}
