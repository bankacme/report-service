package com.bank.report.infrastructure.adapter.out.rest;

import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.domain.exception.NotAvailableException;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.ProductAttributes;
import com.bank.report.domain.model.ProductCategory;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportProduct;
import com.bank.report.infrastructure.support.RxJavaReactorBridge;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * {@link ProductQueryPort} de P2 (data-model 3.1): la cabecera sale del servicio dueño.
 * <ul>
 *   <li>accounts → account-service {@code GET /accounts/{id}} (categoría = tipo de cuenta).</li>
 *   <li>credits → credit-service {@code GET /credits/{id}} (categoría por {@code ownerType}).</li>
 *   <li>credit-cards → credit-service {@code GET /credit-cards/{id}}.</li>
 *   <li>debit-cards → 501: las tarjetas de débito llegan en P3.</li>
 * </ul>
 */
@Component
public class ProductRestAdapter implements ProductQueryPort {

    private final RestSource accounts;
    private final RestSource credits;
    private final Clock clock;

    public ProductRestAdapter(@LoadBalanced WebClient.Builder builder,
                              @Value("${bank.clients.account-service.base-url}") String accountServiceUrl,
                              @Value("${bank.clients.credit-service.base-url}") String creditServiceUrl,
                              CircuitBreakerRegistry circuitBreakerRegistry, TimeLimiterRegistry timeLimiterRegistry,
                              Clock clock) {
        this.accounts = new RestSource("account-service", builder, accountServiceUrl, circuitBreakerRegistry,
                timeLimiterRegistry);
        this.credits = new RestSource("credit-service", builder, creditServiceUrl, circuitBreakerRegistry,
                timeLimiterRegistry);
        this.clock = clock;
    }

    @Override
    public Maybe<ReportProduct> findById(ProductType productType, String productId) {
        Mono<ReportProduct> product = switch (productType) {
            case ACCOUNT -> accounts.getOptional(uri -> uri.path("/accounts/{id}").build(productId),
                    AccountResponse.class).map(this::toProduct);
            case CREDIT -> credits.getOptional(uri -> uri.path("/credits/{id}").build(productId),
                    CreditResponse.class).map(this::toProduct);
            case CREDIT_CARD -> credits.getOptional(uri -> uri.path("/credit-cards/{id}").build(productId),
                    CardResponse.class).map(this::toProduct);
            case DEBIT_CARD -> Mono.error(new NotAvailableException(
                    "Debit card reports are available from P3 (debit-service does not exist yet)"));
        };
        return RxJavaReactorBridge.toMaybe(product);
    }

    /** credit-service {@code GET /credit-cards?customerId=} (todas, en cualquier estado). */
    @Override
    public Flowable<ReportProduct> findCardsByCustomer(String customerId) {
        Mono<List<ReportProduct>> cards = credits.getList(
                        uri -> uri.path("/credit-cards").queryParam("customerId", customerId).build(),
                        CardResponse.class)
                .map(list -> list.stream().map(this::toProduct).toList());
        return RxJavaReactorBridge.toSingle(cards).flattenAsFlowable(list -> list);
    }

    @Override
    public boolean debitCardsAvailable() {
        return false;
    }

    private ReportProduct toProduct(AccountResponse account) {
        ProductAttributes attributes = new ProductAttributes(ProductAttributes.mask(account.accountNumber()), null,
                null, null, List.of(), null, null);
        return new ReportProduct(account.id(), ProductType.ACCOUNT, ProductCategory.valueOf(account.type()),
                account.customerId(), ProductStatus.valueOf(account.status()), attributes, clock.instant());
    }

    private ReportProduct toProduct(CreditResponse credit) {
        ProductCategory category = "BUSINESS".equals(credit.ownerType())
                ? ProductCategory.BUSINESS_CREDIT
                : ProductCategory.PERSONAL_CREDIT;
        ProductAttributes attributes = new ProductAttributes(null, null, money(credit.principalAmount()),
                credit.dueDate(), List.of(), null, null);
        return new ReportProduct(credit.id(), ProductType.CREDIT, category, credit.customerId(),
                ProductStatus.valueOf(credit.status()), attributes, clock.instant());
    }

    private ReportProduct toProduct(CardResponse card) {
        ProductAttributes attributes = new ProductAttributes(card.maskedNumber(), money(card.creditLimit()), null,
                null, List.of(), null, null);
        return new ReportProduct(card.id(), ProductType.CREDIT_CARD, ProductCategory.CREDIT_CARD,
                card.customerId(), ProductStatus.valueOf(card.status()), attributes, clock.instant());
    }

    private static Money money(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }

    /** Solo los campos que toma el reporte; el resto del cuerpo se ignora. */
    private record AccountResponse(String id, String accountNumber, String customerId, String type, String status) {
    }

    private record CreditResponse(String id, String customerId, String ownerType, String status,
                                  BigDecimal principalAmount, LocalDate dueDate) {
    }

    private record CardResponse(String id, String customerId, String status, String maskedNumber,
                                BigDecimal creditLimit) {
    }
}
