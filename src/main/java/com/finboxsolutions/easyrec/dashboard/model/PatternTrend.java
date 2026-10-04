package com.finboxsolutions.easyrec.dashboard.model;

/**
 * How a pattern moved from one run to the next.
 *
 * <p>Derived when read and never stored, as ER_DASHBOARD_PATTERN_STAT intends: it depends on
 * which run is the previous one, and on a tolerance that should stay a setting rather than be
 * frozen into the data. See {@code PatternService.trendOf}.
 */
public enum PatternTrend {

    /** First run the pattern was detected on. */
    NEW("New", true),
    /** Detected again after one or more runs where it was not. */
    REAPPEARED("Reappeared", true),
    INCREASING("Increasing", true),
    STABLE("Stable", false),
    DECREASING("Decreasing", false),
    /** Not detected on this run, after being detected on the previous one. */
    RESOLVED("Resolved", false),
    /** Not detected on this run, nor on the previous one. */
    ABSENT("Absent", false);

    private final String label;
    private final boolean needsAttention;

    PatternTrend(String label, boolean needsAttention) {
        this.label = label;
        this.needsAttention = needsAttention;
    }

    public String label() {
        return label;
    }

    /** True for the movements an operator should look at: something appeared or grew. */
    public boolean needsAttention() {
        return needsAttention;
    }

    /** True when the pattern was detected on the run. */
    public boolean isPresent() {
        return this != RESOLVED && this != ABSENT;
    }

    /** The label, since the table cells and their filter menu show the value as text. */
    @Override
    public String toString() {
        return label;
    }
}
