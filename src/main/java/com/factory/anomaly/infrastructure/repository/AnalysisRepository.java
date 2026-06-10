package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Analysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByAnomalyId(Long anomalyId);
}
