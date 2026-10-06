package com.bank.report.application.usecase;

import static com.bank.report.application.usecase.Fixtures.CLOCK;
import static com.bank.report.application.usecase.Fixtures.creditCard;
import static com.bank.report.application.usecase.Fixtures.movement;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.application.query.LastMovementsQuery;
import com.bank.report.application.query.ReportSettings;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.model.LastMovementsReport;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.service.LastMovementsSelector;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GetLastMovementsUseCaseImplTest {

    private final InMemorySources sources = new InMemorySources().add(creditCard("cc-A", "cust-A",
            ProductStatus.ACTIVE));

    private final GetLastMovementsUseCaseImpl useCase = new GetLastMovementsUseCaseImpl(sources, sources,
            new LastMovementsSelector(), ReportSettings.defaults(), CLOCK);

    private void twelveCharges() {
        IntStream.rangeClosed(1, 12).forEach(i -> sources.add(movement(String.format("m%02d", i), "cc-A",
                MovementType.CARD_CHARGE, "10.00", null, String.format("2026-09-%02dT15:00:00Z", i))));
    }

    @Test
    void returnsTheTenMostRecentByDefault() {
        twelveCharges();

        LastMovementsReport report = useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "cc-A", null))
                .blockingGet();

        assertThat(report.limit()).isEqualTo(10);
        assertThat(report.movements()).hasSize(10);
        assertThat(report.movements().get(0).movementId()).isEqualTo("m12");
        assertThat(report.product().productId()).isEqualTo("cc-A");
    }

    @Test
    void honoursAnExplicitLimit() {
        twelveCharges();

        assertThat(useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "cc-A", 3)).blockingGet()
                .movements()).hasSize(3);
    }

    @Test
    void aLimitOutOfRangeIsRejectedBeforeCallingAnySource() {
        useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "cc-A", 51)).test()
                .assertError(IllegalArgumentException.class);
        useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "cc-A", 0)).test()
                .assertError(IllegalArgumentException.class);

        assertThat(sources.calls()).isEmpty();
    }

    @Test
    void anUnknownProductIs404() {
        useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "ghost", null)).test()
                .assertError(ProductNotFoundException.class);
    }

    @Test
    void aProductWithoutMovementsHasAnEmptyList() {
        assertThat(useCase.execute(new LastMovementsQuery(ProductType.CREDIT_CARD, "cc-A", null)).blockingGet()
                .movements()).isEmpty();
    }
}
