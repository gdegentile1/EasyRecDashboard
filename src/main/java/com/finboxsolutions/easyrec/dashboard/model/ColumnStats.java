package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_STAT_COLS: the per-column result of a reconciliation,
 * identified by (RUN_ID, TEMPLATE_ID, COL_LABEL).
 */
public record ColumnStats(
        int runId,
        Integer templateId,
        String colLabel,
        String colClass,
        String colTolerance,
        Double colMatchPct,
        long colNbUnmatch,
        long colNbExactMatch,
        long colNbToleranceMatch,
        long colNbForceMatch,
        Double colImpact,
        Double colImpactAbs,
        Double colAverage,
        Double colStdDeviation,
        Double colMinDiffAbs,
        Double colMaxDiffAbs,
        Double colMinDiffPct,
        Double colMaxDiffPct) {

    /**
     * COL_MATCH_PCT as a percentage. The column stores a ratio, so it is scaled here for
     * display only; anything that aggregates or compares it keeps working on the ratio.
     */
    public Double matchPercentage() {
        return colMatchPct == null ? null : colMatchPct * 100.0d;
    }
}
