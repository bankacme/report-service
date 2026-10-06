package com.bank.report.application.query;

/**
 * Límites de los reportes (data-model §7), desde el Config Server: {@code report.max-range-days},
 * {@code report.max-movements}, {@code report.page.max-size} y {@code report.last-movements.max}.
 */
public record ReportSettings(int maxRangeDays, int maxMovements, int maxPageSize, int maxLastMovements) {

    public static ReportSettings defaults() {
        return new ReportSettings(366, 5000, 100, 50);
    }
}
