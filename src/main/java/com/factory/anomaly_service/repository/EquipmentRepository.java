package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.EquipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<EquipmentEntity, Long> {
}
