package com.bank.report.infrastructure.config;

import com.bank.report.application.port.in.GenerateProductReportUseCase;
import com.bank.report.application.port.in.GetCustomerCardsReportUseCase;
import com.bank.report.application.port.in.GetLastMovementsUseCase;
import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.application.query.ReportSettings;
import com.bank.report.application.usecase.GenerateProductReportUseCaseImpl;
import com.bank.report.application.usecase.GetCustomerCardsReportUseCaseImpl;
import com.bank.report.application.usecase.GetLastMovementsUseCaseImpl;
import com.bank.report.domain.service.LastMovementsSelector;
import com.bank.report.domain.service.ProductReportCalculator;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Un @Bean por caso de uso: las clases de application y domain no llevan anotaciones de Spring,
 * igual que en los demás servicios. Los límites vienen de {@code report.*} (data-model §7).
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public ReportSettings reportSettings(@Value("${report.max-range-days:366}") int maxRangeDays,
                                         @Value("${report.max-movements:5000}") int maxMovements,
                                         @Value("${report.page.max-size:100}") int maxPageSize,
                                         @Value("${report.last-movements.max:50}") int maxLastMovements) {
        return new ReportSettings(maxRangeDays, maxMovements, maxPageSize, maxLastMovements);
    }

    @Bean
    public ProductReportCalculator productReportCalculator() {
        return new ProductReportCalculator();
    }

    @Bean
    public LastMovementsSelector lastMovementsSelector() {
        return new LastMovementsSelector();
    }

    @Bean
    public GenerateProductReportUseCase generateProductReportUseCase(ProductQueryPort productQueryPort,
                                                                     MovementQueryPort movementQueryPort,
                                                                     ProductReportCalculator calculator,
                                                                     ReportSettings settings, Clock clock) {
        return new GenerateProductReportUseCaseImpl(productQueryPort, movementQueryPort, calculator, settings, clock);
    }

    @Bean
    public GetLastMovementsUseCase getLastMovementsUseCase(ProductQueryPort productQueryPort,
                                                           MovementQueryPort movementQueryPort,
                                                           LastMovementsSelector selector, ReportSettings settings,
                                                           Clock clock) {
        return new GetLastMovementsUseCaseImpl(productQueryPort, movementQueryPort, selector, settings, clock);
    }

    @Bean
    public GetCustomerCardsReportUseCase getCustomerCardsReportUseCase(ProductQueryPort productQueryPort,
                                                                       MovementQueryPort movementQueryPort,
                                                                       LastMovementsSelector selector,
                                                                       Clock clock) {
        return new GetCustomerCardsReportUseCaseImpl(productQueryPort, movementQueryPort, selector, clock);
    }
}
