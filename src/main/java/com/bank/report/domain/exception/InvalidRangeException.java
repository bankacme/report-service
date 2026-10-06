package com.bank.report.domain.exception;

/** 400 INVALID_RANGE: {@code from > to} o más días que el máximo (regla 1). */
public class InvalidRangeException extends RuntimeException {

    public InvalidRangeException(String message) {
        super(message);
    }
}
