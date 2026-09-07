package com.finboxsolutions.easyrec.dashboard.model;

/**
 * The three outcomes EasyRec records, independent of the numeric code used to store them.
 *
 * <p>{@link #UNKNOWN} carries the raw code so an unmapped value can still be displayed
 * verbatim rather than being silently swallowed.
 */
public enum StatusLabel {

    PASSED,
    FAILED,
    /** The execution itself errored: not one side of the pass/fail pair. */
    ERROR,
    UNKNOWN;

    public boolean isPassed() {
        return this == PASSED;
    }

    public boolean isFailed() {
        return this == FAILED;
    }
}
