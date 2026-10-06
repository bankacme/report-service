package com.bank.report.application.usecase;

import com.bank.report.application.port.in.GetCustomerCardsReportUseCase;
import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.domain.model.CardMovements;
import com.bank.report.domain.model.CustomerCardsReport;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportProduct;
import com.bank.report.domain.service.LastMovementsSelector;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.util.List;

/**
 * Últimos 10 movimientos de cada tarjeta de crédito y de débito de un cliente. Sin 404: el servicio
 * no conoce clientes, así que uno sin tarjetas devuelve listas vacías. En P2 no hay tarjetas de
 * débito ({@code debitCardsIncluded = false}).
 */
public class GetCustomerCardsReportUseCaseImpl implements GetCustomerCardsReportUseCase {

    private final ProductQueryPort productQueryPort;
    private final MovementQueryPort movementQueryPort;
    private final LastMovementsSelector selector;
    private final Clock clock;

    public GetCustomerCardsReportUseCaseImpl(ProductQueryPort productQueryPort, MovementQueryPort movementQueryPort,
                                             LastMovementsSelector selector, Clock clock) {
        this.productQueryPort = productQueryPort;
        this.movementQueryPort = movementQueryPort;
        this.selector = selector;
        this.clock = clock;
    }

    @Override
    public Single<CustomerCardsReport> execute(String customerId) {
        return productQueryPort.findCardsByCustomer(customerId)
                // concatMap: conserva el orden de las tarjetas y no lanza N llamadas a la vez.
                .concatMapSingle(this::withLastMovements)
                .toList()
                .map(cards -> new CustomerCardsReport(customerId, productQueryPort.debitCardsAvailable(),
                        ofType(cards, ProductType.CREDIT_CARD), ofType(cards, ProductType.DEBIT_CARD),
                        clock.instant()));
    }

    private Single<CardMovements> withLastMovements(ReportProduct card) {
        int limit = LastMovementsSelector.DEFAULT_LIMIT;
        return movementQueryPort.findLast(card.productId(), limit).toList()
                .map(movements -> new CardMovements(card, selector.select(movements, limit)));
    }

    private static List<CardMovements> ofType(List<CardMovements> cards, ProductType type) {
        return cards.stream()
                .filter(card -> card.card().productType() == type)
                .toList();
    }
}
