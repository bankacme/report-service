package com.bank.report.infrastructure.adapter.in.rest;

import com.bank.report.application.port.in.GenerateProductReportUseCase;
import com.bank.report.application.port.in.GetCustomerCardsReportUseCase;
import com.bank.report.application.port.in.GetLastMovementsUseCase;
import com.bank.report.application.query.LastMovementsQuery;
import com.bank.report.application.query.ProductReportQuery;
import com.bank.report.domain.exception.NotAvailableException;
import com.bank.report.infrastructure.adapter.in.rest.api.ReportsApi;
import com.bank.report.infrastructure.adapter.in.rest.dto.CategoryReport;
import com.bank.report.infrastructure.adapter.in.rest.dto.CustomerCardsReport;
import com.bank.report.infrastructure.adapter.in.rest.dto.LastMovementsReport;
import com.bank.report.infrastructure.adapter.in.rest.dto.ProductCategory;
import com.bank.report.infrastructure.adapter.in.rest.dto.ProductReport;
import com.bank.report.infrastructure.mapper.ReportRestMapper;
import com.bank.report.infrastructure.support.RxJavaReactorBridge;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Los cuatro endpoints de {@code /reports}. Solo lectura: todos responden 200 o un error estándar. */
@RestController
public class ReportController implements ReportsApi {

    private final GenerateProductReportUseCase generateProductReportUseCase;
    private final GetLastMovementsUseCase getLastMovementsUseCase;
    private final GetCustomerCardsReportUseCase getCustomerCardsReportUseCase;
    private final ReportRestMapper mapper;

    public ReportController(GenerateProductReportUseCase generateProductReportUseCase,
                            GetLastMovementsUseCase getLastMovementsUseCase,
                            GetCustomerCardsReportUseCase getCustomerCardsReportUseCase,
                            ReportRestMapper mapper) {
        this.generateProductReportUseCase = generateProductReportUseCase;
        this.getLastMovementsUseCase = getLastMovementsUseCase;
        this.getCustomerCardsReportUseCase = getCustomerCardsReportUseCase;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<ProductReport>> getProductReport(String productType, String productId,
                                                                LocalDate from, LocalDate to, Integer page,
                                                                Integer size, ServerWebExchange exchange) {
        return Mono.defer(() -> {
            ProductReportQuery query = new ProductReportQuery(mapper.toProductType(productType), productId, from, to,
                    page, size);
            return RxJavaReactorBridge.toMono(generateProductReportUseCase.execute(query));
        }).map(report -> ResponseEntity.ok(mapper.toDto(report)));
    }

    @Override
    public Mono<ResponseEntity<LastMovementsReport>> getLastMovements(String productType, String productId,
                                                                      Integer limit, ServerWebExchange exchange) {
        return Mono.defer(() -> {
            LastMovementsQuery query = new LastMovementsQuery(mapper.toProductType(productType), productId, limit);
            return RxJavaReactorBridge.toMono(getLastMovementsUseCase.execute(query));
        }).map(report -> ResponseEntity.ok(mapper.toDto(report)));
    }

    @Override
    public Mono<ResponseEntity<CustomerCardsReport>> getCustomerCardsLastMovements(String customerId,
                                                                                    ServerWebExchange exchange) {
        return RxJavaReactorBridge.toMono(getCustomerCardsReportUseCase.execute(customerId))
                .map(report -> ResponseEntity.ok(mapper.toDto(report)));
    }

    /** Desde P3, con el read model (data-model 2.4): en P2 responde 501 NOT_AVAILABLE. */
    @Override
    public Mono<ResponseEntity<CategoryReport>> getCategoryReport(ProductCategory category, LocalDate from,
                                                                  LocalDate to, ServerWebExchange exchange) {
        return Mono.error(new NotAvailableException("The report by product category is available from P3 "
                + "(it needs the read model)"));
    }
}
