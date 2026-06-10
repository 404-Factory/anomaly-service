package com.factory.anomaly.service;

import com.factory.anomaly.engine.RuleEngine;
import com.factory.anomaly.engine.RuleResult;
import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.redis.SensorSample;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
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
    private RuleEngine ruleEngine;
    @Mock
    private AnomalyRepository anomalyRepository;
    @Mock
    private EquipmentProjectionRepository equipmentProjectionRepository;
    @Mock
    private com.factory.common.kafka.publisher.EventPublisher eventPublisher;
    @Mock
    private com.factory.common.event.support.DomainEventFactory domainEventFactory;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        anomalyDetectionService = new AnomalyDetectionServiceImpl(
                sensorRedisRepository,
                ruleEngine,
                anomalyRepository,
                equipmentProjectionRepository,
                eventPublisher,
                domainEventFactory,
                objectMapper
        );
        ReflectionTestUtils.setField(anomalyDetectionService, "eventPublishEnabled", false);
    }

    @Test
    void testDetectWithNelsonRule3() throws Exception {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        LocalDateTime detectedAt = LocalDateTime.of(2026, 6, 9, 20, 0, 0);
        Instant detectedInstant = detectedAt.toInstant(ZoneOffset.UTC);

        // EquipmentProjection mock
        EquipmentProjection equipment = mock(EquipmentProjection.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipment.getName()).thenReturn("EQP-01");

        when(equipmentProjectionRepository.findById(1L)).thenReturn(Optional.of(equipment));

        // Mock redis samples with min/max stored inside
        List<SensorSample> fiveMinSamples = List.of(
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 58, 0, 0, ZoneOffset.UTC), 12.0, 10.0, 50.0),
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 59, 0, 0, ZoneOffset.UTC), 15.0, 10.0, 50.0)
        );
        List<SensorSample> oneMinSamples = List.of(
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 59, 0, 0, ZoneOffset.UTC), 15.0, 10.0, 50.0)
        );

        when(sensorRedisRepository.findSamples(eq(equipmentCode), eq(sensorType), eq(detectedAt), eq(5), eq(0)))
                .thenReturn(fiveMinSamples);
        when(sensorRedisRepository.findSamples(eq(equipmentCode), eq(sensorType), eq(detectedAt), eq(1), eq(0)))
                .thenReturn(oneMinSamples);

        // Rule evaluates to true for NELSON_RULE_3
        RuleResult ruleResult = new RuleResult(
                true,
                RuleName.NELSON_RULE_3,
                Severity.WARNING,
                AnomalyType.HIGH,
                15.0,
                12.0,
                3.0,
                25.0,
                "Nelson Rule 3 Violation"
        );
        when(ruleEngine.evaluate(any(), any(), anyDouble(), anyDouble())).thenReturn(ruleResult);

        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<Anomaly> result = anomalyDetectionService.detect(equipmentCode, sensorType, detectedAt);

        // Then
        assertThat(result).isPresent();
        Anomaly log = result.get();
        assertThat(log.getRuleName()).isEqualTo(RuleName.NELSON_RULE_3);
        assertThat(log.getFirstDetectedAt()).isEqualTo(detectedInstant.minusSeconds(60L)); // 1 minute window for Rule 3
        assertThat(log.getSampleCount()).isEqualTo(1); // oneMinuteSamples size
        assertThat(log.getMin()).isEqualTo(10.0);
        assertThat(log.getMax()).isEqualTo(50.0);
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

        // Redis cache is empty
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3"))
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
        verify(sensorRedisRepository).setAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", 10L, 300);
        // Verify save was called once
        verify(anomalyRepository, times(1)).save(any(Anomaly.class));
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

        // Redis cache has key pointing to anomaly ID 10L
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3"))
                .thenReturn(10L);

        // Mock existing anomaly in DB
        Anomaly existingAnomaly = Anomaly.builder()
                .id(10L)
                .name("Anomaly_EQP-01_TEMP")
                .equipmentId(1L)
                .recipeParameter(sensorType)
                .severity(Severity.WARNING)
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

        // Verify save was called with the updated anomaly
        verify(anomalyRepository, times(1)).save(existingAnomaly);
        // Verify no new anomaly was created via builder
        verify(equipmentProjectionRepository, never()).findById(anyLong());
        verify(sensorRedisRepository, never()).setAnomalyCache(anyString(), anyString(), anyString(), anyLong(), anyLong());
    }
}
