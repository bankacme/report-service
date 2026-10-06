package com.bank.report.domain.model;

import java.time.LocalDate;
import java.util.List;

/** Datos de cabecera del reporte; todos opcionales según el producto. */
public record ProductAttributes(
        String maskedNumber,
        Money creditLimit,
        Money principalAmount,
        LocalDate dueDate,
        List<String> linkedAccountIds,
        String mainAccountId,
        String expiryDate) {

    public ProductAttributes {
        linkedAccountIds = linkedAccountIds == null ? List.of() : List.copyOf(linkedAccountIds);
    }

    public static ProductAttributes none() {
        return new ProductAttributes(null, null, null, null, List.of(), null, null);
    }

    /** "**** " + últimos 4 dígitos (data-model 5), para el número de cuenta. */
    public static String mask(String number) {
        if (number == null || number.length() < 4) {
            return number;
        }
        return "**** " + number.substring(number.length() - 4);
    }
}
