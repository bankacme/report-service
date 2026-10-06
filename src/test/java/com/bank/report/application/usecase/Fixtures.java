package com.bank.report.application.usecase;

import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductAttributes;
import com.bank.report.domain.model.ProductCategory;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportProduct;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

final class Fixtures {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-06T15:00:00Z"), ZoneOffset.UTC);

    private Fixtures() {
    }

    static ReportProduct savings(String id, String customerId) {
        return new ReportProduct(id, ProductType.ACCOUNT, ProductCategory.SAVINGS, customerId, ProductStatus.ACTIVE,
                new ProductAttributes("**** 4871", null, null, null, List.of(), null, null), CLOCK.instant());
    }

    static ReportProduct creditCard(String id, String customerId, ProductStatus status) {
        return new ReportProduct(id, ProductType.CREDIT_CARD, ProductCategory.CREDIT_CARD, customerId, status,
                new ProductAttributes("**** 1234", Money.of("2000.00"), null, null, List.of(), null, null),
                CLOCK.instant());
    }

    static ReportMovement movement(String id, String productId, MovementType type, String amount, String balance,
                                   String occurredAt) {
        return new ReportMovement(id, "op-" + id, productId, ProductType.ACCOUNT, "cust-A", type, Money.of(amount),
                balance == null ? null : Money.of(balance), MovementStatus.COMPLETED, null, null, null, null, null,
                Instant.parse(occurredAt));
    }
}
