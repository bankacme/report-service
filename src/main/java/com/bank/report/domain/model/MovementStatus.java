package com.bank.report.domain.model;

/** Solo estos dos se guardan o se traen; los reportes usan únicamente COMPLETED (regla 2). */
public enum MovementStatus {
    COMPLETED,
    REVERSED
}
