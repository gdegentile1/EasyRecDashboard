package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_RUN: a reconciliation run inside a batch.
 *
 * <p>TEMPLATE_ID is held as a plain id rather than as a resolved template, for the same
 * reason {@link RunContextRow} does: ER_DASHBOARD_TEMPLATE holds two rows per
 * reconciliation and the caller has to choose between them, see
 * {@code TemplateIds#resolveStatsTemplateId}.
 */
public record RunRow(
        int runId,
        int batchId,
        String recType,
        Integer templateId,
        String sourceAlias,
        String targetAlias,
        String sourceLabel,
        String targetLabel,
        Integer statusCode,
        Long epochMillis,
        Long durationMillis,
        String description,
        int purgeStatus) {

    public StatusLabel status() {
        return StatusScope.EXECUTION.labelOf(statusCode);
    }
}
