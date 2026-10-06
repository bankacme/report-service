package com.bank.report.application.query;

import com.bank.report.domain.model.ProductType;
import java.time.LocalDate;

/** Reporte completo de un producto en un intervalo; {@code page}/{@code size} opcionales. */
public record ProductReportQuery(ProductType productType, String productId, LocalDate from, LocalDate to,
                                 Integer page, Integer size) {
}
