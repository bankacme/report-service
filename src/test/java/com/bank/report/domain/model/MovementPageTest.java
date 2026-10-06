package com.bank.report.domain.model;

import static com.bank.report.domain.Movements.completed;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MovementPageTest {

    private final List<ReportMovement> seven = IntStream.rangeClosed(1, 7)
            .mapToObj(i -> completed("m" + i, MovementType.DEPOSIT, "10.00", null, "2026-09-0" + i + "T15:00:00Z"))
            .sorted(ReportMovement.MOST_RECENT_FIRST)
            .toList();

    @Test
    void theFirstPageHasSizeElementsAndTheTotals() {
        MovementPage page = MovementPage.slice(seven, new PageRequest(0, 5));

        assertThat(page.content()).extracting(ReportMovement::movementId).containsExactly("m7", "m6", "m5", "m4", "m3");
        assertThat(page.totalElements()).isEqualTo(7);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void theLastPageHasTheRest() {
        MovementPage page = MovementPage.slice(seven, new PageRequest(1, 5));

        assertThat(page.content()).extracting(ReportMovement::movementId).containsExactly("m2", "m1");
    }

    @Test
    void aPageBeyondTheEndIsEmpty() {
        MovementPage page = MovementPage.slice(seven, new PageRequest(3, 5));

        assertThat(page.content()).isEmpty();
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void noMovementsMeansZeroPages() {
        MovementPage page = MovementPage.slice(List.of(), new PageRequest(0, 20));

        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
    }
}
