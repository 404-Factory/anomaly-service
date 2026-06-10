package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

    private AnomalyDetectionServiceImpl anomalyDetectionService;

    @Mock
    private SensorRedisRepository sensorRedisRepository;
    @Mock
    private AnomalyRepository anomalyRepository;
    @Mock
    private EquipmentProjectionRepository equipmentProjectionRepository;
    @Mock
    private com.factory.common.kafka.publisher.EventPublisher eventPublisher;
    @Mock
    private com.factory.common.event.support.DomainEventFactory domainEventFactory;

    @BeforeEach
    void setUp() {
        anomalyDetectionService = new AnomalyDetectionServiceImpl(
                sensorRedisRepository,
                anomalyRepository,
                equipmentProjectionRepository,
                eventPublisher,
                domainEventFactory
        );
        ReflectionTestUtils.setField(anomalyDetectionService, "eventPublishEnabled", false);
    }

    @Test
    void testDetectNewViolation() {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
                equipmentCode,
                sensorType,
                RuleName.NELSON_RULE_3,
                AnomalyType.HIGH,
                Severity.WARNING,
                15.0,
                12.0,
                3.0,
                25.0,
                10.0,
                50.0,
                detectedAt,
                10,
                "Nelson Rule 3 Violation"
        );

        // Mock Lock acquisition
        when(sensorRedisRepository.acquireLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong()))
                .thenReturn(true);

        // Redis cache is empty
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH"))
                .thenReturn(null);

        // EquipmentProjection mock
        EquipmentProjection equipment = mock(EquipmentProjection.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipment.getName()).thenReturn("EQP-01");
        when(equipmentProjectionRepository.findById(1L)).thenReturn(Optional.of(equipment));

        // Anomaly mock save
        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> {
            Anomaly a = invocation.getArgument(0);
            return Anomaly.builder()
                    .id(10L)
                    .name(a.getName())
                    .equipmentId(a.getEquipmentId())
                    .recipeParameter(a.getRecipeParameter())
                    .severity(a.getSeverity())
                    .lastDetectedAt(a.getLastDetectedAt())
                    .ruleName(a.getRuleName())
                    .anomalyType(a.getAnomalyType())
                    .logType(a.getLogType())
                    .firstDetectedAt(a.getFirstDetectedAt())
                    .sampleCount(a.getSampleCount())
                    .detectionReason(a.getDetectionReason())
                    .measuredValue(a.getMeasuredValue())
                    .referenceValue(a.getReferenceValue())
                    .deviation(a.getDeviation())
                    .deviationRate(a.getDeviationRate())
                    .min(a.getMin())
                    .max(a.getMax())
                    .build();
        });

        // When
        Optional<Anomaly> result = anomalyDetectionService.detect(violation);

        // Then
        assertThat(result).isPresent();
        Anomaly log = result.get();
        assertThat(log.getId()).isEqualTo(10L);
        assertThat(log.getRuleName()).isEqualTo(RuleName.NELSON_RULE_3);
        assertThat(log.getLastDetectedAt()).isEqualTo(detectedAt);
        assertThat(log.getSampleCount()).isEqualTo(10);

        // Verify set cache was called
        verify(sensorRedisRepository).setAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH", 10L, 300);
        // Verify save was called once
        verify(anomalyRepository, times(1)).save(any(Anomaly.class));
        // Verify release lock was called
        verify(sensorRedisRepository).releaseLock(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH");
    }

    @Test
    void testDetectDuplicateViolation() {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
                equipmentCode,
                sensorType,
                RuleName.NELSON_RULE_3,
                AnomalyType.HIGH,
                Severity.WARNING,
                15.0,
                12.0,
                3.0,
                25.0,
                10.0,
                50.0,
                detectedAt,
                10,
                "Nelson Rule 3 Violation"
        );

        // Mock Lock acquisition
        when(sensorRedisRepository.acquireLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong()))
                .thenReturn(true);

        // Redis cache has key pointing to anomaly ID 10L
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH"))
                .thenReturn(10L);

        // Mock existing anomaly in DB
        Anomaly existingAnomaly = Anomaly.builder()
                .id(10L)
                .name("Anomaly_EQP-01_TEMP")
                .equipmentId(1L)
                .recipeParameter(sensorType)
                .severity(Severity.CAUTION)
                .lastDetectedAt(Instant.parse("2026-06-10T12:00:00Z"))
                .ruleName(RuleName.NELSON_RULE_3)
                .anomalyType(AnomalyType.HIGH)
                .logType(com.factory.anomaly.domain.enums.LogType.SENSOR)
                .firstDetectedAt(Instant.parse("2026-06-10T12:00:00Z"))
                .sampleCount(5)
                .build();
        when(anomalyRepository.findById(10L)).thenReturn(Optional.of(existingAnomaly));

        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<Anomaly> result = anomalyDetectionService.detect(violation);

        // Then
        assertThat(result).isPresent();
        Anomaly log = result.get();
        assertThat(log.getId()).isEqualTo(10L);
        assertThat(log.getLastDetectedAt()).isEqualTo(detectedAt);
        assertThat(log.getSampleCount()).isEqualTo(10);
        assertThat(log.getSeverity()).isEqualTo(Severity.WARNING); // upgraded from CAUTION!

        // Verify save was called with the updated anomaly
        verify(anomalyRepository, times(1)).save(existingAnomaly);
        // Verify no new anomaly was created via builder
        verify(equipmentProjectionRepository, never()).findById(anyLong());
        verify(sensorRedisRepository, never()).setAnomalyCache(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong());
        // Verify release lock was called
        verify(sensorRedisRepository).releaseLock(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH");
    }
}
