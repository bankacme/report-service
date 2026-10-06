package com.bank.report.infrastructure.adapter.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.domain.exception.DownstreamServiceUnavailableException;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class MovementRestAdapterTest {

    private static final String PATH = "/products/acc-1/transactions";
    private static final DateRange SEPTEMBER = new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    @RegisterExtension
    static WireMockExtension transactionService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private MovementRestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new MovementRestAdapter(WebClient.builder(), transactionService.baseUrl(),
                ResilienceTestSupport.circuitBreakers(), ResilienceTestSupport.timeLimiters());
    }

    private static String transaction(String id, String type) {
        return """
                { "id": "%s", "operationId": "op-%s", "productId": "acc-1", "productType": "ACCOUNT",
                  "customerId": "cust-A", "type": "%s", "amount": 2.00, "resultingBalance": 1000.00,
                  "status": "COMPLETED", "parentTransactionId": "parent-1", "occurredAt": "2026-09-24T15:24:00Z" }
                """.formatted(id, id, type);
    }

    private static String page(int page, int totalElements, int totalPages, List<String> content) {
        return """
                { "page": %d, "size": 100, "totalElements": %d, "totalPages": %d, "content": [ %s ] }
                """.formatted(page, totalElements, totalPages, String.join(",", content));
    }

    @Test
    void countsWithAOneElementPageAndOnlyCompleted() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 7, 7, List.of()))));

        assertThat(adapter.countInRange("acc-1", SEPTEMBER).blockingGet()).isEqualTo(7L);

        transactionService.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("status", equalTo("COMPLETED"))
                .withQueryParam("from", equalTo("2026-09-01"))
                .withQueryParam("to", equalTo("2026-09-30"))
                .withQueryParam("size", equalTo("1")));
    }

    @Test
    void walksEveryPageOfTheRange() {
        List<String> firstHundred = IntStream.range(0, 100).mapToObj(i -> transaction("t" + i, "DEPOSIT"))
                .collect(Collectors.toList());
        transactionService.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo("0"))
                .willReturn(okJson(page(0, 102, 2, firstHundred))));
        transactionService.stubFor(get(urlPathEqualTo(PATH)).withQueryParam("page", equalTo("1"))
                .willReturn(okJson(page(1, 102, 2, List.of(transaction("t100", "WITHDRAWAL"),
                        transaction("t101", "FEE"))))));

        List<ReportMovement> movements = adapter.findInRange("acc-1", SEPTEMBER).toList().blockingGet();

        assertThat(movements).hasSize(102);
        assertThat(movements.get(101).type()).isEqualTo(MovementType.FEE);
        transactionService.verify(2, getRequestedFor(urlPathEqualTo(PATH)).withQueryParam("size", equalTo("100")));
    }

    @Test
    void mapsEveryFieldOfATransaction() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 1, 1,
                List.of(transaction("t1", "FEE"))))));

        ReportMovement movement = adapter.findInRange("acc-1", SEPTEMBER).blockingFirst();

        assertThat(movement.movementId()).isEqualTo("t1");
        assertThat(movement.operationId()).isEqualTo("op-t1");
        assertThat(movement.productType()).isEqualTo(ProductType.ACCOUNT);
        assertThat(movement.amount()).isEqualTo(Money.of("2.00"));
        assertThat(movement.resultingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(movement.status()).isEqualTo(MovementStatus.COMPLETED);
        assertThat(movement.parentMovementId()).isEqualTo("parent-1");
        assertThat(movement.occurredAt()).isEqualTo(Instant.parse("2026-09-24T15:24:00Z"));
    }

    @Test
    void theLastMovementUpToADayAsksWithoutFrom() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 3, 3,
                List.of(transaction("t9", "DEPOSIT"))))));

        assertThat(adapter.findLastUpTo("acc-1", LocalDate.of(2026, 8, 31)).blockingGet().movementId())
                .isEqualTo("t9");

        transactionService.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("from", absent())
                .withQueryParam("to", equalTo("2026-08-31"))
                .withQueryParam("size", equalTo("1")));
    }

    @Test
    void noPreviousMovementIsEmpty() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 0, 0, List.of()))));

        adapter.findLastUpTo("acc-1", LocalDate.of(2026, 8, 31)).test().awaitDone(5, TimeUnit.SECONDS)
                .assertNoValues().assertComplete();
    }

    @Test
    void theLastNIsOnePageOfSizeN() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 2, 1,
                List.of(transaction("t2", "CARD_CHARGE"), transaction("t1", "CARD_PAYMENT"))))));

        assertThat(adapter.findLast("acc-1", 10).toList().blockingGet()).hasSize(2);

        transactionService.verify(getRequestedFor(urlPathEqualTo(PATH))
                .withQueryParam("size", equalTo("10"))
                .withQueryParam("from", absent()));
    }

    @Test
    void transactionServiceDownOrSlowIsServiceUnavailable() {
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
        adapter.countInRange("acc-1", SEPTEMBER).test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DownstreamServiceUnavailableException.class);

        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(page(0, 0, 0, List.of()))
                .withFixedDelay(2500)));
        adapter.findLast("acc-1", 10).test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DownstreamServiceUnavailableException.class);
    }

    @Test
    void afterFourFailuresTheCircuitOpensAndTransactionServiceIsNotCalledAgain() {
        // Mismos umbrales que bank-config: ventana de 4, mínimo 4 llamadas, 50 %.
        transactionService.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(500)));
        for (int i = 0; i < 4; i++) {
            adapter.countInRange("acc-1", SEPTEMBER).test().awaitDone(5, TimeUnit.SECONDS)
                    .assertError(DownstreamServiceUnavailableException.class);
        }

        long start = System.currentTimeMillis();
        adapter.countInRange("acc-1", SEPTEMBER).test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(error -> error instanceof DownstreamServiceUnavailableException
                        && error.getCause() instanceof CallNotPermittedException);

        assertThat(System.currentTimeMillis() - start).as("ms con el circuito abierto").isLessThan(500L);
        transactionService.verify(4, getRequestedFor(urlPathEqualTo(PATH)));
    }
}
