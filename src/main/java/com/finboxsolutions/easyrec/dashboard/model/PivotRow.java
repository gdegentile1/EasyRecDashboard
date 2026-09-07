package com.finboxsolutions.easyrec.dashboard.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One row of ER_DASHBOARD_PIVOT: the metrics of a single pivot key combination.
 *
 * <p>The key components are already split across PIVOT_KEY0..9 and PIVOT_LEVEL is the
 * 0-based index of the deepest populated one. Reading those columns rather than splitting
 * PIVOT_KEYS on its separator keeps a key that itself contains a separator from inventing
 * a tree level; the joined string is only a fallback.
 */
public record PivotRow(
        int runId,
        Integer templateId,
        String pivotKeys,
        Integer pivotLevel,
        List<String> keys,
        Map<PivotMetric, Double> metrics) {

    /** How many PIVOT_KEY columns the table has. */
    public static final int MAX_KEYS = 10;

    public static final String KEY_SEPARATOR = "/";

    public PivotRow {
        keys = List.copyOf(keys);
        metrics = Map.copyOf(metrics);
    }

    public double metric(PivotMetric metric) {
        Double value = metrics.get(metric);
        return value == null ? 0.0d : value;
    }

    /**
     * Builds the metric map from raw column values, filling the two totals EasyRec does not
     * store. TOTAL_BREAKS and TOTAL_IMPACT_ABS are plain sums of stored counts, so they are
     * computed once at load and then behave like any other measure in the tree.
     */
    public static Map<PivotMetric, Double> withTotals(Map<PivotMetric, Double> stored) {
        EnumMap<PivotMetric, Double> metrics = new EnumMap<>(PivotMetric.class);
        metrics.putAll(stored);
        metrics.put(PivotMetric.TOTAL_BREAKS,
                value(metrics, PivotMetric.MISSING_SRC_COUNT)
                        + value(metrics, PivotMetric.MISSING_TRG_COUNT)
                        + value(metrics, PivotMetric.UNMATCH_COUNT));
        metrics.put(PivotMetric.TOTAL_IMPACT_ABS,
                value(metrics, PivotMetric.MISSING_SRC_IMPACT_ABS)
                        + value(metrics, PivotMetric.MISSING_TRG_IMPACT_ABS)
                        + value(metrics, PivotMetric.UNMATCH_IMPACT_ABS));
        return metrics;
    }

    private static double value(Map<PivotMetric, Double> metrics, PivotMetric metric) {
        Double found = metrics.get(metric);
        return found == null ? 0.0d : found;
    }
}
