package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Violation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ViolationRepository extends JpaRepository<Violation, Long> {
}
