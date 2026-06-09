package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRecipeDetailRepository extends JpaRepository<EquipmentRecipeDetail, EquipmentRecipeDetailId> {
}
