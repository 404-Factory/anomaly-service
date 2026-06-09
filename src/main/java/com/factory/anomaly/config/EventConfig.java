package com.factory.anomaly.config;

import com.factory.common.event.support.EventEnvelopeFactory;
import com.factory.common.event.support.IdempotencyKeyGenerator;
import com.factory.common.event.support.TraceIdProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class EventConfig {

    @Bean
    @ConditionalOnMissingBean(EventEnvelopeFactory.class)
    public EventEnvelopeFactory eventEnvelopeFactory(
            IdempotencyKeyGenerator idempotencyKeyGenerator,
            TraceIdProvider traceIdProvider,
            Clock clock) {
        return new EventEnvelopeFactory(idempotencyKeyGenerator, traceIdProvider, clock);
    }
}
