package com.bank.report.domain.model;

/** Cantidad y suma de los movimientos de un tipo. */
public record TypeTotal(MovementType type, long count, Money totalAmount) {
}
