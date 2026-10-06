package com.bank.report.domain.model;

/**
 * Categoría del producto (data-model 1.4): la cuenta usa su tipo, el crédito su dueño
 * (PERSONAL/BUSINESS), las tarjetas la suya.
 */
public enum ProductCategory {
    SAVINGS,
    CHECKING,
    FIXED_TERM,
    PERSONAL_CREDIT,
    BUSINESS_CREDIT,
    CREDIT_CARD,
    DEBIT_CARD
}
