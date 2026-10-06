package com.bank.report.domain.model;

import java.util.List;

/** Una página de movimientos ya ordenados (más reciente primero). */
public record MovementPage(int page, int size, long totalElements, int totalPages, List<ReportMovement> content) {

    public MovementPage {
        content = List.copyOf(content);
    }

    /**
     * Porción en memoria de la lista completa ya ordenada (data-model 4.4): el resumen ya necesita
     * todos los movimientos del intervalo, así que la página sale de ellos sin otra consulta.
     */
    public static MovementPage slice(List<ReportMovement> sorted, PageRequest request) {
        long total = sorted.size();
        int totalPages = (int) ((total + request.size() - 1) / request.size());
        List<ReportMovement> content = sorted.stream()
                .skip(request.offset())
                .limit(request.size())
                .toList();
        return new MovementPage(request.page(), request.size(), total, totalPages, content);
    }
}
