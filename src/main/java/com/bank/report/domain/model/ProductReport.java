package com.bank.report.domain.model;

import java.time.Instant;

/** Reporte completo de un producto en un intervalo: cabecera, resumen y una página de movimientos. */
public record ProductReport(
        ReportProduct product,
        DateRange period,
        ReportSummary summary,
        MovementPage movements,
        Instant generatedAt) {
}
