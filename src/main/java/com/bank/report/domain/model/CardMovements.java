package com.bank.report.domain.model;

import java.util.List;

/** Una tarjeta con sus últimos movimientos. */
public record CardMovements(ReportProduct card, List<ReportMovement> movements) {

    public CardMovements {
        movements = List.copyOf(movements);
    }
}
