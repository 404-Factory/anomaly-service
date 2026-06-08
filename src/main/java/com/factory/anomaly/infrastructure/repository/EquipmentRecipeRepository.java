package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.EquipmentRecipe;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRecipeRepository extends JpaRepository<EquipmentRecipe, Long> {
    Optional<EquipmentRecipe> findTopByEquipment_EquipmentIdOrderByVersionDesc(Long equipmentId);
}
