package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.repository.support.AnomalyRepositorySupport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long>, AnomalyRepositorySupport {
    Optional<Anomaly> findFirstByEquipmentIdAndRecipeParameterAndStatusOrderByIdDesc(
        Long equipmentId, 
        String recipeParameter, 
        String status
    );
}
