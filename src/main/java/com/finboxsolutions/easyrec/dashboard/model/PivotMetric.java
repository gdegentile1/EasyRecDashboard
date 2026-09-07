package com.finboxsolutions.easyrec.dashboard.model;

import java.util.List;

/**
 * The measures ER_DASHBOARD_PIVOT carries, and how each one behaves.
 *
 * <p>Holding these as an enum rather than as record components is what lets the pivot tree
 * fold a branch generically and lets the column chooser offer the same set the table
 * renders. {@link #derivedFrom()} marks the measures that are ratios rather than counts:
 * adding those up a tree is meaningless, so a parent recomputes them from its summed
 * numerator and denominator instead.
 */
public enum PivotMetric {

    TOTAL_BREAKS("Total Breaks", Tone.BAD, 0),
    UNMATCH_SUM_S("Sum SRC", Tone.PLAIN, 2),
    UNMATCH_SUM_T("Sum TGT", Tone.PLAIN, 2),
    UNMATCH_IMPACT("Unmatch Impact", Tone.SIGNED, 2),
    UNMATCH_IMPACT_ABS("Unmatch Impact (Abs)", Tone.PLAIN, 2),
    UNMATCH_IMPACT_PCT("Unmatch Impact Pct", Tone.SIGNED, 2),
    MATCH_COUNT("Matched", Tone.GOOD, 0),
    MISSING_SRC_COUNT("Missing Src", Tone.WARN, 0),
    MISSING_TRG_COUNT("Missing Trg", Tone.WARN, 0),
    UNMATCH_COUNT("Unmatched", Tone.BAD, 0),
    TOTAL_IMPACT_ABS("Impact (Abs)", Tone.PLAIN, 2),
    MATCH_SUM_S("Match Sum (Src)", Tone.PLAIN, 2),
    MATCH_SUM_T("Match Sum (Trg)", Tone.PLAIN, 2),
    MISSING_SRC_SUM_S("Missing Src Sum (Src)", Tone.PLAIN, 2),
    MISSING_SRC_SUM_T("Missing Src Sum (Trg)", Tone.PLAIN, 2),
    MISSING_SRC_IMPACT("Missing Src Impact", Tone.PLAIN, 2),
    MISSING_SRC_IMPACT_ABS("Missing Src Impact (Abs)", Tone.PLAIN, 2),
    MISSING_TRG_SUM_S("Missing Trg Sum (Src)", Tone.PLAIN, 2),
    MISSING_TRG_SUM_T("Missing Trg Sum (Trg)", Tone.PLAIN, 2),
    MISSING_TRG_IMPACT("Missing Trg Impact", Tone.PLAIN, 2),
    MISSING_TRG_IMPACT_ABS("Missing Trg Impact (Abs)", Tone.PLAIN, 2);

    /** How a cell is coloured. WARN and BAD colour only a non-zero value. */
    public enum Tone { GOOD, WARN, BAD, SIGNED, PLAIN }

    /** Shown when the viewer has not chosen a set of their own, in this order. */
    public static final List<PivotMetric> DEFAULT_COLUMNS = List.of(
            TOTAL_BREAKS, UNMATCH_SUM_S, UNMATCH_SUM_T,
            UNMATCH_IMPACT, UNMATCH_IMPACT_ABS, UNMATCH_IMPACT_PCT);

    /** Measures held as a ratio but read as a percentage. */
    public static final List<PivotMetric> PERCENT_COLUMNS = List.of(UNMATCH_IMPACT_PCT);

    private final String label;
    private final Tone tone;
    private final int decimals;

    PivotMetric(String label, Tone tone, int decimals) {
        this.label = label;
        this.tone = tone;
        this.decimals = decimals;
    }

    public String label() {
        return label;
    }

    public Tone tone() {
        return tone;
    }

    public int decimals() {
        return decimals;
    }

    /**
     * The (numerator, denominator) this measure is a ratio of, or null when it is a count.
     *
     * <p>Both operands are themselves measures, so a folding parent always has the sums it
     * needs already to hand.
     */
    public PivotMetric[] derivedFrom() {
        return this == UNMATCH_IMPACT_PCT
                ? new PivotMetric[] { UNMATCH_IMPACT, UNMATCH_SUM_S }
                : null;
    }
}
