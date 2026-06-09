package com.factory.anomaly.service;

import com.factory.anomaly.dto.SensorSnapshotDto;
import com.factory.anomaly.engine.RuleEngine;
import com.factory.anomaly.engine.RuleResult;
import com.factory.anomaly.infrastructure.entity.AnomalyLog;
import com.factory.anomaly.infrastructure.entity.Equipment;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipe;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetail;
import com.factory.anomaly.infrastructure.entity.EquipmentRecipeDetailId;
import com.factory.anomaly.infrastructure.enums.AnomalyType;
import com.factory.anomaly.infrastructure.enums.LogType;
import com.factory.anomaly.infrastructure.enums.RuleName;
import com.factory.anomaly.infrastructure.enums.Severity;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.redis.SensorSample;
import com.factory.anomaly.infrastructure.repository.AnomalyLogRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeDetailRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRecipeRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentRepository;
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
    private AnomalyLogRepository anomalyLogRepository;
    @Mock
    private EquipmentRepository equipmentRepository;
    @Mock
    private EquipmentRecipeRepository equipmentRecipeRepository;
    @Mock
    private EquipmentRecipeDetailRepository equipmentRecipeDetailRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        anomalyDetectionService = new AnomalyDetectionServiceImpl(
                sensorRedisRepository,
                ruleEngine,
                anomalyLogRepository,
                equipmentRepository,
                equipmentRecipeRepository,
                equipmentRecipeDetailRepository,
                mock(com.factory.common.kafka.publisher.EventPublisher.class),
                mock(com.factory.common.event.support.EventEnvelopeFactory.class),
                objectMapper
        );
        ReflectionTestUtils.setField(anomalyDetectionService, "eventPublishEnabled", false);
    }

    @Test
    void testDetectWithNelsonRule3AndSnapshot() throws Exception {
        // Given
        String equipmentCode = "EQP-01";
        String sensorType = "TEMP";
        LocalDateTime detectedAt = LocalDateTime.of(2026, 6, 9, 20, 0, 0);
        Instant detectedInstant = detectedAt.toInstant(ZoneOffset.UTC);

        Equipment equipment = Equipment.builder().id(1L).name(equipmentCode).build();
        EquipmentRecipe recipe = EquipmentRecipe.builder().id(2L).equipment(equipment).version(1.0).build();
        EquipmentRecipeDetail detail = EquipmentRecipeDetail.builder()
                .id(new EquipmentRecipeDetailId(2L, sensorType))
                .equipmentRecipe(recipe)
                .min(10.0)
                .max(50.0)
                .build();

        when(equipmentRepository.findByName(equipmentCode)).thenReturn(Optional.of(equipment));
        when(equipmentRecipeRepository.findTopByEquipment_IdOrderByVersionDesc(1L)).thenReturn(Optional.of(recipe));
        when(equipmentRecipeDetailRepository.findById(any())).thenReturn(Optional.of(detail));
        when(equipmentRecipeDetailRepository.findByEquipmentRecipe_Id(2L)).thenReturn(List.of(detail));

        // Mock redis samples
        List<SensorSample> fiveMinSamples = List.of(
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 58, 0, 0, ZoneOffset.UTC), 12.0),
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 59, 0, 0, ZoneOffset.UTC), 15.0)
        );
        List<SensorSample> oneMinSamples = List.of(
                new SensorSample(OffsetDateTime.of(2026, 6, 9, 19, 59, 0, 0, ZoneOffset.UTC), 15.0)
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

        // Redis keys for snapshot
        String redisKey = "sensor:EQP-01:temp_sensor_1:TEMP";
        when(sensorRedisRepository.findKeys(equipmentCode, sensorType)).thenReturn(List.of(redisKey));
        when(sensorRedisRepository.findSamplesByKey(redisKey, detectedAt, 5, 0)).thenReturn(fiveMinSamples);

        when(anomalyLogRepository.save(any(AnomalyLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        Optional<AnomalyLog> result = anomalyDetectionService.detect(equipmentCode, sensorType, detectedAt);

        // Then
        assertThat(result).isPresent();
        AnomalyLog log = result.get();
        assertThat(log.getRuleName()).isEqualTo(RuleName.NELSON_RULE_3);
        assertThat(log.getFirstDetectedAt()).isEqualTo(detectedInstant.minusSeconds(60L)); // 1 minute window for Rule 3
        assertThat(log.getSampleCount()).isEqualTo(1); // oneMinuteSamples size

        // Verify Snapshot JSON
        assertThat(log.getSnapshotData()).isNotNull();
        List<SensorSnapshotDto> snapshotList = objectMapper.readValue(
                log.getSnapshotData(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SensorSnapshotDto.class)
        );
        assertThat(snapshotList).hasSize(1);
        SensorSnapshotDto snapshotDto = snapshotList.get(0);
        assertThat(snapshotDto.sensorId()).isEqualTo("temp_sensor_1");
        assertThat(snapshotDto.sensorType()).isEqualTo("TEMP");
        assertThat(snapshotDto.values()).hasSize(2);
    }
}
