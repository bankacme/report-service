package com.bank.report.application.port.in;

import com.bank.report.domain.model.CustomerCardsReport;
import io.reactivex.rxjava3.core.Single;

public interface GetCustomerCardsReportUseCase {

    Single<CustomerCardsReport> execute(String customerId);
}
