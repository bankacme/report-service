package com.bank.report.domain.model;

import java.util.List;

/**
 * Resumen de todo el intervalo (regla 5). {@code openingBalance} y {@code closingBalance} son
 * nulos cuando no hay movimiento anterior al intervalo o el intervalo no tiene movimientos.
 */
public record ReportSummary(
        long movementsCount,
        List<TypeTotal> totalsByType,
        Money totalFees,
        Money openingBalance,
        Money closingBalance) {

    public ReportSummary {
        totalsByType = List.copyOf(totalsByType);
    }
}
