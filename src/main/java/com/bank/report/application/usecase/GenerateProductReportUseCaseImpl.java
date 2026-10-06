package com.bank.report.application.usecase;

import com.bank.report.application.port.in.GenerateProductReportUseCase;
import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.application.query.ProductReportQuery;
import com.bank.report.application.query.ReportSettings;
import com.bank.report.domain.exception.ProductNotFoundException;
import com.bank.report.domain.exception.RangeTooLargeException;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.MovementPage;
import com.bank.report.domain.model.PageRequest;
import com.bank.report.domain.model.ProductReport;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportProduct;
import com.bank.report.domain.service.ProductReportCalculator;
import io.reactivex.rxjava3.core.Single;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Reporte completo de un producto (ficha 4.1, data-model 2.2 y 3.2):
 * <ol>
 *   <li>Valida el intervalo (400) y la página antes de llamar a nadie.</li>
 *   <li>Cabecera del producto (404 si no existe; uno cerrado también se reporta, regla 10).</li>
 *   <li>Cuenta los movimientos del intervalo y rechaza con 422 si pasan el tope (regla 11).</li>
 *   <li>Trae todos los del intervalo y el último anterior; el resumen es de todo el intervalo
 *       (regla 4) y la página es una porción de la misma lista ordenada.</li>
 * </ol>
 */
public class GenerateProductReportUseCaseImpl implements GenerateProductReportUseCase {

    private final ProductQueryPort productQueryPort;
    private final MovementQueryPort movementQueryPort;
    private final ProductReportCalculator calculator;
    private final ReportSettings settings;
    private final Clock clock;

    public GenerateProductReportUseCaseImpl(ProductQueryPort productQueryPort, MovementQueryPort movementQueryPort,
                                            ProductReportCalculator calculator, ReportSettings settings,
                                            Clock clock) {
        this.productQueryPort = productQueryPort;
        this.movementQueryPort = movementQueryPort;
        this.calculator = calculator;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    public Single<ProductReport> execute(ProductReportQuery query) {
        return Single.defer(() -> {
            DateRange range = DateRange.of(query.from(), query.to(), settings.maxRangeDays());
            PageRequest page = PageRequest.of(query.page(), query.size(), settings.maxPageSize());
            return productQueryPort.findById(query.productType(), query.productId())
                    .switchIfEmpty(Single.error(() ->
                            new ProductNotFoundException(query.productType(), query.productId())))
                    .flatMap(product -> report(product, range, page));
        });
    }

    private Single<ProductReport> report(ReportProduct product, DateRange range, PageRequest page) {
        String productId = product.productId();
        return movementQueryPort.countInRange(productId, range)
                .flatMap(count -> count > settings.maxMovements()
                        ? Single.error(new RangeTooLargeException(count, settings.maxMovements()))
                        : Single.zip(
                                movementQueryPort.findInRange(productId, range).toList(),
                                movementQueryPort.findLastUpTo(productId, range.dayBefore())
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty()),
                                (movements, previous) -> build(product, range, page, movements, previous)));
    }

    private ProductReport build(ReportProduct product, DateRange range, PageRequest page,
                                List<ReportMovement> movements, Optional<ReportMovement> previous) {
        List<ReportMovement> sorted = movements.stream()
                .filter(ReportMovement::isCompleted)
                .sorted(ReportMovement.MOST_RECENT_FIRST)
                .toList();
        return new ProductReport(product, range, calculator.summarize(sorted, previous),
                MovementPage.slice(sorted, page), clock.instant());
    }
}
