package com.bank.report.infrastructure.adapter.out.rest;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.time.Duration;

/** Los mismos umbrales y el mismo timeout de 2 s que bank-config (uno menor falla con la primera llamada en frío). */
final class ResilienceTestSupport {

    private ResilienceTestSupport() {
    }

    static CircuitBreakerRegistry circuitBreakers() {
        return CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(4)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(5))
                .build());
    }

    static TimeLimiterRegistry timeLimiters() {
        return TimeLimiterRegistry.of(TimeLimiterConfig.custom()
                .timeoutDuration(Duration.ofSeconds(2))
                .cancelRunningFuture(true)
                .build());
    }
}
