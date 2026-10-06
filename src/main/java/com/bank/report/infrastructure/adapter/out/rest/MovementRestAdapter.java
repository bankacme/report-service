package com.bank.report.infrastructure.adapter.out.rest;

import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.infrastructure.support.RxJavaReactorBridge;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link MovementQueryPort} de P2 (data-model 3.2): siempre transaction-service
 * {@code GET /products/{productId}/transactions?status=COMPLETED}, que ya excluye REVERSED, FAILED,
 * PENDING y DISCARDED y devuelve los más recientes primero.
 */
@Component
public class MovementRestAdapter implements MovementQueryPort {

    /** El máximo del contrato: menos llamadas al recorrer el intervalo para el resumen. */
    static final int PAGE_SIZE = 100;
    private static final String COMPLETED = "COMPLETED";

    private final RestSource transactions;

    public MovementRestAdapter(@LoadBalanced WebClient.Builder builder,
                               @Value("${bank.clients.transaction-service.base-url}") String transactionServiceUrl,
                               CircuitBreakerRegistry circuitBreakerRegistry,
                               TimeLimiterRegistry timeLimiterRegistry) {
        this.transactions = new RestSource("transaction-service", builder, transactionServiceUrl,
                circuitBreakerRegistry, timeLimiterRegistry);
    }

    /** Una página de tamaño 1: solo interesa {@code totalElements} (data-model 2.5). */
    @Override
    public Single<Long> countInRange(String productId, DateRange range) {
        return RxJavaReactorBridge.toSingle(page(productId, range.from(), range.to(), 0, 1)
                .map(TransactionPage::totalElements));
    }

    /** Recorre todas las páginas de 100 del intervalo, una tras otra. */
    @Override
    public Flowable<ReportMovement> findInRange(String productId, DateRange range) {
        Flux<TransactionResponse> all = page(productId, range.from(), range.to(), 0, PAGE_SIZE)
                .flatMapMany(first -> Flux.concat(
                        Flux.fromIterable(first.content()),
                        Flux.range(1, Math.max(0, first.totalPages() - 1))
                                .concatMap(number -> page(productId, range.from(), range.to(), number, PAGE_SIZE)
                                        .flatMapIterable(TransactionPage::content))));
        return RxJavaReactorBridge.toFlowable(all.map(MovementRestAdapter::toMovement));
    }

    /** Sin {@code from}: todo el historial hasta {@code day}; el primero es el más reciente. */
    @Override
    public Maybe<ReportMovement> findLastUpTo(String productId, LocalDate day) {
        Mono<ReportMovement> last = page(productId, null, day, 0, 1)
                .flatMap(page -> Mono.justOrEmpty(page.content().stream().findFirst()))
                .map(MovementRestAdapter::toMovement);
        return RxJavaReactorBridge.toMaybe(last);
    }

    @Override
    public Flowable<ReportMovement> findLast(String productId, int limit) {
        return RxJavaReactorBridge.toFlowable(page(productId, null, null, 0, limit)
                .flatMapIterable(TransactionPage::content)
                .map(MovementRestAdapter::toMovement));
    }

    private Mono<TransactionPage> page(String productId, LocalDate from, LocalDate to, int page, int size) {
        Function<UriBuilder, URI> uri = builder -> {
            builder.path("/products/{productId}/transactions")
                    .queryParam("status", COMPLETED)
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (from != null) {
                builder.queryParam("from", from);
            }
            if (to != null) {
                builder.queryParam("to", to);
            }
            return builder.build(productId);
        };
        return transactions.get(uri, TransactionPage.class);
    }

    private static ReportMovement toMovement(TransactionResponse transaction) {
        return new ReportMovement(transaction.id(), transaction.operationId(), transaction.productId(),
                ProductType.valueOf(transaction.productType()), transaction.customerId(),
                MovementType.valueOf(transaction.type()), Money.of(transaction.amount()),
                transaction.resultingBalance() == null ? null : Money.of(transaction.resultingBalance()),
                MovementStatus.valueOf(transaction.status()), transaction.description(), transaction.transferId(),
                transaction.parentTransactionId(), null, null, transaction.occurredAt());
    }

    private record TransactionPage(int page, int size, long totalElements, int totalPages,
                                   List<TransactionResponse> content) {

        TransactionPage {
            content = content == null ? List.of() : content;
        }
    }

    private record TransactionResponse(String id, String operationId, String productId, String productType,
                                       String customerId, String type, BigDecimal amount,
                                       BigDecimal resultingBalance, String status, String description,
                                       String transferId, String parentTransactionId, Instant occurredAt) {
    }
}
