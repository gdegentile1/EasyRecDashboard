package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_STAT_ROWS: the row-level result of a single reconciliation,
 * identified by (RUN_ID, TEMPLATE_ID).
 *
 * <p>STATUS here uses {@link StatusScope#RECONCILIATION}, which is the opposite of the
 * mapping used by the batch and run tables.
 */
public record RowStats(
        int runId,
        Integer templateId,
        Integer statusCode,
        long rowsSource,
        long rowsTarget,
        long missingSource,
        long missingTarget,
        long unmatched,
        long matched,
        long forceMatched,
        long nbComments,
        String filter,
        String matchType,
        String pivotBreakdown) {

    public StatusLabel status() {
        return StatusScope.RECONCILIATION.labelOf(statusCode);
    }

    /** The larger of the source and target row counts: the denominator of every rate here. */
    public long totalRows() {
        return Math.max(rowsSource, rowsTarget);
    }

    public long breakCount() {
        return missingSource + missingTarget + unmatched;
    }

    /** Match rate as a percentage, or null when there is nothing to compare. */
    public Double matchRate() {
        long total = totalRows();
        return total == 0L ? null : matched * 100.0d / total;
    }

    /** {@code value} as a percentage of {@link #totalRows()}, so the tiles are comparable. */
    public Double shareOfRows(long value) {
        long total = totalRows();
        return total == 0L ? null : value * 100.0d / total;
    }
}
