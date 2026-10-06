package com.bank.report.application.port.out;

import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.ReportMovement;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;

/**
 * Movimientos COMPLETED de un producto. P2: REST a transaction-service. P3: read model. El orden
 * no es parte del contrato del puerto: los casos de uso ordenan con {@code MOST_RECENT_FIRST}.
 */
public interface MovementQueryPort {

    /** Cuántos hay en el intervalo, para comprobar el tope antes de traerlos (data-model 2.5). */
    Single<Long> countInRange(String productId, DateRange range);

    /** Todos los del intervalo. */
    Flowable<ReportMovement> findInRange(String productId, DateRange range);

    /** El más reciente hasta {@code day} inclusive (saldo inicial); vacío si no hay ninguno. */
    Maybe<ReportMovement> findLastUpTo(String productId, LocalDate day);

    /** Los {@code limit} más recientes. */
    Flowable<ReportMovement> findLast(String productId, int limit);
}
