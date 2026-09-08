package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the dashboard screens need, computed off the DAO and free of Swing.
 *
 * <p>Keeping this layer UI-free is what makes the numbers testable: the aggregations below
 * are the ones that have to agree across the home screen, the batch list, the batch detail
 * and the comparison, and a discrepancy between two screens is otherwise very hard to
 * spot. Call every method off the EDT.
 */
public class DashboardService {

    /**
     * A batch plus the aggregates the list and home screens show beside it.
     *
     * <p>{@code sourceRows} and {@code targetRows} are the batch's whole volume: every
     * ER_DASHBOARD_STAT_ROWS row of every run, summed. They were already being computed
     * here as the denominator of {@code matchRate} and then thrown away, which is why a
     * screen could show 99% and give no hint whether that was 99% of eight rows or of two
     * million - the one thing that says whether the rate is worth trusting.
     *
     * <p>Both are 0 rather than null for a batch with no statistics, matching the pass and
     * fail counts beside them.
     */
    public record BatchSummary(
            BatchRow batch,
            Double matchRate,
            int reconciliationsPassed,
            int reconciliationsFailed,
            long sourceRows,
            long targetRows,
            List<RunRow> runs,
            Map<String, String> runCells) {

        public int runCount() {
            return runs.size();
        }
    }

    /**
     * One reconciliation: a context row, the template it names, and its statistics.
     *
     * <p>A batch holds a single run and that run reconciles one template per context row,
     * so these - not the runs - are what the batch screen lists.
     */
    public record Reconciliation(
            int runId,
            Integer templateId,
            Integer statsTemplateId,
            String templatePath,
            RunContextRow context,
            RowStats stats) {

        public Double matchRate() {
            return stats == null ? null : stats.matchRate();
        }

        /**
         * The reconciliation's outcome, from ER_DASHBOARD_STAT_ROWS and nowhere else.
         *
         * <p>That column is written from {@code TemplateUtils.getStatus}: 0 the template
         * matched, 1 it had mismatches, -1 it produced no statistics. It is the only record
         * of how a reconciliation actually turned out.
         *
         * <p>ER_DASHBOARD_RUN_CONTEXT has a STATUS too and it is not an outcome.
         * {@code ExecutionContextDaoImpl} inserts a hard-coded 0 into it and nothing in
         * EasyRec ever updates it - the schema has a select and an insert for that table and
         * no update at all. Under the reconciliation mapping a permanent 0 reads as PASSED,
         * so consulting it made every reconciliation claim to have passed however many breaks
         * it found. It is an operator-maintained field with no operator writing to it yet.
         *
         * <p>Absent statistics are therefore UNKNOWN rather than anything else: a
         * reconciliation that recorded nothing has no outcome to report, and saying PASSED
         * for it was the same mistake in a quieter form.
         */
        public StatusLabel status() {
            return stats == null ? StatusLabel.UNKNOWN : stats.status();
        }
    }

    /** One of the one-click date windows offered at the top of the home screen. */
    public record Period(String key, String label, int days) {
    }

    /** A period with the batches that fall inside it. */
    public record PeriodCard(
            Period period,
            LocalDate from,
            LocalDate to,
            List<Integer> batchIds,
            int passed,
            int failed) {

        public int count() {
            return batchIds.size();
        }
    }

    /** The windows the home screen offers. The last is also the default range. */
    public static final List<Period> PERIODS = List.of(
            new Period("today", "Today", 1),
            new Period("week", "Last 7 Days", 7),
            new Period("month", "Last 30 Days", 30));

    /** How many batches the home table lists; the header reports the full count beside it. */
    public static final int HOME_RECENT_LIMIT = 25;

    /** The run columns the batch list shows, in display order. */
    public static final List<String> RUN_COLUMNS = List.of("Project", "Template Path",
            "Source Alias", "Source Label", "Target Alias", "Target Label");

    /**
     * The context columns a reconciliation row shows, in display order.
     *
     * <p>No Project among them. Every reconciliation on that screen belongs to the one batch
     * being looked at, so the column repeated a single value down the whole table while the
     * header above it already carried that value in a field the operator can edit. A column
     * that never varies within its own screen costs width and says nothing.
     */
    public static final List<String> CONTEXT_COLUMNS =
            List.of("Source", "Target", "Category 1", "Category 2", "Category 3");

    private final DashboardDao dao;
    private final ZoneId zone;

    public DashboardService(DashboardDao dao) {
        this(dao, ZoneId.systemDefault());
    }

    public DashboardService(DashboardDao dao, ZoneId zone) {
        this.dao = dao;
        this.zone = zone;
    }

    // ------------------------------------------------------------------- batch selection

    /**
     * Batches whose timestamp falls in {@code [from, to]}, inclusive of both whole days.
     *
     * <p>Filtered in Java rather than in SQL because SYS_DATE is an epoch-millisecond
     * string in a character column: no cast is portable across Oracle, H2 and DuckDB, and
     * one unparseable row would fail the whole statement. ER_DASHBOARD_BATCH is the small
     * table, so reading it whole is cheap; the statistics tables are always queried by id.
     */
    public List<BatchRow> findBatchesInRange(List<BatchRow> batches, LocalDate from, LocalDate to) {
        long start = from == null ? Long.MIN_VALUE : epochStartOfDay(from);
        long end = to == null ? Long.MAX_VALUE : epochStartOfDay(to.plusDays(1));
        List<BatchRow> matching = new ArrayList<>();
        for (BatchRow batch : batches) {
            Long epoch = batch.epochMillis();
            if (epoch != null && epoch >= start && epoch < end) {
                matching.add(batch);
            }
        }
        return matching;
    }

    /**
     * The period cards, with the batches each window covers.
     *
     * <p>Periods overlap on purpose, so a batch can land in more than one of them.
     */
    public List<PeriodCard> periodCards(List<BatchRow> batches, LocalDate today,
                                        Map<Integer, int[]> statusCounts) {
        List<PeriodCard> cards = new ArrayList<>(PERIODS.size());
        for (Period period : PERIODS) {
            LocalDate from = today.minusDays(period.days() - 1L);
            List<Integer> ids = new ArrayList<>();
            int passed = 0;
            int failed = 0;
            for (BatchRow batch : findBatchesInRange(batches, from, today)) {
                ids.add(batch.batchId());
                int[] counts = statusCounts.get(batch.batchId());
                if (counts != null) {
                    passed += counts[0];
                    failed += counts[1];
                }
            }
            cards.add(new PeriodCard(period, from, today, ids, passed, failed));
        }
        return cards;
    }

    private long epochStartOfDay(LocalDate day) {
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    // ------------------------------------------------------------------ batch aggregates

    /**
     * Match rate, pass/fail counts and folded run cells for each of {@code batches}.
     *
     * <p>Everything is fetched in bulk for the whole page: the screen this replaces was
     * rewritten twice to get the per-batch queries out of it.
     */
    public List<BatchSummary> summarise(List<BatchRow> batches) {
        List<Integer> batchIds = new ArrayList<>(batches.size());
        for (BatchRow batch : batches) {
            batchIds.add(batch.batchId());
        }
        Map<Integer, List<RunRow>> runsByBatch = dao.findRunsByBatch(batchIds);

        Map<Integer, Integer> batchByRun = new LinkedHashMap<>();
        List<Integer> runIds = new ArrayList<>();
        Set<Integer> templateIds = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<RunRow>> entry : runsByBatch.entrySet()) {
            for (RunRow run : entry.getValue()) {
                batchByRun.put(run.runId(), entry.getKey());
                runIds.add(run.runId());
                if (run.templateId() != null) {
                    templateIds.add(run.templateId());
                }
            }
        }

        List<RowStats> stats = dao.findRowStats(runIds);
        Map<Integer, TemplateRow> templates = dao.findTemplates(templateIds);

        Map<Integer, long[]> totals = new LinkedHashMap<>();
        Map<Integer, int[]> statusCounts = new LinkedHashMap<>();
        for (RowStats row : stats) {
            Integer batchId = batchByRun.get(row.runId());
            if (batchId == null) {
                continue;
            }
            long[] total = totals.computeIfAbsent(batchId, key -> new long[3]);
            total[0] += row.matched();
            total[1] += row.rowsSource();
            total[2] += row.rowsTarget();

            int[] counts = statusCounts.computeIfAbsent(batchId, key -> new int[2]);
            StatusLabel status = row.status();
            if (status.isPassed()) {
                counts[0]++;
            } else if (status.isFailed()) {
                counts[1]++;
            }
        }

        List<BatchSummary> summaries = new ArrayList<>(batches.size());
        for (BatchRow batch : batches) {
            long[] total = totals.get(batch.batchId());
            int[] counts = statusCounts.getOrDefault(batch.batchId(), new int[2]);
            List<RunRow> runs = runsByBatch.getOrDefault(batch.batchId(), List.of());
            summaries.add(new BatchSummary(
                    batch,
                    total == null ? null : Rates.matchRate(total[0], total[1], total[2]),
                    counts[0], counts[1],
                    total == null ? 0L : total[1],
                    total == null ? 0L : total[2],
                    runs,
                    foldRunCells(runs, templates)));
        }
        return summaries;
    }

    /**
     * Pass/fail reconciliation counts per batch, as {@code {passed, failed}}.
     *
     * <p>One ER_DASHBOARD_STAT_ROWS row is one reconciliation, and its STATUS is what the
     * batch detail screen badges, so counting those rows keeps the two screens telling the
     * same story. Any other code counts as neither.
     */
    public Map<Integer, int[]> reconciliationStatusCounts(Collection<Integer> batchIds) {
        Map<Integer, int[]> counts = new LinkedHashMap<>();
        if (batchIds.isEmpty()) {
            return counts;
        }
        Map<Integer, Integer> batchByRun = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<RunRow>> entry : dao.findRunsByBatch(batchIds).entrySet()) {
            for (RunRow run : entry.getValue()) {
                batchByRun.put(run.runId(), entry.getKey());
            }
        }
        for (Integer batchId : batchIds) {
            counts.put(batchId, new int[2]);
        }
        for (RowStats row : dao.findRowStats(batchByRun.keySet())) {
            int[] bucket = counts.get(batchByRun.get(row.runId()));
            if (bucket == null) {
                continue;
            }
            StatusLabel status = row.status();
            if (status.isPassed()) {
                bucket[0]++;
            } else if (status.isFailed()) {
                bucket[1]++;
            }
        }
        return counts;
    }

    /**
     * A batch usually holds one run, but nothing in the schema says so, so the distinct
     * values of each field are folded and the extras are offered as a tooltip.
     */
    private Map<String, String> foldRunCells(List<RunRow> runs, Map<Integer, TemplateRow> templates) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String column : RUN_COLUMNS) {
            values.put(column, new ArrayList<>());
        }
        for (RunRow run : runs) {
            TemplateRow template = run.templateId() == null ? null : templates.get(run.templateId());
            add(values, "Project", run.projectPath());
            add(values, "Template Path", template == null ? null : template.fullPath());
            add(values, "Source Alias", run.sourceAlias());
            add(values, "Source Label", run.sourceLabel());
            add(values, "Target Alias", run.targetAlias());
            add(values, "Target Label", run.targetLabel());
        }
        Map<String, String> cells = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            List<String> found = entry.getValue();
            cells.put(entry.getKey(), found.isEmpty() ? "-" : String.join(" | ", found));
        }
        return cells;
    }

    private void add(Map<String, List<String>> values, String column, String raw) {
        String cleaned = ContextValues.clean(raw);
        List<String> found = values.get(column);
        if (cleaned != null && !found.contains(cleaned)) {
            found.add(cleaned);
        }
    }

    // -------------------------------------------------------------------- reconciliations

    /**
     * One row per context entry across {@code runIds}, with its resolved statistics.
     *
     * @param pathFragment narrows to templates whose FULL_PATH contains it, or null for all.
     *                     Resolved to template ids in the database rather than filtered
     *                     here, so a batch with many templates only fetches what it shows.
     */
    public List<Reconciliation> findReconciliations(Collection<Integer> runIds, String pathFragment) {
        if (runIds.isEmpty()) {
            return List.of();
        }
        Set<Integer> allowedTemplates = null;
        if (pathFragment != null && !pathFragment.isBlank()) {
            allowedTemplates = new LinkedHashSet<>(dao.findTemplateIdsByPathFragment(pathFragment.trim()));
            if (allowedTemplates.isEmpty()) {
                return List.of();
            }
        }

        List<RunContextRow> contexts = dao.findRunContexts(runIds);
        List<RowStats> stats = dao.findRowStats(runIds);
        Map<Integer, Set<Integer>> statsTemplateIds = TemplateIds.statsTemplateIdsByRun(stats);

        Map<Long, RowStats> statsByKey = new LinkedHashMap<>();
        for (RowStats row : stats) {
            statsByKey.put(key(row.runId(), row.templateId()), row);
        }

        Set<Integer> templateIds = new LinkedHashSet<>();
        for (RunContextRow context : contexts) {
            if (context.templateId() != null) {
                templateIds.add(context.templateId());
            }
        }
        Map<Integer, TemplateRow> templates = dao.findTemplates(templateIds);

        List<Reconciliation> reconciliations = new ArrayList<>(contexts.size());
        for (RunContextRow context : contexts) {
            if (allowedTemplates != null && !allowedTemplates.contains(context.templateId())) {
                continue;
            }
            Integer statsTemplateId = TemplateIds.resolve(context.templateId(),
                    statsTemplateIds.getOrDefault(context.runId(), Set.of()));
            TemplateRow template = context.templateId() == null ? null : templates.get(context.templateId());
            reconciliations.add(new Reconciliation(
                    context.runId(),
                    context.templateId(),
                    statsTemplateId,
                    template == null ? null : template.fullPath(),
                    context,
                    statsByKey.get(key(context.runId(), statsTemplateId))));
        }
        return reconciliations;
    }

    /** Packs (RUN_ID, TEMPLATE_ID) into one key, since neither table has a single-column one. */
    private static long key(int runId, Integer templateId) {
        return ((long) runId << 32) | (templateId == null ? 0xFFFFFFFFL : (templateId & 0xFFFFFFFFL));
    }

    /** Orders reconciliations, putting rows with no value last whichever way the sort runs. */
    public static Comparator<Reconciliation> reconciliationComparator(String column, boolean ascending) {
        Comparator<Reconciliation> comparator = switch (column) {
            case "Template Path" -> nullsLast(Reconciliation::templatePath);
            case "Project" -> nullsLast(rec -> ContextValues.clean(rec.context().name()));
            case "Source" -> nullsLast(rec -> ContextValues.clean(rec.context().sourceLabel()));
            case "Target" -> nullsLast(rec -> ContextValues.clean(rec.context().targetLabel()));
            case "Category 1" -> nullsLast(rec -> ContextValues.clean(rec.context().category1()));
            case "Category 2" -> nullsLast(rec -> ContextValues.clean(rec.context().category2()));
            case "Category 3" -> nullsLast(rec -> ContextValues.clean(rec.context().category3()));
            case "Status" -> Comparator.comparing(rec -> rec.status().name());
            case "Match Rate" -> Comparator.comparing(Reconciliation::matchRate,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(Reconciliation::templateId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return ascending ? comparator : comparator.reversed();
    }

    private static Comparator<Reconciliation> nullsLast(
            java.util.function.Function<Reconciliation, String> extractor) {
        return Comparator.comparing(extractor,
                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
    }

    // ---------------------------------------------------------------------- filter values

    /** Distinct Project / Source / Target values for the batch list filters. */
    public Map<String, List<String>> filterOptions() {
        Map<String, List<String>> options = new LinkedHashMap<>();
        options.put("Project", cleaned(dao.findContextValues("NAME")));
        options.put("Source", cleaned(dao.findContextValues("SOURCE_LABEL")));
        options.put("Target", cleaned(dao.findContextValues("TARGET_LABEL")));
        options.put("User", cleaned(dao.findBatchUserNames()));
        return options;
    }

    private List<String> cleaned(List<String> raw) {
        List<String> values = new ArrayList<>(raw.size());
        for (String value : raw) {
            String kept = ContextValues.clean(value);
            if (kept != null && !values.contains(kept)) {
                values.add(kept);
            }
        }
        values.sort(String.CASE_INSENSITIVE_ORDER);
        return values;
    }

    /** Exposed so the history and comparison services share one clock. */
    public ZoneId zone() {
        return zone;
    }

    /** The batch's timestamp, or null. Kept here so every screen formats it the same way. */
    public LocalDateTime timestampOf(BatchRow batch) {
        return batch.epochMillis() == null
                ? null
                : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(batch.epochMillis()), zone);
    }
}
