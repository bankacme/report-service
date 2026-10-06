package com.bank.report.application.port.in;

import com.bank.report.application.query.LastMovementsQuery;
import com.bank.report.domain.model.LastMovementsReport;
import io.reactivex.rxjava3.core.Single;

public interface GetLastMovementsUseCase {

    Single<LastMovementsReport> execute(LastMovementsQuery query);
}
