package com.finboxsolutions.easyrec.dashboard.service;

/**
 * The one definition of "match rate" the whole dashboard uses.
 *
 * <p>Every screen has to divide by the same thing or the numbers stop agreeing across
 * drill-downs, so the denominator is fixed here: the larger of the source and target row
 * counts. Counts are summed first and divided once - averaging per-reconciliation rates
 * would weight a two-row template the same as a two-million-row one.
 */
public final class Rates {

    /** Above this a rate reads as healthy. */
    public static final double GOOD_THRESHOLD = 95.0d;

    /** Below {@link #GOOD_THRESHOLD} and above this a rate reads as a warning. */
    public static final double WARN_THRESHOLD = 80.0d;

    /** The colour band a rate falls in. */
    public enum Tone { GOOD, WARN, BAD, NONE }

    private Rates() {
    }

    /** Match rate as a percentage, or null when there is nothing to compare. */
    public static Double matchRate(long matched, long rowsSource, long rowsTarget) {
        long total = Math.max(rowsSource, rowsTarget);
        return total == 0L ? null : matched * 100.0d / total;
    }

    public static Tone toneOf(Double rate) {
        if (rate == null) {
            return Tone.NONE;
        }
        if (rate >= GOOD_THRESHOLD) {
            return Tone.GOOD;
        }
        return rate >= WARN_THRESHOLD ? Tone.WARN : Tone.BAD;
    }

    /**
     * An impact as a proportion of the absolute base it is measured against.
     *
     * <p>Mirrors how EasyRec fills UNMATCH_IMPACT_PCT - impact over abs(UNMATCH_SUM_S),
     * falling back to the sign of the impact when there is no base to divide by - so a
     * folded branch total reads on the same scale as the stored leaves beneath it.
     */
    public static double impactRatio(double impact, double base) {
        double denominator = Math.abs(base);
        if (denominator == 0.0d) {
            if (impact == 0.0d) {
                return 0.0d;
            }
            return impact > 0.0d ? 1.0d : -1.0d;
        }
        return impact / denominator;
    }
}
