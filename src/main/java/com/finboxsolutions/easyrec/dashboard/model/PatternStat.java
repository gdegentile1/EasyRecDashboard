package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_PATTERN_STAT with the pattern it counts: how often a known pattern
 * occurred on one run, and the size of that run.
 *
 * <p>Every known pattern of a template gets a row on every run the export covered, so an
 * {@code occurrences} of zero is a pattern that was looked for and not detected - not a
 * pattern nobody looked for, and not a pattern that was fixed. A run the export skipped has
 * no row at all.
 *
 * @param rowsSource ROWS_SOURCE of the run on the pattern's template, or null when
 *                   the run has no row statistics there
 * @param rowsTarget ROWS_TARGET, likewise
 */
public record PatternStat(
        int runId,
        PatternRow pattern,
        long occurrences,
        boolean aboveThreshold,
        String templateCfgHash,
        Long rowsSource,
        Long rowsTarget) {

    public int patternId() {
        return pattern.patternId();
    }

    /**
     * The rows compared on the run: the larger side, the denominator every rate on the
     * dashboard uses. Null when the run has no row statistics on this template.
     */
    public Long rowsCompared() {
        if (rowsSource == null && rowsTarget == null) {
            return null;
        }
        return Math.max(rowsSource == null ? 0L : rowsSource, rowsTarget == null ? 0L : rowsTarget);
    }

    /** Occurrences as a percentage of the rows compared, or null when that is unknown or zero. */
    public Double rate() {
        Long rows = rowsCompared();
        return rows == null || rows == 0L ? null : occurrences * 100.0d / rows;
    }
}
