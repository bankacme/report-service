package com.bank.report.infrastructure.adapter.in.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.bank.report.application.port.in.GenerateProductReportUseCase;
import com.bank.report.application.port.in.GetCustomerCardsReportUseCase;
import com.bank.report.application.port.in.GetLastMovementsUseCase;
import com.bank.report.domain.exception.DownstreamServiceUnavailableException;
import com.bank.report.domain.exception.InvalidRangeException;
import com.bank.report.domain.exception.NotAvailableException;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.exception.RangeTooLargeException;
import com.bank.report.domain.model.CardMovements;
import com.bank.report.domain.model.CustomerCardsReport;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.LastMovementsReport;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementPage;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.PageRequest;
import com.bank.report.domain.model.ProductAttributes;
import com.bank.report.domain.model.ProductCategory;
import com.bank.report.domain.model.ProductReport;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportProduct;
import com.bank.report.domain.model.ReportSummary;
import com.bank.report.domain.model.TypeTotal;
import com.bank.report.infrastructure.mapper.ReportRestMapper;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(ReportController.class)
@Import(ReportRestMapper.class)
class ReportControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T15:00:00Z");
    private static final String A1 = "3f1c9a7e-5b2d-4e8a-9c6f-1a2b3c4d5e6f";
    private static final ReportProduct ACCOUNT = new ReportProduct(A1, ProductType.ACCOUNT, ProductCategory.SAVINGS,
            "cust-A", ProductStatus.ACTIVE, new ProductAttributes("**** 4871", null, null, null, List.of(), null,
            null), NOW);
    private static final ReportProduct CARD = new ReportProduct("cc-1", ProductType.CREDIT_CARD,
            ProductCategory.CREDIT_CARD, "cust-A", ProductStatus.ACTIVE, new ProductAttributes("**** 1234",
            Money.of("2000.00"), null, null, List.of(), null, null), NOW);
    private static final ReportMovement FEE = new ReportMovement("m7", "op-7", A1, ProductType.ACCOUNT, "cust-A",
            MovementType.FEE, Money.of("2.00"), Money.of("1000.00"), MovementStatus.COMPLETED, null, null, "m6",
            null, null, Instant.parse("2026-09-24T15:24:00Z"));

    @Autowired
    private WebTestClient client;

    @MockitoBean
    private GenerateProductReportUseCase generateProductReportUseCase;
    @MockitoBean
    private GetLastMovementsUseCase getLastMovementsUseCase;
    @MockitoBean
    private GetCustomerCardsReportUseCase getCustomerCardsReportUseCase;

    @Test
    void returnsTheProductReport() {
        ReportSummary summary = new ReportSummary(1, List.of(new TypeTotal(MovementType.FEE, 1, Money.of("2.00"))),
                Money.of("2.00"), Money.of("1000.00"), null);
        ProductReport report = new ProductReport(ACCOUNT,
                new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)), summary,
                MovementPage.slice(List.of(FEE), new PageRequest(0, 5)), NOW);
        given(generateProductReportUseCase.execute(argThat(query -> query != null
                && query.productType() == ProductType.ACCOUNT && A1.equals(query.productId())
                && Integer.valueOf(5).equals(query.size())))).willReturn(Single.just(report));

        client.get().uri("/api/v1/reports/products/accounts/" + A1 + "?from=2026-09-01&to=2026-09-30&size=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.product.productType").isEqualTo("ACCOUNT")
                .jsonPath("$.product.category").isEqualTo("SAVINGS")
                .jsonPath("$.product.maskedNumber").isEqualTo("**** 4871")
                .jsonPath("$.period.from").isEqualTo("2026-09-01")
                .jsonPath("$.summary.movementsCount").isEqualTo(1)
                .jsonPath("$.summary.totalsByType[0].type").isEqualTo("FEE")
                .jsonPath("$.summary.totalFees").isEqualTo(2.0)
                .jsonPath("$.summary.openingBalance").isEqualTo(1000.0)
                .jsonPath("$.summary.closingBalance").doesNotExist()
                .jsonPath("$.movements.totalElements").isEqualTo(1)
                .jsonPath("$.movements.content[0].parentMovementId").isEqualTo("m6")
                .jsonPath("$.generatedAt").exists();
    }

    @Test
    void returnsTheLastMovements() {
        given(getLastMovementsUseCase.execute(argThat(query -> query != null
                && query.productType() == ProductType.CREDIT_CARD && query.limit() == 10)))
                .willReturn(Single.just(new LastMovementsReport(CARD, 10, List.of(), NOW)));

        client.get().uri("/api/v1/reports/products/credit-cards/cc-1/last-movements").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.limit").isEqualTo(10)
                .jsonPath("$.product.creditLimit").isEqualTo(2000.0)
                .jsonPath("$.movements").isEmpty();
    }

    @Test
    void returnsTheCustomerCards() {
        given(getCustomerCardsReportUseCase.execute(eq("cust-A"))).willReturn(Single.just(new CustomerCardsReport(
                "cust-A", false, List.of(new CardMovements(CARD, List.of())), List.of(), NOW)));

        client.get().uri("/api/v1/reports/customers/cust-A/cards/last-movements").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.debitCardsIncluded").isEqualTo(false)
                .jsonPath("$.creditCards[0].card.productId").isEqualTo("cc-1")
                .jsonPath("$.debitCards").isEmpty();
    }

    @Test
    void anUnknownProductTypeIs400WithoutCallingTheUseCase() {
        client.get().uri("/api/v1/reports/products/loans/x1?from=2026-09-01&to=2026-09-30").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
        verifyNoInteractions(generateProductReportUseCase);
    }

    @Test
    void fromAndToAreRequired() {
        client.get().uri("/api/v1/reports/products/accounts/" + A1 + "?from=2026-09-01").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void aMalformedDateIs400() {
        client.get().uri("/api/v1/reports/products/accounts/" + A1 + "?from=01-09-2026&to=2026-09-30").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void aLimitAbove50Is400() {
        client.get().uri("/api/v1/reports/products/credit-cards/cc-1/last-movements?limit=51").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void mapsEachDomainErrorToItsStatusAndCode() {
        expectError(new InvalidRangeException("from > to"), 400, "INVALID_RANGE");
        expectError(new ProductNotFoundException(ProductType.ACCOUNT, A1), 404, "PRODUCT_NOT_FOUND");
        expectError(new RangeTooLargeException(6000, 5000), 422, "RANGE_TOO_LARGE");
        expectError(new NotAvailableException("P3"), 501, "NOT_AVAILABLE");
        expectError(new DownstreamServiceUnavailableException("transaction-service", new RuntimeException()), 503,
                "SERVICE_UNAVAILABLE");
    }

    private void expectError(RuntimeException error, int status, String code) {
        given(generateProductReportUseCase.execute(any())).willReturn(Single.error(error));

        client.get().uri("/api/v1/reports/products/accounts/" + A1 + "?from=2026-09-01&to=2026-09-30").exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.path").isEqualTo("/api/v1/reports/products/accounts/" + A1);
    }

    @Test
    void theCategoryReportIsNotAvailableInP2() {
        client.get().uri("/api/v1/reports/product-categories/SAVINGS?from=2026-09-01&to=2026-09-30").exchange()
                .expectStatus().isEqualTo(501)
                .expectBody().jsonPath("$.code").isEqualTo("NOT_AVAILABLE");
    }

    @Test
    void anUnknownCategoryIs400() {
        client.get().uri("/api/v1/reports/product-categories/LOANS?from=2026-09-01&to=2026-09-30").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void aCustomerIdLongerThan36Is400() {
        client.get().uri("/api/v1/reports/customers/" + "x".repeat(37) + "/cards/last-movements").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
        verifyNoInteractions(getCustomerCardsReportUseCase);
    }

    @Test
    void aPageSizeAbove100Is400() {
        client.get().uri("/api/v1/reports/products/accounts/" + A1 + "?from=2026-09-01&to=2026-09-30&size=101")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.code").isEqualTo("VALIDATION_ERROR");
    }
}
