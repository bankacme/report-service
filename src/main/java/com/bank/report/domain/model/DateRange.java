package com.bank.report.domain.model;

import com.bank.report.domain.exception.InvalidRangeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Intervalo del reporte (regla 1): {@code from ≤ to} y como máximo {@code maxDays} días contados
 * ambos extremos (366 por defecto, {@code report.max-range-days}). Se interpreta como
 * {@code [from 00:00, to+1 00:00)} en la zona del banco.
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
    }

    public static DateRange of(LocalDate from, LocalDate to, int maxDays) {
        if (from == null || to == null) {
            throw new InvalidRangeException("from and to are required");
        }
        if (from.isAfter(to)) {
            throw new InvalidRangeException("from (" + from + ") must not be after to (" + to + ")");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > maxDays) {
            throw new InvalidRangeException("The range covers " + days + " days; the maximum is " + maxDays);
        }
        return new DateRange(from, to);
    }

    public Instant startInclusive(ZoneId zone) {
        return from.atStartOfDay(zone).toInstant();
    }

    public Instant endExclusive(ZoneId zone) {
        return to.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public boolean contains(Instant instant, ZoneId zone) {
        return !instant.isBefore(startInclusive(zone)) && instant.isBefore(endExclusive(zone));
    }

    /** Último día antes del intervalo: para buscar el movimiento que da el saldo inicial. */
    public LocalDate dayBefore() {
        return from.minusDays(1);
    }
}
