package com.factory.anomaly.service;

import com.factory.anomaly.domain.dto.response.AnomalyDetailResponse;
import com.factory.anomaly.domain.dto.response.AnomalyResponse;
import com.factory.anomaly.domain.enums.AnalysisStatus;
import com.factory.anomaly.event.producer.payload.AnalysisRequestedPayload;
import com.factory.anomaly.event.type.AnalysisEventType;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.exception.AnomalyErrorCode;
import com.factory.anomaly.exception.AnomalyException;
import com.factory.anomaly.infrastructure.entity.Analysis;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.repository.AnalysisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.common.event.domain.DomainEvent;
import com.factory.common.event.support.DomainEventFactory;
import com.factory.common.kafka.publisher.EventPublisher;
import com.factory.common.outbox.jpa.autoconfigure.OutboxJpaAutoConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnomalyServiceImpl implements AnomalyService {

    private final AnomalyRepository anomalyRepository;
    private final AnalysisRepository analysisRepository;
    private final EventPublisher eventPublisher;
    private final DomainEventFactory domainEventFactory;

    @Override
    public Page<AnomalyResponse> getAnomalies(Long processId, Long equipmentId, String keyword,
        Pageable pageable) {
        return anomalyRepository.fetchAnomaliesWithCondition(processId, equipmentId, keyword,
            pageable);
    }


    @Override
    @Transactional
    public AnomalyDetailResponse getAnomaly(Long anomalyId) {
        Anomaly anomaly = anomalyRepository.findById(anomalyId)
            .orElseThrow(() -> new AnomalyException(AnomalyErrorCode.ANOMALY_LOG_NOT_FOUND));

        Analysis analysis = analysisRepository.findByAnomalyId(anomalyId)
            .orElse(null);

        if (analysis == null) {

            analysisRepository.save(Analysis.builder()
                .anomalyId(anomalyId)
                .status(AnalysisStatus.RUNNING)
                .summary(null)
                .build());

            AnalysisRequestedPayload payload = new AnalysisRequestedPayload(anomaly.getId(),
                anomaly.getEquipmentId(), anomaly.getRecipeParameter());

            eventPublisher.publish(
                domainEventFactory.create(AnalysisEventType.ANALYSIS_REQUESTED, "Analysis",
                    String.valueOf(anomaly.getId()), payload));
        }
        return anomalyRepository.fetchAnomaly(anomalyId);
    }
}

