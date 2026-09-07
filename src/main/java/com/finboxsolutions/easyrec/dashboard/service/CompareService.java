package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Side-by-side comparison of several batches, at template level and at column level.
 *
 * <p>Two things make this more than a join. First, FULL_PATH - not TEMPLATE_ID - is the
 * only stable identity for "the same reconciliation" across batches: EasyRec allocates
 * fresh ER_DASHBOARD_TEMPLATE rows per run, so the ids differ between batches even for the
 * same template file. Second, rows are ordered by spread rather than alphabetically, so
 * the reconciliations that actually moved surface at the top instead of being buried.
 *
 * <p>Spread is a magnitude and cannot say whether things improved; the signed delta from
 * the oldest to the newest batch can, which is why every row carries both.
 */
public class CompareService {

    /** Comparing more than this makes the table unreadable and the queries add up. */
    public static final int MAX_COMPARE_BATCHES = 6;

    /** One batch's figures for a row, or null when that batch has no such row at all. */
    public record Cell(Double value, long matched, long breaks, int count, String detail) {
    }

    /** One comparison row: a template path or a column label, with a cell per batch. */
    public record CompareRow(
            String label,
            List<Cell> cells,
            Double spread,
            Double delta,
            Integer deltaFromBatchId,
            Integer deltaToBatchId,
            boolean missingFromSome) {
    }

    /** The whole-batch totals shown above the comparison table. */
    public record BatchTotals(
            int batchId,
            long matched,
            long unmatched,
            long missingSource,
            long missingTarget,
            long rowsSource,
            long rowsTarget,
            int runCount,
            int templateCount) {

        public Double matchRate() {
            return Rates.matchRate(matched, rowsSource, rowsTarget);
        }

        public long breaks() {
            return unmatched + missingSource + missingTarget;
        }
    }

    private final DashboardDao dao;
    private final DashboardService dashboard;

    public CompareService(DashboardDao dao, DashboardService dashboard) {
        this.dao = dao;
        this.dashboard = dashboard;
    }

    /**
     * Trims and orders a selection, oldest batch first.
     *
     * <p>Oldest first is not cosmetic: it is what makes the leftmost column the baseline
     * and lets the signed delta read as "what changed since".
     */
    public List<BatchRow> resolveSelection(List<BatchRow> allBatches, Collection<Integer> requested) {
        Set<Integer> wanted = new LinkedHashSet<>(requested);
        List<BatchRow> found = new ArrayList<>();
        for (BatchRow batch : allBatches) {
            if (wanted.contains(batch.batchId())) {
                found.add(batch);
            }
        }
        found.sort(Comparator.comparingInt(BatchRow::batchId));
        return found.size() > MAX_COMPARE_BATCHES ? found.subList(0, MAX_COMPARE_BATCHES) : found;
    }

    /** Summed row statistics per batch, in the order given. */
    public Map<Integer, BatchTotals> totals(List<Integer> batchIds,
                                            Map<Integer, List<DashboardService.Reconciliation>> byBatch) {
        Map<Integer, List<RunRow>> runsByBatch = dao.findRunsByBatch(batchIds);
        Map<Integer, BatchTotals> totals = new LinkedHashMap<>();
        for (Integer batchId : batchIds) {
            long[] sums = new long[6];
            Set<Integer> seen = new LinkedHashSet<>();
            for (DashboardService.Reconciliation rec : byBatch.getOrDefault(batchId, List.of())) {
                RowStats stats = rec.stats();
                if (stats == null) {
                    continue;
                }
                // A run reconciles one template per context row, so a stat row is counted
                // once even when two context rows resolve to the same statistics id.
                if (!seen.add(System.identityHashCode(stats))) {
                    continue;
                }
                sums[0] += stats.matched();
                sums[1] += stats.unmatched();
                sums[2] += stats.missingSource();
                sums[3] += stats.missingTarget();
                sums[4] += stats.rowsSource();
                sums[5] += stats.rowsTarget();
            }
            totals.put(batchId, new BatchTotals(batchId, sums[0], sums[1], sums[2], sums[3],
                    sums[4], sums[5], runsByBatch.getOrDefault(batchId, List.of()).size(),
                    byBatch.getOrDefault(batchId, List.of()).size()));
        }
        return totals;
    }

    /**
     * One row per template FULL_PATH, with a cell per batch, widest spread first.
     *
     * <p>Where a batch reconciles one path more than once its counts are summed into a
     * single cell, so the comparison stays one row per path however the batch was composed.
     */
    public List<CompareRow> compareTemplates(
            List<Integer> batchIds,
            Map<Integer, List<DashboardService.Reconciliation>> byBatch) {

        Set<String> paths = new TreeSet<>();
        for (List<DashboardService.Reconciliation> recs : byBatch.values()) {
            for (DashboardService.Reconciliation rec : recs) {
                if (rec.templatePath() != null) {
                    paths.add(rec.templatePath());
                }
            }
        }

        List<CompareRow> rows = new ArrayList<>(paths.size());
        for (String path : paths) {
            List<Cell> cells = new ArrayList<>(batchIds.size());
            for (Integer batchId : batchIds) {
                List<DashboardService.Reconciliation> matching = new ArrayList<>();
                for (DashboardService.Reconciliation rec : byBatch.getOrDefault(batchId, List.of())) {
                    if (path.equals(rec.templatePath())) {
                        matching.add(rec);
                    }
                }
                if (matching.isEmpty()) {
                    cells.add(null);   // this batch did not reconcile this template
                    continue;
                }
                long matched = 0;
                long breaks = 0;
                long rowsSource = 0;
                long rowsTarget = 0;
                for (DashboardService.Reconciliation rec : matching) {
                    RowStats stats = rec.stats();
                    if (stats == null) {
                        continue;
                    }
                    matched += stats.matched();
                    breaks += stats.breakCount();
                    rowsSource += stats.rowsSource();
                    rowsTarget += stats.rowsTarget();
                }
                cells.add(new Cell(Rates.matchRate(matched, rowsSource, rowsTarget),
                        matched, breaks, matching.size(), null));
            }
            rows.add(buildRow(path, batchIds, cells));
        }
        rows.sort(bySpreadThenLabel());
        return rows;
    }

    /**
     * One row per column label for a single template path, with a cell per batch.
     *
     * <p>Column statistics are keyed by the statistics-side TEMPLATE_ID, which each
     * reconciliation already carries resolved.
     */
    public List<CompareRow> compareColumns(
            List<Integer> batchIds,
            Map<Integer, List<DashboardService.Reconciliation>> byBatch,
            String path) {

        Map<Integer, Map<String, ColumnStats>> byBatchAndLabel = new LinkedHashMap<>();
        for (Integer batchId : batchIds) {
            Map<Integer, Integer> templateIdByRun = new LinkedHashMap<>();
            for (DashboardService.Reconciliation rec : byBatch.getOrDefault(batchId, List.of())) {
                if (path.equals(rec.templatePath()) && rec.statsTemplateId() != null) {
                    templateIdByRun.put(rec.runId(), rec.statsTemplateId());
                }
            }
            Map<String, ColumnStats> byLabel = new LinkedHashMap<>();
            for (List<ColumnStats> found : dao.findColumnStats(templateIdByRun).values()) {
                for (ColumnStats column : found) {
                    byLabel.put(column.colLabel(), column);
                }
            }
            byBatchAndLabel.put(batchId, byLabel);
        }

        Set<String> labels = new TreeSet<>();
        for (Map<String, ColumnStats> byLabel : byBatchAndLabel.values()) {
            labels.addAll(byLabel.keySet());
        }

        List<CompareRow> rows = new ArrayList<>(labels.size());
        for (String label : labels) {
            List<Cell> cells = new ArrayList<>(batchIds.size());
            for (Integer batchId : batchIds) {
                ColumnStats column = byBatchAndLabel.get(batchId).get(label);
                if (column == null) {
                    cells.add(null);   // this batch has no such column
                    continue;
                }
                cells.add(new Cell(column.matchPercentage(), column.colNbExactMatch(),
                        column.colNbUnmatch(), 1, column.colClass()));
            }
            rows.add(buildRow(label, batchIds, cells));
        }
        rows.sort(bySpreadThenLabel());
        return rows;
    }

    /** Spread, signed delta and the missing flag for one row of cells. */
    private CompareRow buildRow(String label, List<Integer> batchIds, List<Cell> cells) {
        List<Double> values = new ArrayList<>();
        Integer firstBatch = null;
        Integer lastBatch = null;
        Double firstValue = null;
        Double lastValue = null;
        boolean missing = false;

        for (int index = 0; index < cells.size(); index++) {
            Cell cell = cells.get(index);
            if (cell == null) {
                missing = true;
                continue;
            }
            if (cell.value() == null) {
                continue;
            }
            values.add(cell.value());
            if (firstValue == null) {
                firstValue = cell.value();
                firstBatch = batchIds.get(index);
            }
            lastValue = cell.value();
            lastBatch = batchIds.get(index);
        }

        Double spread = null;
        if (values.size() > 1) {
            double min = values.get(0);
            double max = values.get(0);
            for (Double value : values) {
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            spread = max - min;
        }
        Double delta = values.size() > 1 ? lastValue - firstValue : null;
        return new CompareRow(label, cells, spread, delta,
                delta == null ? null : firstBatch, delta == null ? null : lastBatch, missing);
    }

    /** Widest spread first; a row with no spread sinks, ties broken alphabetically. */
    private static Comparator<CompareRow> bySpreadThenLabel() {
        return Comparator
                .comparingDouble((CompareRow row) -> row.spread() == null ? 0.0d : -row.spread())
                .thenComparing(CompareRow::label, String.CASE_INSENSITIVE_ORDER);
    }
}
