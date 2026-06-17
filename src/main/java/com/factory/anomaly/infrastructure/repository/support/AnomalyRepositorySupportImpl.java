package com.factory.anomaly.infrastructure.repository.support;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import static com.factory.anomaly.infrastructure.entity.QAnomaly.anomaly;
import static com.factory.anomaly.infrastructure.entity.QEquipmentProjection.equipmentProjection;
import static com.factory.anomaly.infrastructure.entity.QAnalysis.analysis;
import static com.factory.anomaly.infrastructure.entity.QViolation.violation;
import static com.querydsl.core.group.GroupBy.*;

@Repository
@RequiredArgsConstructor
public class AnomalyRepositorySupportImpl implements AnomalyRepositorySupport {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AnomalyResponse> fetchAnomaliesWithCondition(
        Long processId,
        Long equipmentId,
        String keyword,
        Pageable pageable
    ) {
        List<AnomalyResponse> content = queryFactory
            .select(Projections.constructor(AnomalyResponse.class,
                anomaly.id,
                anomaly.name,
                anomaly.logType.stringValue(),
                anomaly.severity,
                equipmentProjection.processName,
                equipmentProjection.name,
                anomaly.recipeParameter,
                anomaly.ruleName.stringValue(),
                anomaly.anomalyType.stringValue(),
                anomaly.lastDetectedAt,
                anomaly.detectionReason,
                JPAExpressions.select(violation.count())
                    .from(violation)
                    .where(violation.anomaly.id.eq(anomaly.id))
            ))
            .from(anomaly)
            .leftJoin(equipmentProjection)
            .on(equipmentProjection.id.eq(anomaly.equipmentId))
            .where(
                processIdEq(processId),
                equipmentIdEq(equipmentId),
                keywordLike(keyword)
            )
            .orderBy(anomaly.firstDetectedAt.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();

        JPAQuery<Long> countQuery = queryFactory
            .select(anomaly.count())
            .from(anomaly)
            .leftJoin(equipmentProjection)
            .on(equipmentProjection.id.eq(anomaly.equipmentId))
            .where(
                processIdEq(processId),
                equipmentIdEq(equipmentId),
                keywordLike(keyword)
            );

        return PageableExecutionUtils.getPage(
            content,
            pageable,
            countQuery::fetchOne
        );
    }

    @Override
    public AnomalyDetailResponse fetchAnomaly(Long id) {
        return queryFactory
            .from(anomaly)
            .leftJoin(equipmentProjection).on(anomaly.equipmentId.eq(equipmentProjection.id))
            .leftJoin(analysis).on(anomaly.id.eq(analysis.anomalyId))
            .leftJoin(violation).on(anomaly.id.eq(violation.anomaly.id))
            .where(anomaly.id.eq(id))
            .transform(
                groupBy(anomaly.id).as(Projections.constructor(
                    AnomalyDetailResponse.class,
                    anomaly.id,
                    anomaly.name,
                    anomaly.severity,
                    equipmentProjection.processName,
                    equipmentProjection.name,
                    anomaly.recipeParameter,
                    anomaly.ruleName.stringValue(),
                    anomaly.anomalyType.stringValue(),
                    anomaly.sampleCount,
                    anomaly.min,
                    anomaly.max,
                    anomaly.measuredValue,
                    anomaly.referenceValue,
                    anomaly.deviation,
                    anomaly.deviationRate,
                    anomaly.detectionReason,
                    anomaly.firstDetectedAt,
                    anomaly.lastDetectedAt,
                    analysis.status.stringValue(),
                    analysis.summary,
                    list(violation)
                ))
            ).get(id);
    }

    private BooleanExpression processIdEq(Long processId) {
        return processId != null ? equipmentProjection.processId.eq(processId) : null;
    }

    private BooleanExpression equipmentIdEq(Long equipmentId) {
        return equipmentId != null ? anomaly.equipmentId.eq(equipmentId) : null;
    }

    private BooleanExpression keywordLike(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }

        return anomaly.recipeParameter.containsIgnoreCase(keyword)
            .or(anomaly.detectionReason.containsIgnoreCase(keyword))
            .or(equipmentProjection.name.containsIgnoreCase(keyword));
    }
}
