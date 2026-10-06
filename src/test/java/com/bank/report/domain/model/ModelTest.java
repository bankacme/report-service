package com.bank.report.domain.model;

import static com.bank.report.domain.Movements.completed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void moneyIsPenWithTwoDecimalsAndNeverNegative() {
        assertThat(Money.of("2").amount()).hasToString("2.00");
        assertThat(Money.of("1.10").plus(Money.of("2.20"))).isEqualTo(Money.of("3.30"));
        assertThatThrownBy(() -> Money.of("-1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, "USD")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Money(null, "PEN")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theOrderIsMostRecentFirstThenByMovementId() {
        ReportMovement older = completed("m1", MovementType.DEPOSIT, "1.00", null, "2026-09-01T10:00:00Z");
        ReportMovement withdrawal = completed("m2", MovementType.WITHDRAWAL, "1.00", null, "2026-09-02T10:00:00Z");
        ReportMovement fee = completed("m3", MovementType.FEE, "2.00", null, "2026-09-02T10:00:00Z");

        assertThat(List.of(older, withdrawal, fee).stream().sorted(ReportMovement.MOST_RECENT_FIRST))
                .extracting(ReportMovement::movementId)
                .containsExactly("m3", "m2", "m1");
    }

    @Test
    void anAccountNumberIsMaskedToItsLastFourDigits() {
        assertThat(ProductAttributes.mask("19412345674871")).isEqualTo("**** 4871");
        assertThat(ProductAttributes.mask("12")).isEqualTo("12");
        assertThat(ProductAttributes.mask(null)).isNull();
    }

    @Test
    void aProductNeedsItsIdentityAndGetsEmptyAttributesByDefault() {
        ReportProduct product = new ReportProduct("acc-1", ProductType.ACCOUNT, ProductCategory.SAVINGS, "cust-A",
                ProductStatus.ACTIVE, null, Instant.parse("2026-09-01T00:00:00Z"));

        assertThat(product.attributes().linkedAccountIds()).isEmpty();
        assertThatThrownBy(() -> new ReportProduct(" ", ProductType.ACCOUNT, ProductCategory.SAVINGS, "cust-A",
                ProductStatus.ACTIVE, null, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aMovementNeedsItsRequiredFields() {
        assertThatThrownBy(() -> new ReportMovement("m1", null, "acc-1", ProductType.ACCOUNT, "cust-A", null,
                Money.of("1"), null, MovementStatus.COMPLETED, null, null, null, null, null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
