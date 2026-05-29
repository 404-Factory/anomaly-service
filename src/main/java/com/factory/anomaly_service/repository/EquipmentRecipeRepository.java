package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.EquipmentRecipeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRecipeRepository extends JpaRepository<EquipmentRecipeEntity, Long> {

    Optional<EquipmentRecipeEntity> findTopByEquipment_EquipmentIdOrderByVersionDesc(Long equipmentId);
}