package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_RUN: a reconciliation run inside a batch.
 *
 * <p>TEMPLATE_ID here is the project's main template, a container with no statistics of its
 * own: it names what the batch ran, and is never the id to read a reconciliation's
 * statistics, pivot or patterns with. Those come from the context row or ER_DASHBOARD_STAT_ROWS,
 * see {@code TemplateIds}.
 *
 * <p>PROJECT_PATH is the run's own, and is not the same thing as the template path beside
 * it: the template path names the file that was reconciled, this names the project it was
 * run from. A batch of twenty templates out of one project carries one project path and
 * twenty template paths.
 */
public record RunRow(
        int runId,
        int batchId,
        String recType,
        Integer templateId,
        String projectPath,
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
