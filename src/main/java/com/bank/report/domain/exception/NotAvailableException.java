package com.bank.report.domain.exception;

/** 501 NOT_AVAILABLE: lo que solo existe desde P3 (tarjetas de débito, reporte por categoría). */
public class NotAvailableException extends RuntimeException {

    public NotAvailableException(String message) {
        super(message);
    }
}
