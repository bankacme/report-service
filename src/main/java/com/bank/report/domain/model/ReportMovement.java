package com.bank.report.domain.model;

import java.time.Instant;
import java.util.Comparator;

/** Un movimiento ya ocurrido de un producto. Inmutable. */
public record ReportMovement(
        String movementId,
        String operationId,
        String productId,
        ProductType productType,
        String customerId,
        MovementType type,
        Money amount,
        Money resultingBalance,
        MovementStatus status,
        String description,
        String transferId,
        String parentMovementId,
        Money fee,
        String accountId,
        Instant occurredAt) {

    /**
     * Regla 3 / data-model 2.1: más reciente primero; en empate (una comisión y su movimiento
     * comparten {@code occurredAt}), por {@code movementId} descendente, para que sea determinista.
     */
    public static final Comparator<ReportMovement> MOST_RECENT_FIRST = Comparator
            .comparing(ReportMovement::occurredAt)
            .thenComparing(ReportMovement::movementId)
            .reversed();

    public ReportMovement {
        if (movementId == null || movementId.isBlank() || productId == null || productId.isBlank()
                || productType == null || type == null || amount == null || status == null || occurredAt == null) {
            throw new IllegalArgumentException("Required ReportMovement fields must not be null/blank");
        }
    }

    public boolean isCompleted() {
        return status == MovementStatus.COMPLETED;
    }
}
