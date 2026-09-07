package com.finboxsolutions.easyrec.dashboard.model;

import java.time.LocalDate;

/**
 * One row of ER_DASHBOARD_RUN_CONTEXT: the operator-facing metadata of a single
 * reconciliation, identified by (RUN_ID, TEMPLATE_ID).
 *
 * <p>The table has no primary key. Any write must therefore be constrained on BOTH
 * columns; updating on RUN_ID alone rewrites every context row of that run.
 */
public record RunContextRow(
        int runId,
        Integer position,
        Integer templateId,
        String sourceAlias,
        String targetAlias,
        String sourceLabel,
        String targetLabel,
        String name,
        String category1,
        String category2,
        String category3,
        String priority,
        String userName,
        String userEmail,
        String groupName,
        String groupEmail,
        Integer statusCode,
        LocalDate dueDate,
        String description) {

    public StatusLabel status() {
        return StatusScope.RECONCILIATION.labelOf(statusCode);
    }
}
