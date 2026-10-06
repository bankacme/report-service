package com.bank.report.domain.service;

import static com.bank.report.domain.Movements.completed;
import static com.bank.report.domain.Movements.movement;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportSummary;
import com.bank.report.domain.model.TypeTotal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProductReportCalculatorTest {

    private final ProductReportCalculator calculator = new ProductReportCalculator();

    /** El ejemplo de data-model §8: cuenta A1 en setiembre de 2026. */
    private final List<ReportMovement> september = List.of(
            completed("m1", MovementType.DEPOSIT, "250.00", "1250.00", "2026-09-03T14:00:00Z"),
            completed("m2", MovementType.WITHDRAWAL, "150.00", "1100.00", "2026-09-05T14:00:00Z"),
            completed("m3", MovementType.DEPOSIT, "100.00", "1200.00", "2026-09-08T13:45:00Z"),
            completed("m4", MovementType.TRANSFER_OUT, "100.00", "1100.00", "2026-09-10T14:10:00Z"),
            completed("m5", MovementType.TRANSFER_IN, "50.00", "1150.00", "2026-09-15T16:02:00Z"),
            completed("m6", MovementType.WITHDRAWAL, "148.00", "1000.00", "2026-09-24T15:24:00Z"),
            completed("m7", MovementType.FEE, "2.00", "1000.00", "2026-09-24T15:24:00Z"));
    private final ReportMovement august = completed("m0", MovementType.DEPOSIT, "1000.00", "1000.00",
            "2026-08-20T15:00:00Z");

    @Test
    void summarizesTheDataModelExample() {
        ReportSummary summary = calculator.summarize(september, Optional.of(august));

        assertThat(summary.movementsCount()).isEqualTo(7);
        assertThat(summary.totalsByType()).containsExactly(
                new TypeTotal(MovementType.DEPOSIT, 2, Money.of("350.00")),
                new TypeTotal(MovementType.FEE, 1, Money.of("2.00")),
                new TypeTotal(MovementType.TRANSFER_IN, 1, Money.of("50.00")),
                new TypeTotal(MovementType.TRANSFER_OUT, 1, Money.of("100.00")),
                new TypeTotal(MovementType.WITHDRAWAL, 2, Money.of("298.00")));
        assertThat(summary.totalFees()).isEqualTo(Money.of("2.00"));
        assertThat(summary.openingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(summary.closingBalance()).isEqualTo(Money.of("1000.00"));
    }

    @Test
    void reversedMovementsDoNotCount() {
        List<ReportMovement> withReversal = List.of(
                completed("m1", MovementType.DEPOSIT, "100.00", "100.00", "2026-09-01T10:00:00Z"),
                movement("m2", MovementType.TRANSFER_OUT, "40.00", "60.00", "2026-09-02T10:00:00Z",
                        MovementStatus.REVERSED));

        ReportSummary summary = calculator.summarize(withReversal, Optional.empty());

        assertThat(summary.movementsCount()).isEqualTo(1);
        assertThat(summary.totalsByType()).extracting(TypeTotal::type).containsExactly(MovementType.DEPOSIT);
        // El saldo final sale del último COMPLETED, no del revertido.
        assertThat(summary.closingBalance()).isEqualTo(Money.of("100.00"));
    }

    @Test
    void anEmptyRangeHasZeroTotalsAndNoClosingBalance() {
        ReportSummary summary = calculator.summarize(List.of(), Optional.of(august));

        assertThat(summary.movementsCount()).isZero();
        assertThat(summary.totalsByType()).isEmpty();
        assertThat(summary.totalFees()).isEqualTo(Money.zero());
        assertThat(summary.openingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(summary.closingBalance()).isNull();
    }

    @Test
    void withoutAPreviousMovementThereIsNoOpeningBalance() {
        // La apertura de una cuenta no es un movimiento: no hay saldo inicial.
        ReportSummary summary = calculator.summarize(september, Optional.empty());

        assertThat(summary.openingBalance()).isNull();
    }

    @Test
    void aReversedPreviousMovementGivesNoOpeningBalance() {
        ReportMovement reversed = movement("m0", MovementType.DEPOSIT, "5.00", "5.00", "2026-08-01T10:00:00Z",
                MovementStatus.REVERSED);

        assertThat(calculator.summarize(september, Optional.of(reversed)).openingBalance()).isNull();
    }

    @Test
    void theClosingBalanceComesFromTheMostRecentEvenIfTheInputIsUnordered() {
        List<ReportMovement> unordered = List.of(september.get(6), september.get(0), september.get(3));

        assertThat(calculator.summarize(unordered, Optional.empty()).closingBalance()).isEqualTo(Money.of("1000.00"));
    }
}
