package com.bank.report.domain;

import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import java.time.Instant;

/** Movimientos de prueba de la cuenta A1. */
public final class Movements {

    public static final String ACCOUNT = "acc-A1";

    private Movements() {
    }

    public static ReportMovement completed(String id, MovementType type, String amount, String balance,
                                           String occurredAt) {
        return movement(id, type, amount, balance, occurredAt, MovementStatus.COMPLETED);
    }

    public static ReportMovement movement(String id, MovementType type, String amount, String balance,
                                          String occurredAt, MovementStatus status) {
        return new ReportMovement(id, "op-" + id, ACCOUNT, ProductType.ACCOUNT, "cust-A", type, Money.of(amount),
                balance == null ? null : Money.of(balance), status, null, null, null, null, null,
                Instant.parse(occurredAt));
    }
}
