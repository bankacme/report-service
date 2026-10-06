package com.bank.report.infrastructure.adapter.out.rest;

import com.bank.report.domain.exception.DownstreamServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

/**
 * Una fuente del reporte (account-, credit- o transaction-service) con su circuit breaker y su
 * timeout de 2 s (instancias de Resilience4j con el nombre del servicio). Toda falla que no sea un
 * 404 es {@link DownstreamServiceUnavailableException} → 503: no se entregan reportes incompletos
 * (regla 12).
 */
final class RestSource {

    private final String service;
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;

    RestSource(String service, WebClient.Builder builder, String baseUrl,
               CircuitBreakerRegistry circuitBreakerRegistry, TimeLimiterRegistry timeLimiterRegistry) {
        this.service = service;
        this.webClient = builder.clone().baseUrl(baseUrl).build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(service);
        this.timeLimiter = timeLimiterRegistry.timeLimiter(service);
    }

    /** Un recurso por id: vacío si responde 404. */
    <T> Mono<T> getOptional(Function<UriBuilder, URI> uri, Class<T> type) {
        return guard(webClient.get().uri(uri).exchangeToMono(response -> {
            if (response.statusCode().equals(HttpStatus.NOT_FOUND)) {
                return Mono.<T>empty();
            }
            if (response.statusCode().isError()) {
                return response.<T>createError();
            }
            return response.bodyToMono(type);
        }));
    }

    /** Un recurso que siempre existe (una lista o una página). */
    <T> Mono<T> get(Function<UriBuilder, URI> uri, Class<T> type) {
        return guard(webClient.get().uri(uri).retrieve().bodyToMono(type));
    }

    /** Una lista JSON (array en la raíz). */
    <T> Mono<List<T>> getList(Function<UriBuilder, URI> uri, Class<T> type) {
        return guard(webClient.get().uri(uri).retrieve().bodyToFlux(type).collectList());
    }

    private <T> Mono<T> guard(Mono<T> call) {
        return call
                .transformDeferred(TimeLimiterOperator.of(timeLimiter))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
                .onErrorMap(error -> new DownstreamServiceUnavailableException(service, error));
    }
}
