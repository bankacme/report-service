package com.bank.report.domain.service;

import com.bank.report.domain.model.ReportMovement;
import java.util.Collection;
import java.util.List;

/** Los N más recientes (regla 7, data-model 2.3); si hay menos de N, los que haya. */
public class LastMovementsSelector {

    public static final int DEFAULT_LIMIT = 10;

    public List<ReportMovement> select(Collection<ReportMovement> movements, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        return movements.stream()
                .filter(ReportMovement::isCompleted)
                .sorted(ReportMovement.MOST_RECENT_FIRST)
                .limit(limit)
                .toList();
    }
}
