package com.bank.report.domain.model;

/** Los 11 tipos de movimiento de transaction-service. */
public enum MovementType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    FEE,
    CREDIT_PAYMENT,
    CARD_PAYMENT,
    CARD_CHARGE,
    DEBIT_PAYMENT,
    YANKI_PAYMENT_OUT,
    YANKI_PAYMENT_IN
}
