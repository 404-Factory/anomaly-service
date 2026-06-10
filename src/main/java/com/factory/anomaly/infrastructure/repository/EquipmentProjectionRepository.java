package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface EquipmentProjectionRepository extends JpaRepository<EquipmentProjection, Long> {
    Optional<EquipmentProjection> findByName(String name);
}
