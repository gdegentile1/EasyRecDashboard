package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * Trend over time, either for a whole batch's reconciliation suite or for one template.
 *
 * <p>The hard part is deciding what counts as "the same thing run again". A batch that
 * reconciled only some of the reference templates would contribute totals built from fewer
 * reconciliations, and putting that on the same trend line as a full run reads as a
 * collapse in volume rather than as a smaller batch. So a batch qualifies only when it
 * covers every one of the reference paths, and is then measured over those paths alone -
 * a wider batch still qualifies, contributing only its overlapping part.
 */
public class HistoryService {

    private static final DateTimeFormatter LONG_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    /** One point on a trend: a batch or run, its figures, and its movement since the last. */
    public record Execution(
            BatchRow batch,
            List<Integer> runIds,
            int reconciliations,
            long matched,
            Double matchRate,
            Long breaks,
            int passed,
            int failed,
            LocalDateTime when,
            String longLabel,
            String shortLabel,
            Double rateDelta,
            Long breaksDelta,
            boolean current) {

        public Rates.Tone tone() {
            return Rates.toneOf(matchRate);
        }
    }

    /** A trend plus the summary figures shown beside it. */
    public record History(
            List<Execution> executions,
            List<String> templatePaths,
            Double bestRate,
            Double worstRate,
            Execution latest) {

        /** Newest first, which is the order the table under the chart reads in. */
        public List<Execution> newestFirst() {
            List<Execution> reversed = new ArrayList<>(executions);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
    }

    private final DashboardDao dao;
    private final DashboardService dashboard;

    public HistoryService(DashboardDao dao, DashboardService dashboard) {
        this.dao = dao;
        this.dashboard = dashboard;
    }

    /**
     * Every batch that ran this batch's whole template set, oldest first.
     *
     * @param referenceBatchId the batch whose suite defines the comparison
     */
    public History batchHistory(int referenceBatchId) {
        List<RunRow> referenceRuns = dao.findRunsByBatch(List.of(referenceBatchId))
                .getOrDefault(referenceBatchId, List.of());
        List<Integer> referenceRunIds = new ArrayList<>();
        for (RunRow run : referenceRuns) {
            referenceRunIds.add(run.runId());
        }
        Set<String> paths = templatePathsOf(referenceRunIds);
        Map<Integer, List<Integer>> runsByBatch = comparableBatches(paths, referenceBatchId);
        if (runsByBatch.isEmpty()) {
            return new History(List.of(), new ArrayList<>(new TreeSet<>(paths)), null, null, null);
        }

        // One pass over every comparable run: calling findReconciliations per batch would
        // repeat the same context, statistics and template queries once per point.
        List<Integer> allRunIds = new ArrayList<>();
        Map<Integer, Integer> batchByRun = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : runsByBatch.entrySet()) {
            for (Integer runId : entry.getValue()) {
                allRunIds.add(runId);
                batchByRun.put(runId, entry.getKey());
            }
        }

        Map<Integer, List<DashboardService.Reconciliation>> grouped = new LinkedHashMap<>();
        for (DashboardService.Reconciliation rec : dashboard.findReconciliations(allRunIds, null)) {
            if (!paths.contains(rec.templatePath())) {
                continue;   // a wider batch's extra templates are not part of this trend
            }
            grouped.computeIfAbsent(batchByRun.get(rec.runId()), key -> new ArrayList<>()).add(rec);
        }

        List<BatchRow> batches = new ArrayList<>();
        for (BatchRow batch : dao.findAllBatches()) {
            if (runsByBatch.containsKey(batch.batchId())) {
                batches.add(batch);
            }
        }

        List<Execution> executions = new ArrayList<>(batches.size());
        for (BatchRow batch : batches) {
            List<DashboardService.Reconciliation> recs =
                    grouped.getOrDefault(batch.batchId(), List.of());
            executions.add(summarise(batch, runsByBatch.get(batch.batchId()), recs,
                    batch.batchId() == referenceBatchId));
        }
        return finish(executions, new ArrayList<>(new TreeSet<>(paths)));
    }

    /**
     * Every execution of one template, oldest first.
     *
     * <p>Ordered by when the batch ran rather than by RUN_ID, so the trend follows real
     * time; a batch with no usable timestamp falls back to its id, which is monotonic.
     */
    public History templateHistory(int templateId) {
        Set<Integer> runIds = new LinkedHashSet<>();
        for (RunContextRow context : dao.findRunContexts(allRunIdsForTemplate(templateId))) {
            if (context.templateId() != null && context.templateId() == templateId) {
                runIds.add(context.runId());
            }
        }
        if (runIds.isEmpty()) {
            return new History(List.of(), List.of(), null, null, null);
        }

        Map<Integer, Integer> batchByRun = new LinkedHashMap<>();
        Map<Integer, BatchRow> batches = new LinkedHashMap<>();
        for (BatchRow batch : dao.findAllBatches()) {
            batches.put(batch.batchId(), batch);
        }
        for (Map.Entry<Integer, List<RunRow>> entry : dao.findRunsByBatch(batches.keySet()).entrySet()) {
            for (RunRow run : entry.getValue()) {
                if (runIds.contains(run.runId())) {
                    batchByRun.put(run.runId(), entry.getKey());
                }
            }
        }

        List<DashboardService.Reconciliation> all = dashboard.findReconciliations(runIds, null);
        Map<Integer, List<DashboardService.Reconciliation>> grouped = new LinkedHashMap<>();
        for (DashboardService.Reconciliation rec : all) {
            if (rec.templateId() != null && rec.templateId() == templateId) {
                grouped.computeIfAbsent(batchByRun.get(rec.runId()), key -> new ArrayList<>()).add(rec);
            }
        }

        List<Execution> executions = new ArrayList<>();
        for (Map.Entry<Integer, List<DashboardService.Reconciliation>> entry : grouped.entrySet()) {
            BatchRow batch = batches.get(entry.getKey());
            if (batch == null) {
                continue;
            }
            List<Integer> runs = new ArrayList<>();
            for (DashboardService.Reconciliation rec : entry.getValue()) {
                runs.add(rec.runId());
            }
            executions.add(summarise(batch, runs, entry.getValue(), false));
        }
        return finish(executions, List.of());
    }

    // ------------------------------------------------------------------------ internals

    /** The distinct template FULL_PATHs reconciled by the given runs. */
    private Set<String> templatePathsOf(Collection<Integer> runIds) {
        Set<Integer> templateIds = new LinkedHashSet<>();
        for (RunContextRow context : dao.findRunContexts(runIds)) {
            if (context.templateId() != null) {
                templateIds.add(context.templateId());
            }
        }
        Set<String> paths = new LinkedHashSet<>();
        dao.findTemplates(templateIds).forEach((id, template) -> {
            if (template.fullPath() != null && !template.fullPath().isBlank()) {
                paths.add(template.fullPath());
            }
        });
        return paths;
    }

    /**
     * Batches that ran every one of {@code paths}, as {@code {batchId: [runId, ...]}}.
     *
     * <p>Coverage is one-directional on purpose: a batch reconciling two of these templates
     * finds every larger batch in its own history, measured over just those two. The
     * reference batch is always included, even if its own paths resolved oddly.
     */
    private Map<Integer, List<Integer>> comparableBatches(Set<String> paths, int referenceBatchId) {
        if (paths.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> pathByTemplate = dao.findTemplateIdsByPaths(paths);
        if (pathByTemplate.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> batchByRun = new LinkedHashMap<>();
        List<BatchRow> allBatches = dao.findAllBatches();
        List<Integer> allBatchIds = new ArrayList<>(allBatches.size());
        for (BatchRow batch : allBatches) {
            allBatchIds.add(batch.batchId());
        }
        for (Map.Entry<Integer, List<RunRow>> entry : dao.findRunsByBatch(allBatchIds).entrySet()) {
            for (RunRow run : entry.getValue()) {
                batchByRun.put(run.runId(), entry.getKey());
            }
        }

        Map<Integer, Set<String>> covered = new LinkedHashMap<>();
        Map<Integer, Set<Integer>> runsByBatch = new LinkedHashMap<>();
        for (RunContextRow context : dao.findRunContexts(batchByRun.keySet())) {
            String path = pathByTemplate.get(context.templateId());
            if (path == null) {
                continue;
            }
            Integer batchId = batchByRun.get(context.runId());
            if (batchId == null) {
                continue;
            }
            covered.computeIfAbsent(batchId, key -> new LinkedHashSet<>()).add(path);
            runsByBatch.computeIfAbsent(batchId, key -> new TreeSet<>()).add(context.runId());
        }

        Map<Integer, List<Integer>> qualifying = new LinkedHashMap<>();
        for (Map.Entry<Integer, Set<String>> entry : covered.entrySet()) {
            if (entry.getValue().containsAll(paths) || entry.getKey() == referenceBatchId) {
                qualifying.put(entry.getKey(), new ArrayList<>(runsByBatch.get(entry.getKey())));
            }
        }
        return qualifying;
    }

    private List<Integer> allRunIdsForTemplate(int templateId) {
        List<Integer> runIds = new ArrayList<>();
        List<BatchRow> batches = dao.findAllBatches();
        List<Integer> batchIds = new ArrayList<>(batches.size());
        for (BatchRow batch : batches) {
            batchIds.add(batch.batchId());
        }
        for (List<RunRow> runs : dao.findRunsByBatch(batchIds).values()) {
            for (RunRow run : runs) {
                runIds.add(run.runId());
            }
        }
        return runIds;
    }

    private Execution summarise(BatchRow batch, List<Integer> runIds,
                                List<DashboardService.Reconciliation> recs, boolean current) {
        long matched = 0;
        long rowsSource = 0;
        long rowsTarget = 0;
        long breaks = 0;
        int passed = 0;
        int failed = 0;
        int withStats = 0;
        for (DashboardService.Reconciliation rec : recs) {
            RowStats stats = rec.stats();
            if (stats == null) {
                continue;
            }
            withStats++;
            matched += stats.matched();
            rowsSource += stats.rowsSource();
            rowsTarget += stats.rowsTarget();
            breaks += stats.breakCount();
            StatusLabel status = stats.status();
            if (status.isPassed()) {
                passed++;
            } else if (status.isFailed()) {
                failed++;
            }
        }
        LocalDateTime when = dashboard.timestampOf(batch);
        return new Execution(batch, runIds == null ? List.of() : runIds, recs.size(), matched,
                Rates.matchRate(matched, rowsSource, rowsTarget),
                withStats == 0 ? null : breaks, passed, failed, when,
                when == null ? "Batch " + batch.batchId() : LONG_LABEL.format(when),
                when == null ? "#" + batch.batchId() : SHORT_LABEL.format(when),
                null, null, current);
    }

    /** Sorts oldest first, fills in the deltas, and computes the best/worst summary. */
    private History finish(List<Execution> executions, List<String> paths) {
        executions.sort(Comparator.comparingDouble(execution -> execution.when() == null
                ? execution.batch().batchId()
                : execution.when().atZone(dashboard.zone()).toInstant().toEpochMilli()));

        List<Execution> withDeltas = new ArrayList<>(executions.size());
        Double previousRate = null;
        Long previousBreaks = null;
        Double best = null;
        Double worst = null;
        for (Execution execution : executions) {
            Double rateDelta = execution.matchRate() != null && previousRate != null
                    ? execution.matchRate() - previousRate : null;
            Long breaksDelta = execution.breaks() != null && previousBreaks != null
                    ? execution.breaks() - previousBreaks : null;
            if (execution.matchRate() != null) {
                previousRate = execution.matchRate();
                best = best == null ? execution.matchRate() : Math.max(best, execution.matchRate());
                worst = worst == null ? execution.matchRate() : Math.min(worst, execution.matchRate());
            }
            if (execution.breaks() != null) {
                previousBreaks = execution.breaks();
            }
            withDeltas.add(new Execution(execution.batch(), execution.runIds(),
                    execution.reconciliations(), execution.matched(), execution.matchRate(),
                    execution.breaks(), execution.passed(), execution.failed(), execution.when(),
                    execution.longLabel(), execution.shortLabel(), rateDelta, breaksDelta,
                    execution.current()));
        }
        return new History(withDeltas, paths, best, worst,
                withDeltas.isEmpty() ? null : withDeltas.get(withDeltas.size() - 1));
    }
}
