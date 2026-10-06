package com.bank.report.domain.model;

import java.time.Instant;

/** Foto de un producto (cabecera del reporte). Sin comportamiento: en P2 se arma en cada consulta. */
public record ReportProduct(
        String productId,
        ProductType productType,
        ProductCategory category,
        String customerId,
        ProductStatus status,
        ProductAttributes attributes,
        Instant updatedAt) {

    public ReportProduct {
        if (productId == null || productId.isBlank() || productType == null || category == null
                || customerId == null || customerId.isBlank() || status == null || updatedAt == null) {
            throw new IllegalArgumentException("Required ReportProduct fields must not be null/blank");
        }
        attributes = attributes == null ? ProductAttributes.none() : attributes;
    }
}
