package com.bank.report.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Importe en soles (regla 14). Escala 2 (HALF_EVEN), nunca negativo, solo PEN en el demo. */
public record Money(BigDecimal amount, String currency) {

    private static final String PEN = "PEN";

    public Money {
        if (amount == null) {
            throw new IllegalArgumentException("Money amount must not be null");
        }
        if (!PEN.equals(currency)) {
            throw new IllegalArgumentException("Only PEN is supported in the demo, got: " + currency);
        }
        amount = amount.setScale(2, RoundingMode.HALF_EVEN);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must not be negative: " + amount);
        }
    }

    public static Money zero() {
        return new Money(BigDecimal.ZERO, PEN);
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount, PEN);
    }

    public static Money of(String amount) {
        return of(new BigDecimal(amount));
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount), currency);
    }
}
