package com.bank.report.application.usecase;

import com.bank.report.application.port.in.GetLastMovementsUseCase;
import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.application.query.LastMovementsQuery;
import com.bank.report.application.query.ReportSettings;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.model.LastMovementsReport;
import com.bank.report.domain.service.LastMovementsSelector;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;

/** Últimos N movimientos de un producto (regla 7): 10 por defecto, entre 1 y 50. */
public class GetLastMovementsUseCaseImpl implements GetLastMovementsUseCase {

    private final ProductQueryPort productQueryPort;
    private final MovementQueryPort movementQueryPort;
    private final LastMovementsSelector selector;
    private final ReportSettings settings;
    private final Clock clock;

    public GetLastMovementsUseCaseImpl(ProductQueryPort productQueryPort, MovementQueryPort movementQueryPort,
                                       LastMovementsSelector selector, ReportSettings settings, Clock clock) {
        this.productQueryPort = productQueryPort;
        this.movementQueryPort = movementQueryPort;
        this.selector = selector;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Single<LastMovementsReport> execute(LastMovementsQuery query) {
        return Single.defer(() -> {
            int limit = resolveLimit(query.limit());
            return productQueryPort.findById(query.productType(), query.productId())
                    .switchIfEmpty(Single.error(() ->
                            new ProductNotFoundException(query.productType(), query.productId())))
                    .flatMap(product -> movementQueryPort.findLast(product.productId(), limit).toList()
                            .map(movements -> new LastMovementsReport(product, limit,
                                    selector.select(movements, limit), clock.instant())));
        });
    }

    private int resolveLimit(Integer requested) {
        int limit = requested == null ? LastMovementsSelector.DEFAULT_LIMIT : requested;
        if (limit < 1 || limit > settings.maxLastMovements()) {
            throw new IllegalArgumentException("limit must be between 1 and " + settings.maxLastMovements());
        }
        return limit;
    }
}
