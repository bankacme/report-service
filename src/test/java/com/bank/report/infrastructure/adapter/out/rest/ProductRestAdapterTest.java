package com.bank.report.infrastructure.adapter.out.rest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.domain.exception.DownstreamServiceUnavailableException;
import com.bank.report.domain.exception.NotAvailableException;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.ProductCategory;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportProduct;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;

class ProductRestAdapterTest {

    @RegisterExtension
    static WireMockExtension accountService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();
    @RegisterExtension
    static WireMockExtension creditService = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T15:00:00Z"), ZoneOffset.UTC);

    private ProductRestAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductRestAdapter(WebClient.builder(), accountService.baseUrl(), creditService.baseUrl(),
                ResilienceTestSupport.circuitBreakers(), ResilienceTestSupport.timeLimiters(), CLOCK);
    }

    private ReportProduct find(ProductType type, String id) {
        return adapter.findById(type, id).timeout(5, TimeUnit.SECONDS).blockingGet();
    }

    @Test
    void anAccountTakesItsCategoryFromItsTypeAndMasksTheNumber() {
        accountService.stubFor(get(urlEqualTo("/accounts/acc-1")).willReturn(okJson("""
                { "id": "acc-1", "accountNumber": "19412345674871", "customerId": "cust-A", "type": "SAVINGS",
                  "status": "ACTIVE", "balance": 1000.00, "conditions": {} }
                """)));

        ReportProduct product = find(ProductType.ACCOUNT, "acc-1");

        assertThat(product.category()).isEqualTo(ProductCategory.SAVINGS);
        assertThat(product.customerId()).isEqualTo("cust-A");
        assertThat(product.attributes().maskedNumber()).isEqualTo("**** 4871");
        assertThat(product.updatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void aCreditTakesItsCategoryFromItsOwnerType() {
        creditService.stubFor(get(urlEqualTo("/credits/cr-1")).willReturn(okJson("""
                { "id": "cr-1", "customerId": "cust-E", "ownerType": "BUSINESS", "status": "PAID",
                  "principalAmount": 20000.00, "outstandingBalance": 0, "dueDate": "2028-12-31" }
                """)));

        ReportProduct product = find(ProductType.CREDIT, "cr-1");

        assertThat(product.category()).isEqualTo(ProductCategory.BUSINESS_CREDIT);
        assertThat(product.status()).isEqualTo(ProductStatus.PAID);
        assertThat(product.attributes().principalAmount()).isEqualTo(Money.of("20000.00"));
        assertThat(product.attributes().dueDate()).isEqualTo(LocalDate.of(2028, 12, 31));
    }

    @Test
    void aCreditCardKeepsItsMaskedNumberAndLimit() {
        creditService.stubFor(get(urlEqualTo("/credit-cards/cc-1")).willReturn(okJson("""
                { "id": "cc-1", "customerId": "cust-A", "status": "OVERDUE", "maskedNumber": "**** 1234",
                  "creditLimit": 2000.00, "usedAmount": 300.00 }
                """)));

        ReportProduct product = find(ProductType.CREDIT_CARD, "cc-1");

        assertThat(product.category()).isEqualTo(ProductCategory.CREDIT_CARD);
        assertThat(product.status()).isEqualTo(ProductStatus.OVERDUE);
        assertThat(product.attributes().creditLimit()).isEqualTo(Money.of("2000.00"));
    }

    @Test
    void a404IsEmpty() {
        accountService.stubFor(get(urlEqualTo("/accounts/ghost")).willReturn(aResponse().withStatus(404)));

        adapter.findById(ProductType.ACCOUNT, "ghost").test().awaitDone(5, TimeUnit.SECONDS)
                .assertNoValues().assertComplete();
    }

    @Test
    void debitCardsAreNotAvailableInP2() {
        adapter.findById(ProductType.DEBIT_CARD, "dc-1").test().assertError(NotAvailableException.class);
        assertThat(adapter.debitCardsAvailable()).isFalse();
    }

    @Test
    void aServerErrorOrATimeoutIsServiceUnavailable() {
        accountService.stubFor(get(urlEqualTo("/accounts/acc-1")).willReturn(aResponse().withStatus(500)));
        creditService.stubFor(get(urlEqualTo("/credits/cr-1")).willReturn(okJson("{}").withFixedDelay(2500)));

        adapter.findById(ProductType.ACCOUNT, "acc-1").test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DownstreamServiceUnavailableException.class);
        adapter.findById(ProductType.CREDIT, "cr-1").test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DownstreamServiceUnavailableException.class);
    }

    @Test
    void listsAllTheCreditCardsOfACustomer() {
        creditService.stubFor(get(urlPathEqualTo("/credit-cards")).withQueryParam("customerId", equalTo("cust-A"))
                .willReturn(okJson("""
                        [ { "id": "cc-1", "customerId": "cust-A", "status": "ACTIVE", "maskedNumber": "**** 1234",
                            "creditLimit": 2000.00 },
                          { "id": "cc-2", "customerId": "cust-A", "status": "CLOSED", "maskedNumber": "**** 9876",
                            "creditLimit": 500.00 } ]
                        """)));

        List<ReportProduct> cards = adapter.findCardsByCustomer("cust-A").toList().blockingGet();

        assertThat(cards).extracting(ReportProduct::productId).containsExactly("cc-1", "cc-2");
        assertThat(cards).extracting(ReportProduct::productType).containsOnly(ProductType.CREDIT_CARD);
    }

    @Test
    void creditServiceDownMakesTheCardsReportUnavailable() {
        creditService.stubFor(get(urlPathEqualTo("/credit-cards")).willReturn(aResponse().withStatus(503)));

        adapter.findCardsByCustomer("cust-A").test().awaitDone(5, TimeUnit.SECONDS)
                .assertError(DownstreamServiceUnavailableException.class);
    }
}
