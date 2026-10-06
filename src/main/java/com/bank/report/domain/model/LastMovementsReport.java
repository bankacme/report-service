package com.bank.report.domain.model;

import java.time.Instant;
import java.util.List;

/** Hasta {@code limit} movimientos más recientes de un producto. */
public record LastMovementsReport(ReportProduct product, int limit, List<ReportMovement> movements,
                                  Instant generatedAt) {

    public LastMovementsReport {
        movements = List.copyOf(movements);
    }
}
