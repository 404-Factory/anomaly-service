package com.factory.anomaly.service;

import com.factory.anomaly.domain.enums.AnomalyType;
import com.factory.anomaly.domain.enums.RuleName;
import com.factory.anomaly.domain.enums.Severity;
import com.factory.anomaly.event.payload.SensorViolationDto;
import com.factory.anomaly.event.payload.producer.AnomalyCreatedPayload;
import com.factory.anomaly.event.type.AnomalyEventType;
import com.factory.anomaly.infrastructure.entity.Anomaly;
import com.factory.anomaly.infrastructure.entity.EquipmentProjection;
import com.factory.anomaly.infrastructure.redis.SensorRedisRepository;
import com.factory.anomaly.infrastructure.repository.AnomalyRepository;
import com.factory.anomaly.infrastructure.repository.EquipmentProjectionRepository;
import com.factory.anomaly.infrastructure.repository.ViolationRepository;
import com.factory.common.event.domain.DomainEvent;
import com.factory.common.event.domain.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
    private ViolationRepository violationRepository;
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
                violationRepository,
                eventPublisher,
                domainEventFactory
        );
        ReflectionTestUtils.setField(anomalyDetectionService, "self", anomalyDetectionService);
        ReflectionTestUtils.setField(anomalyDetectionService, "eventPublishEnabled", false);
    }

    @Test
    void testDetectNewViolation() {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
                1L,
                "1-TEMP",
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
                1L,
                "1-TEMP",
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

    @Test
    void testConcurrentDetect() throws InterruptedException {
        // Given
        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
                1L,
                "1-TEMP",
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

        // State variables to track lock status and cached anomaly ID
        java.util.concurrent.atomic.AtomicBoolean lockAcquired = new java.util.concurrent.atomic.AtomicBoolean(false);
        java.util.concurrent.atomic.AtomicReference<Long> cacheReference = new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.atomic.AtomicInteger dbInsertCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger dbUpdateCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Mock Lock acquisition: only succeed if lock is not currently held
        when(sensorRedisRepository.acquireLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong()))
                .thenAnswer(invocation -> lockAcquired.compareAndSet(false, true));

        // Mock Lock release
        doAnswer(invocation -> {
            lockAcquired.set(false);
            return null;
        }).when(sensorRedisRepository).releaseLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"));

        // Mock Cache Read
        when(sensorRedisRepository.getAnomalyCache(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH")))
                .thenAnswer(invocation -> cacheReference.get());

        // Mock Cache Write
        doAnswer(invocation -> {
            Long id = invocation.getArgument(4);
            cacheReference.set(id);
            return null;
        }).when(sensorRedisRepository).setAnomalyCache(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong(), anyLong());

        // EquipmentProjection mock
        EquipmentProjection equipment = mock(EquipmentProjection.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipment.getName()).thenReturn("EQP-01");
        when(equipmentProjectionRepository.findById(1L)).thenReturn(Optional.of(equipment));

        // Mock existing anomaly lookup
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

        // Mock DB Save: increment counter based on whether ID exists
        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> {
            Anomaly a = invocation.getArgument(0);
            if (a.getId() == null) {
                dbInsertCount.incrementAndGet();
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
                        .build();
            } else {
                dbUpdateCount.incrementAndGet();
                return a;
            }
        });

        // Run 4 threads concurrently trying to detect the same violation
        int numThreads = 4;
        java.util.concurrent.ExecutorService executorService = java.util.concurrent.Executors.newFixedThreadPool(numThreads);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(numThreads);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch doneLatch = new java.util.concurrent.CountDownLatch(numThreads);

        for (int i = 0; i < numThreads; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // wait for all threads to start at once
                    anomalyDetectionService.detect(violation);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown(); // start all threads concurrently
        doneLatch.await();
        executorService.shutdown();

        // Assertions:
        // 1. Only 1 thread should insert a new Anomaly (dbInsertCount should be 1)
        assertThat(dbInsertCount.get()).isEqualTo(1);
        // 2. The other 3 threads should find the cached anomaly and update it (dbUpdateCount should be 3)
        assertThat(dbUpdateCount.get()).isEqualTo(3);
        // 3. Cache reference should contain the anomaly ID (10L)
        assertThat(cacheReference.get()).isEqualTo(10L);
    }

    @Test
    void testDetectPublishesOutboxEvent() {
        // Given
        ReflectionTestUtils.setField(anomalyDetectionService, "eventPublishEnabled", true);

        String equipmentCode = "1";
        String sensorType = "TEMP";
        Instant detectedAt = Instant.parse("2026-06-10T12:00:59Z");

        SensorViolationDto violation = new SensorViolationDto(
                1L,
                "1-TEMP",
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

        when(sensorRedisRepository.acquireLock(eq(equipmentCode), eq(sensorType), eq("NELSON_RULE_3"), eq("HIGH"), anyLong()))
                .thenReturn(true);
        when(sensorRedisRepository.getAnomalyCache(equipmentCode, sensorType, "NELSON_RULE_3", "HIGH"))
                .thenReturn(null);

        EquipmentProjection equipment = mock(EquipmentProjection.class);
        when(equipment.getId()).thenReturn(1L);
        when(equipment.getName()).thenReturn("EQP-01");
        when(equipmentProjectionRepository.findById(1L)).thenReturn(Optional.of(equipment));

        when(anomalyRepository.save(any(Anomaly.class))).thenAnswer(invocation -> {
            Anomaly a = invocation.getArgument(0);
            return Anomaly.builder().id(10L).name(a.getName()).equipmentId(a.getEquipmentId())
                    .recipeParameter(a.getRecipeParameter()).severity(a.getSeverity())
                    .lastDetectedAt(a.getLastDetectedAt()).ruleName(a.getRuleName())
                    .anomalyType(a.getAnomalyType()).logType(a.getLogType())
                    .firstDetectedAt(a.getFirstDetectedAt()).sampleCount(a.getSampleCount())
                    .detectionReason(a.getDetectionReason()).build();
        });

        when(domainEventFactory.create(any(), eq("Anomaly"), anyString(), any()))
                .thenAnswer(invocation -> {
                    AnomalyCreatedPayload p = invocation.getArgument(3);
                    return DomainEvent.of("test-key", AnomalyEventType.ANOMALY_CREATED, "Anomaly", "10", p, "trace-id");
                });

        // When
        anomalyDetectionService.detect(violation);

        // Then
        ArgumentCaptor<Event<?>> eventCaptor = ArgumentCaptor.forClass(Event.class);
        verify(eventPublisher).publish(eventCaptor.capture());

        Event<?> capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getEventType()).isEqualTo("AnomalyCreated");

        AnomalyCreatedPayload payload = (AnomalyCreatedPayload) capturedEvent.getPayload();
        assertThat(payload.getEquipmentId()).isEqualTo(1L);
        assertThat(payload.getEquipmentName()).isEqualTo("EQP-01");
        assertThat(payload.getRecipeParameter()).isEqualTo("TEMP");
        assertThat(payload.getSeverity()).isEqualTo("WARNING");
        assertThat(payload.getCauseRule()).isEqualTo("NELSON_RULE_3");
        assertThat(payload.getOccurredTime()).isEqualTo(detectedAt);
    }
}
