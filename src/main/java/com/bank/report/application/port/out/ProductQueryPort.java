package com.bank.report.application.port.out;

import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportProduct;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Cabecera de los productos. P2: REST a account-service y credit-service. P3: read model. Un tipo
 * que la fuente todavía no tiene (tarjeta de débito en P2) falla con {@code NotAvailableException}.
 */
public interface ProductQueryPort {

    /** Vacío si el servicio dueño no conoce el producto. */
    Maybe<ReportProduct> findById(ProductType productType, String productId);

    /** Tarjetas de crédito (y de débito si la fuente las tiene) del cliente, en cualquier estado. */
    Flowable<ReportProduct> findCardsByCustomer(String customerId);

    /** {@code false} en P2: no existen tarjetas de débito todavía. */
    boolean debitCardsAvailable();
}
