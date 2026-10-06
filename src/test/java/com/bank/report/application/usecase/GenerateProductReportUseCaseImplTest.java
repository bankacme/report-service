package com.bank.report.application.usecase;

import static com.bank.report.application.usecase.Fixtures.CLOCK;
import static com.bank.report.application.usecase.Fixtures.movement;
import static com.bank.report.application.usecase.Fixtures.savings;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.application.query.ProductReportQuery;
import com.bank.report.application.query.ReportSettings;
import com.bank.report.domain.exception.InvalidRangeException;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.exception.RangeTooLargeException;
import com.bank.report.domain.model.Money;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductReport;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.service.ProductReportCalculator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class GenerateProductReportUseCaseImplTest {

    private static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate SEP_30 = LocalDate.of(2026, 9, 30);

    private final InMemorySources sources = new InMemorySources()
            .add(savings("acc-A1", "cust-A"))
            .add(movement("m0", "acc-A1", MovementType.DEPOSIT, "1000.00", "1000.00", "2026-08-20T15:00:00Z"),
                    movement("m1", "acc-A1", MovementType.DEPOSIT, "250.00", "1250.00", "2026-09-03T14:00:00Z"),
                    movement("m2", "acc-A1", MovementType.WITHDRAWAL, "150.00", "1100.00", "2026-09-05T14:00:00Z"),
                    movement("m3", "acc-A1", MovementType.WITHDRAWAL, "98.00", "1000.00", "2026-09-24T15:24:00Z"),
                    movement("m4", "acc-A1", MovementType.FEE, "2.00", "1000.00", "2026-09-24T15:24:00Z"),
                    movement("m5", "acc-A1", MovementType.DEPOSIT, "10.00", "1010.00", "2026-10-02T15:00:00Z"));

    private GenerateProductReportUseCaseImpl useCase(ReportSettings settings) {
        return new GenerateProductReportUseCaseImpl(sources, sources, new ProductReportCalculator(), settings, CLOCK);
    }

    private GenerateProductReportUseCaseImpl useCase() {
        return useCase(ReportSettings.defaults());
    }

    private ProductReportQuery september(Integer page, Integer size) {
        return new ProductReportQuery(ProductType.ACCOUNT, "acc-A1", SEP_1, SEP_30, page, size);
    }

    @Test
    void buildsTheHeaderSummaryAndFirstPage() {
        ProductReport report = useCase().execute(september(0, 2)).blockingGet();

        assertThat(report.product().productId()).isEqualTo("acc-A1");
        assertThat(report.period().from()).isEqualTo(SEP_1);
        assertThat(report.summary().movementsCount()).isEqualTo(4);
        assertThat(report.summary().totalFees()).isEqualTo(Money.of("2.00"));
        assertThat(report.summary().openingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(report.summary().closingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(report.movements().totalElements()).isEqualTo(4);
        assertThat(report.movements().totalPages()).isEqualTo(2);
        assertThat(report.movements().content()).extracting(ReportMovement::movementId).containsExactly("m4", "m3");
        assertThat(report.generatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void theSummaryCoversTheWholeRangeWhateverThePage() {
        ProductReport secondPage = useCase().execute(september(1, 2)).blockingGet();

        assertThat(secondPage.movements().content()).extracting(ReportMovement::movementId)
                .containsExactly("m2", "m1");
        assertThat(secondPage.summary().movementsCount()).isEqualTo(4);
    }

    @Test
    void defaultsToTheFirstPageOf20() {
        ProductReport report = useCase().execute(september(null, null)).blockingGet();

        assertThat(report.movements().size()).isEqualTo(20);
        assertThat(report.movements().content()).hasSize(4);
    }

    @Test
    void anInvalidRangeFailsBeforeCallingAnySource() {
        useCase().execute(new ProductReportQuery(ProductType.ACCOUNT, "acc-A1", SEP_30, SEP_1, null, null))
                .test().assertError(InvalidRangeException.class);

        assertThat(sources.calls()).isEmpty();
    }

    @Test
    void anUnknownProductIs404() {
        useCase().execute(new ProductReportQuery(ProductType.ACCOUNT, "ghost", SEP_1, SEP_30, null, null))
                .test().assertError(ProductNotFoundException.class);
    }

    @Test
    void theProductTypeMustMatch() {
        // acc-A1 es una cuenta: pedirlo como tarjeta de crédito no lo encuentra.
        useCase().execute(new ProductReportQuery(ProductType.CREDIT_CARD, "acc-A1", SEP_1, SEP_30, null, null))
                .test().assertError(ProductNotFoundException.class);
    }

    @Test
    void tooManyMovementsIs422WithoutFetchingThem() {
        useCase(new ReportSettings(366, 3, 100, 50)).execute(september(null, null))
                .test().assertError(RangeTooLargeException.class);

        assertThat(sources.calls()).containsExactly("product:acc-A1", "count:acc-A1");
    }

    @Test
    void aRangeWithoutMovementsStillHasAnOpeningBalance() {
        ProductReport report = useCase().execute(new ProductReportQuery(ProductType.ACCOUNT, "acc-A1",
                LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 31), null, null)).blockingGet();

        assertThat(report.summary().movementsCount()).isZero();
        assertThat(report.summary().openingBalance()).isEqualTo(Money.of("1000.00"));
        assertThat(report.summary().closingBalance()).isNull();
        assertThat(report.movements().content()).isEmpty();
    }
}
