package com.bank.report.application.port.in;

import com.bank.report.application.query.ProductReportQuery;
import com.bank.report.domain.model.ProductReport;
import io.reactivex.rxjava3.core.Single;

public interface GenerateProductReportUseCase {

    Single<ProductReport> execute(ProductReportQuery query);
}
