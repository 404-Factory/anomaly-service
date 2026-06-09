package com.factory.anomaly.config;

import com.factory.common.event.support.EventEnvelopeFactory;
import com.factory.common.event.support.IdempotencyKeyGenerator;
import com.factory.common.event.support.MdcTraceIdProvider;
import com.factory.common.event.support.TraceIdProvider;
import com.factory.common.event.support.UUIDIdempotencyKeyGenerator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventConfig {

    @Bean
    public IdempotencyKeyGenerator idempotencyKeyGenerator() {
        return new UUIDIdempotencyKeyGenerator();
    }

    @Bean
    public TraceIdProvider traceIdProvider() {
        return new MdcTraceIdProvider();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public EventEnvelopeFactory eventEnvelopeFactory(
            IdempotencyKeyGenerator idempotencyKeyGenerator,
            TraceIdProvider traceIdProvider,
            Clock clock) {
        return new EventEnvelopeFactory(idempotencyKeyGenerator, traceIdProvider, clock);
    }
}
