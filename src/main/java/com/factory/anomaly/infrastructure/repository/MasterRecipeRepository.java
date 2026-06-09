package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.MasterRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterRecipeRepository extends JpaRepository<MasterRecipe, Long> {
}
