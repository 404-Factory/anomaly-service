package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.EquipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {

    Optional<EquipmentEntity> findByEquipmentName(String equipmentName);
}