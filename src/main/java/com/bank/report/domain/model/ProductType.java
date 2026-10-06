package com.bank.report.domain.model;

/** Tipo de producto del reporte. En la ruta REST va en plural (accounts, credits, ...). */
public enum ProductType {
    ACCOUNT,
    CREDIT,
    CREDIT_CARD,
    DEBIT_CARD
}
