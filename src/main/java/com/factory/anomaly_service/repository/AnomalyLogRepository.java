package com.factory.anomaly_service.repository;

import com.factory.anomaly_service.domain.entity.AnomalyLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AnomalyLogRepository extends JpaRepository<AnomalyLogEntity, Long> {

    @Query("""
            SELECT a
            FROM AnomalyLogEntity a
            LEFT JOIN FETCH a.equipment e
            LEFT JOIN FETCH e.process p
            WHERE (:processId IS NULL OR p.processId = :processId)
              AND (:equipmentId IS NULL OR e.equipmentId = :equipmentId)
              AND (
                    :keyword IS NULL
                    OR LOWER(a.recipeParameter) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(a.detectionReason) LIKE LOWER(CONCAT('%', :keyword, '%'))
                    OR LOWER(e.equipmentName) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY a.occurredTime DESC
            """)
    List<AnomalyLogEntity> findAnomalyLogs(
            @Param("processId") Long processId,
            @Param("equipmentId") Long equipmentId,
            @Param("keyword") String keyword
    );
}