package com.bank.report.domain.exception;

/**
 * 422 RANGE_TOO_LARGE: el intervalo tiene más movimientos que el tope (regla 11). Se rechaza en
 * lugar de devolver un reporte parcial.
 */
public class RangeTooLargeException extends RuntimeException {

    public RangeTooLargeException(long movements, int max) {
        super("The range has " + movements + " movements; the maximum per report is " + max);
    }
}
