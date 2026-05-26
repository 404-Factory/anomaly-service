package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyLogRepository extends JpaRepository<AnomalyLogEntity, Long> {
}