package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRecipeDetailRepository extends JpaRepository<EquipmentRecipeDetail, EquipmentRecipeDetailId> {
    List<EquipmentRecipeDetail> findByEquipmentRecipe_Id(Long equipmentRecipeId);
}
