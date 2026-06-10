package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.repository.support.AnomalyRepositorySupport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long>, AnomalyRepositorySupport {

}
