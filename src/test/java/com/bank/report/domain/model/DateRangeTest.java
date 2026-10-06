package com.bank.report.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bank.report.domain.exception.InvalidRangeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Test
    void fromAfterToIsInvalid() {
        assertThatThrownBy(() -> DateRange.of(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 9, 1), 366))
                .isInstanceOf(InvalidRangeException.class);
    }

    @Test
    void aSingleDayIsValid() {
        DateRange range = DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), 366);

        assertThat(range.from()).isEqualTo(range.to());
    }

    @Test
    void exactly366DaysIsValidAnd367IsNot() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        assertThat(DateRange.of(from, from.plusDays(365), 366).to()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThatThrownBy(() -> DateRange.of(from, from.plusDays(366), 366))
                .isInstanceOf(InvalidRangeException.class)
                .hasMessageContaining("367");
    }

    @Test
    void missingDatesAreInvalid() {
        assertThatThrownBy(() -> DateRange.of(null, LocalDate.of(2026, 9, 1), 366))
                .isInstanceOf(InvalidRangeException.class);
    }

    @Test
    void isTheHalfOpenIntervalOfWholeDaysInTheBankZone() {
        DateRange september = DateRange.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 366);

        // Lima es UTC-5: el 1 de setiembre empieza a las 05:00Z.
        assertThat(september.startInclusive(LIMA)).isEqualTo(Instant.parse("2026-09-01T05:00:00Z"));
        assertThat(september.endExclusive(LIMA)).isEqualTo(Instant.parse("2026-10-01T05:00:00Z"));
        assertThat(september.contains(Instant.parse("2026-09-01T05:00:00Z"), LIMA)).isTrue();
        assertThat(september.contains(Instant.parse("2026-10-01T04:59:59Z"), LIMA)).isTrue();
        assertThat(september.contains(Instant.parse("2026-10-01T05:00:00Z"), LIMA)).isFalse();
        assertThat(september.dayBefore()).isEqualTo(LocalDate.of(2026, 8, 31));
    }
}
