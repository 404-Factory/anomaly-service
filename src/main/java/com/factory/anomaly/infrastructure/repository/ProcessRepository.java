package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessRepository extends JpaRepository<Process, Long> {
}
