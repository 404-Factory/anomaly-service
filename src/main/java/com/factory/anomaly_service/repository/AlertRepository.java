package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRepository extends JpaRepository<AlertEntity, Long> {
}