package com.bank.report.domain.service;

import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportSummary;
import com.bank.report.domain.model.TypeTotal;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resumen de un intervalo (regla 5, data-model 2.2), puro y con Streams. Recibe TODOS los
 * movimientos del intervalo (no solo la página, regla 4) y el último anterior al intervalo.
 * Ignora lo que no esté COMPLETED (regla 2), aunque la fuente ya lo filtre.
 */
public class ProductReportCalculator {

    public ReportSummary summarize(Collection<ReportMovement> inRange, Optional<ReportMovement> previous) {
        List<ReportMovement> completed = inRange.stream()
                .filter(ReportMovement::isCompleted)
                .sorted(ReportMovement.MOST_RECENT_FIRST)
                .toList();

        // Ordenado por el nombre del tipo, como el ejemplo del contrato (DEPOSIT, FEE, TRANSFER_IN, ...).
        Map<MovementType, List<ReportMovement>> byType = completed.stream()
                .collect(Collectors.groupingBy(ReportMovement::type,
                        () -> new TreeMap<>(Comparator.comparing(MovementType::name)), Collectors.toList()));
        List<TypeTotal> totalsByType = byType.entrySet().stream()
                .map(entry -> new TypeTotal(entry.getKey(), entry.getValue().size(), sum(entry.getValue())))
                .toList();

        Money totalFees = sum(byType.getOrDefault(MovementType.FEE, List.of()));
        Money openingBalance = previous
                .filter(ReportMovement::isCompleted)
                .map(ReportMovement::resultingBalance)
                .orElse(null);
        Money closingBalance = completed.stream()
                .findFirst()
                .map(ReportMovement::resultingBalance)
                .orElse(null);

        return new ReportSummary(completed.size(), totalsByType, totalFees, openingBalance, closingBalance);
    }

    private static Money sum(List<ReportMovement> movements) {
        return movements.stream()
                .map(ReportMovement::amount)
                .reduce(Money.zero(), Money::plus);
    }
}
