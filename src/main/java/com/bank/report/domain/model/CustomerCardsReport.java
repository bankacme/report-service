package com.bank.report.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Últimos movimientos de cada tarjeta de crédito y de débito de un cliente. En P2 no hay tarjetas
 * de débito: {@code debitCards} vacío y {@code debitCardsIncluded = false}.
 */
public record CustomerCardsReport(
        String customerId,
        boolean debitCardsIncluded,
        List<CardMovements> creditCards,
        List<CardMovements> debitCards,
        Instant generatedAt) {

    public CustomerCardsReport {
        creditCards = List.copyOf(creditCards);
        debitCards = List.copyOf(debitCards);
    }
}
