package com.bank.report.application.usecase;

import com.bank.report.application.port.out.MovementQueryPort;
import com.bank.report.application.port.out.ProductQueryPort;
import com.bank.report.domain.model.DateRange;
import com.bank.report.domain.model.ProductType;
import com.bank.report.domain.model.ReportMovement;
import com.bank.report.domain.model.ReportProduct;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Productos y movimientos en memoria; registra qué se pidió para comprobar el orden de las llamadas. */
final class InMemorySources implements ProductQueryPort, MovementQueryPort {

    static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final Map<String, ReportProduct> products = new LinkedHashMap<>();
    private final List<ReportMovement> movements = new ArrayList<>();
    private final List<String> calls = new ArrayList<>();
    private boolean debitCardsAvailable;

    InMemorySources add(ReportProduct product) {
        products.put(product.productId(), product);
        return this;
    }

    InMemorySources add(ReportMovement... newMovements) {
        movements.addAll(List.of(newMovements));
        return this;
    }

    InMemorySources withDebitCards() {
        debitCardsAvailable = true;
        return this;
    }

    List<String> calls() {
        return calls;
    }

    @Override
    public Maybe<ReportProduct> findById(ProductType productType, String productId) {
        calls.add("product:" + productId);
        ReportProduct product = products.get(productId);
        return product == null || product.productType() != productType ? Maybe.empty() : Maybe.just(product);
    }

    @Override
    public Flowable<ReportProduct> findCardsByCustomer(String customerId) {
        return Flowable.fromIterable(products.values())
                .filter(product -> product.customerId().equals(customerId))
                .filter(product -> product.productType() == ProductType.CREDIT_CARD
                        || product.productType() == ProductType.DEBIT_CARD);
    }

    @Override
    public boolean debitCardsAvailable() {
        return debitCardsAvailable;
    }

    @Override
    public Single<Long> countInRange(String productId, DateRange range) {
        calls.add("count:" + productId);
        return inRange(productId, range).count();
    }

    @Override
    public Flowable<ReportMovement> findInRange(String productId, DateRange range) {
        calls.add("range:" + productId);
        return inRange(productId, range);
    }

    @Override
    public Maybe<ReportMovement> findLastUpTo(String productId, LocalDate day) {
        calls.add("previous:" + productId);
        return of(productId)
                .filter(movement -> movement.occurredAt().isBefore(day.plusDays(1).atStartOfDay(LIMA).toInstant()))
                .sorted(ReportMovement.MOST_RECENT_FIRST)
                .firstElement();
    }

    @Override
    public Flowable<ReportMovement> findLast(String productId, int limit) {
        calls.add("last:" + productId);
        return of(productId).sorted(ReportMovement.MOST_RECENT_FIRST).take(limit);
    }

    private Flowable<ReportMovement> inRange(String productId, DateRange range) {
        return of(productId).filter(movement -> range.contains(movement.occurredAt(), LIMA));
    }

    private Flowable<ReportMovement> of(String productId) {
        return Flowable.fromIterable(List.copyOf(movements))
                .filter(movement -> movement.productId().equals(productId))
                .filter(ReportMovement::isCompleted);
    }
}
