package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_PATTERN_STAT with the pattern it counts: how often a known pattern
 * occurred on one run.
 *
 * <p>Every known pattern of a template gets a row on every run the export covered, so an
 * {@code occurrences} of zero is a pattern that was looked for and not detected - not a
 * pattern nobody looked for. A run the export skipped has no row at all.
 */
public record PatternStat(
        int runId,
        PatternRow pattern,
        long occurrences,
        boolean aboveThreshold,
        String templateCfgHash) {

    public int patternId() {
        return pattern.patternId();
    }
}
