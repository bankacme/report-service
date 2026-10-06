package com.bank.report.domain.service;

import static com.bank.report.domain.Movements.completed;
import static com.bank.report.domain.Movements.movement;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.report.domain.model.MovementStatus;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ReportMovement;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class LastMovementsSelectorTest {

    private final LastMovementsSelector selector = new LastMovementsSelector();

    private final List<ReportMovement> twelve = IntStream.rangeClosed(1, 12)
            .mapToObj(i -> completed(String.format("m%02d", i), MovementType.CARD_CHARGE, "10.00", null,
                    String.format("2026-09-%02dT15:00:00Z", i)))
            .toList();

    @Test
    void takesTheTenMostRecentByDefault() {
        List<ReportMovement> last = selector.select(twelve, LastMovementsSelector.DEFAULT_LIMIT);

        assertThat(last).hasSize(10);
        assertThat(last.get(0).movementId()).isEqualTo("m12");
        assertThat(last.get(9).movementId()).isEqualTo("m03");
    }

    @Test
    void withFewerThanTheLimitReturnsWhatThereIs() {
        assertThat(selector.select(twelve.subList(0, 3), 10)).hasSize(3);
    }

    @Test
    void skipsReversedMovements() {
        List<ReportMovement> mixed = List.of(
                completed("m1", MovementType.CARD_CHARGE, "10.00", null, "2026-09-01T10:00:00Z"),
                movement("m2", MovementType.CARD_PAYMENT, "10.00", null, "2026-09-02T10:00:00Z",
                        MovementStatus.REVERSED));

        assertThat(selector.select(mixed, 10)).extracting(ReportMovement::movementId).containsExactly("m1");
    }

    @Test
    void theLimitMustBePositive() {
        assertThatThrownBy(() -> selector.select(twelve, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
