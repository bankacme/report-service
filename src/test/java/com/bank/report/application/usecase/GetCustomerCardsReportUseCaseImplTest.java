package com.bank.report.application.usecase;

import static com.bank.report.application.usecase.Fixtures.CLOCK;
import static com.bank.report.application.usecase.Fixtures.creditCard;
import static com.bank.report.application.usecase.Fixtures.movement;
import static com.bank.report.application.usecase.Fixtures.savings;
import static org.assertj.core.api.Assertions.assertThat;

import com.bank.report.domain.model.CardMovements;
import com.bank.report.domain.model.CustomerCardsReport;
import com.bank.report.domain.model.MovementType;
import com.bank.report.domain.model.ProductStatus;
import com.bank.report.domain.service.LastMovementsSelector;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GetCustomerCardsReportUseCaseImplTest {

    private final InMemorySources sources = new InMemorySources()
            .add(savings("acc-A1", "cust-A"))
            .add(creditCard("cc-A1", "cust-A", ProductStatus.ACTIVE))
            .add(creditCard("cc-A2", "cust-A", ProductStatus.CLOSED))
            .add(creditCard("cc-B", "cust-B", ProductStatus.ACTIVE));

    private final GetCustomerCardsReportUseCaseImpl useCase = new GetCustomerCardsReportUseCaseImpl(sources, sources,
            new LastMovementsSelector(), CLOCK);

    @Test
    void listsEveryCreditCardOfTheCustomerWithItsLastTenMovements() {
        IntStream.rangeClosed(1, 12).forEach(i -> sources.add(movement("a" + i, "cc-A1", MovementType.CARD_CHARGE,
                "10.00", null, String.format("2026-09-%02dT15:00:00Z", i))));
        sources.add(movement("b1", "cc-B", MovementType.CARD_CHARGE, "10.00", null, "2026-09-01T15:00:00Z"));

        CustomerCardsReport report = useCase.execute("cust-A").blockingGet();

        assertThat(report.customerId()).isEqualTo("cust-A");
        // Cualquier estado (también la cerrada); nunca la cuenta de ahorro ni la tarjeta de otro cliente.
        assertThat(report.creditCards()).extracting(card -> card.card().productId())
                .containsExactly("cc-A1", "cc-A2");
        assertThat(report.creditCards().get(0).movements()).hasSize(10);
        assertThat(report.creditCards().get(1).movements()).isEmpty();
        assertThat(report.generatedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void inP2ThereAreNoDebitCards() {
        CustomerCardsReport report = useCase.execute("cust-A").blockingGet();

        assertThat(report.debitCardsIncluded()).isFalse();
        assertThat(report.debitCards()).isEmpty();
    }

    @Test
    void aCustomerWithoutCardsGetsEmptyListsNotA404() {
        CustomerCardsReport report = useCase.execute("cust-nobody").blockingGet();

        assertThat(report.creditCards()).isEmpty();
        assertThat(report.debitCards()).isEmpty();
    }

    @Test
    void reportsWhetherTheSourceHasDebitCards() {
        sources.withDebitCards();

        assertThat(useCase.execute("cust-A").blockingGet().debitCardsIncluded()).isTrue();
    }

    @Test
    void cardMovementsKeepsItsCard() {
        CardMovements card = useCase.execute("cust-A").blockingGet().creditCards().get(0);

        assertThat(card.card().attributes().maskedNumber()).isEqualTo("**** 1234");
    }
}
