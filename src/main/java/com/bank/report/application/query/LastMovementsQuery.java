package com.bank.report.application.query;

import com.bank.report.domain.model.ProductType;

/** Últimos movimientos de un producto; {@code limit} opcional (10 por defecto, 1–50). */
public record LastMovementsQuery(ProductType productType, String productId, Integer limit) {
}
