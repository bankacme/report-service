package com.bank.report.domain.model;

/** Estado del producto en su servicio dueño (la unión de los de cuentas, créditos y tarjetas). */
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    OVERDUE,
    PAID,
    CLOSED
}
