package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.DefectInfo;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DefectRepository extends JpaRepository<DefectInfo, Long> {

    @Query("""
        SELECT d FROM DefectInfo d 
        WHERE d.causeEquipmentId = :equipmentId 
          AND (d.occurredTime = :anomalyTime 
               OR d.detectedTime BETWEEN :anomalyTime AND :detectedEndTime)
    """)
    List<DefectInfo> findCorrelatedDefects(
        @Param("equipmentId") Long equipmentId,
        @Param("anomalyTime") Instant anomalyTime,
        @Param("detectedEndTime") Instant detectedEndTime
    );
}
