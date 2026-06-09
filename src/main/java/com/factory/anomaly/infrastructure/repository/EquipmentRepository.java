package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Equipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {
    Optional<Equipment> findByName(String name);
}
