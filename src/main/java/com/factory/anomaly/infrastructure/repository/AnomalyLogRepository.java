package com.factory.anomaly.infrastructure.repository;

import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AnomalyLogRepository extends JpaRepository<AnomalyLog, Long> {

    @Query("""
            SELECT a
            FROM AnomalyLog a
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
    List<AnomalyLog> findAnomalyLogs(
            @Param("processId") Long processId,
            @Param("equipmentId") Long equipmentId,
            @Param("keyword") String keyword
    );
}
