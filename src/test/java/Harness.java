import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.service.CompareService;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.HistoryService;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/** Prints the ported figures so they can be diffed against the Django originals. */
public final class Harness {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:sqlite:" + args[0];
        DataSource dataSource = new DataSource() {
            @Override public Connection getConnection() throws SQLException {
                return DriverManager.getConnection(url);
            }
            @Override public Connection getConnection(String u, String p) throws SQLException {
                return getConnection();
            }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };

        DashboardDao dao = new JdbcDashboardDao(dataSource);
        DashboardService dashboard = new DashboardService(dao);
        CompareService compare = new CompareService(dao, dashboard);
        HistoryService history = new HistoryService(dao, dashboard);

        List<BatchRow> batches = dao.findAllBatches();

        System.out.println("## BATCH SUMMARIES");
        for (DashboardService.BatchSummary summary : dashboard.summarise(batches)) {
            System.out.printf(Locale.ROOT, "batch=%d status=%s rate=%s passed=%d failed=%d runs=%d%n",
                    summary.batch().batchId(), summary.batch().status(),
                    format(summary.matchRate()), summary.reconciliationsPassed(),
                    summary.reconciliationsFailed(), summary.runCount());
        }

        System.out.println("## RECONCILIATIONS");
        for (BatchRow batch : batches) {
            List<Integer> runIds = new ArrayList<>();
            dao.findRunsByBatch(List.of(batch.batchId())).values()
                    .forEach(runs -> runs.forEach(run -> runIds.add(run.runId())));
            List<DashboardService.Reconciliation> recs = dashboard.findReconciliations(runIds, null);
            long withStats = recs.stream().filter(rec -> rec.stats() != null).count();
            System.out.printf(Locale.ROOT, "batch=%d reconciliations=%d withStats=%d%n",
                    batch.batchId(), recs.size(), withStats);
            for (DashboardService.Reconciliation rec : recs) {
                System.out.printf(Locale.ROOT, "  run=%d tpl=%s stats=%s rate=%s status=%s path=%s%n",
                        rec.runId(), rec.templateId(), rec.statsTemplateId(),
                        format(rec.matchRate()), rec.status(), rec.templatePath());
            }
        }

        System.out.println("## PIVOT TREES");
        for (BatchRow batch : batches) {
            List<Integer> runIds = new ArrayList<>();
            dao.findRunsByBatch(List.of(batch.batchId())).values()
                    .forEach(runs -> runs.forEach(run -> runIds.add(run.runId())));
            for (DashboardService.Reconciliation rec : dashboard.findReconciliations(runIds, null)) {
                if (rec.statsTemplateId() == null) {
                    continue;
                }
                List<PivotRow> pivots = dao.findPivots(rec.runId(), rec.statsTemplateId());
                if (pivots.isEmpty()) {
                    continue;
                }
                PivotTreeBuilder.Tree tree = PivotTreeBuilder.build(pivots,
                        rec.stats() == null ? null : rec.stats().pivotBreakdown());
                System.out.printf(Locale.ROOT, "run=%d tpl=%d pivots=%d nodes=%d maxDepth=%d levels=%s%n",
                        rec.runId(), rec.statsTemplateId(), pivots.size(), tree.rows().size(),
                        tree.maxDepth(), levelNames(tree));
                for (PivotTreeBuilder.Node node : tree.rows()) {
                    System.out.printf(Locale.ROOT,
                            "  id=%d parent=%d depth=%d agg=%s leaves=%d key=%s breaks=%.2f "
                            + "sumS=%.4f impact=%.4f impactPct=%.6f%n",
                            node.id(), node.parentId(), node.depth(), node.aggregated(),
                            node.leafCount(), node.key(),
                            node.value(PivotMetric.TOTAL_BREAKS),
                            node.value(PivotMetric.UNMATCH_SUM_S),
                            node.value(PivotMetric.UNMATCH_IMPACT),
                            node.value(PivotMetric.UNMATCH_IMPACT_PCT));
                }
            }
        }

        System.out.println("## COMPARE (all batches, capped)");
        List<Integer> requested = new ArrayList<>();
        for (BatchRow batch : batches) {
            requested.add(batch.batchId());
        }
        List<BatchRow> selection = compare.resolveSelection(batches, requested);
        List<Integer> selectedIds = new ArrayList<>();
        for (BatchRow batch : selection) {
            selectedIds.add(batch.batchId());
        }
        Map<Integer, List<DashboardService.Reconciliation>> byBatch = new LinkedHashMap<>();
        Map<Integer, List<com.finboxsolutions.easyrec.dashboard.model.RunRow>> runsByBatch =
                dao.findRunsByBatch(selectedIds);
        for (Integer batchId : selectedIds) {
            List<Integer> runIds = new ArrayList<>();
            runsByBatch.getOrDefault(batchId, List.of()).forEach(run -> runIds.add(run.runId()));
            byBatch.put(batchId, dashboard.findReconciliations(runIds, null));
        }
        System.out.println("selected=" + selectedIds);
        compare.totals(selectedIds, byBatch).forEach((batchId, totals) ->
                System.out.printf(Locale.ROOT, "totals batch=%d rate=%s breaks=%d templates=%d%n",
                        batchId, format(totals.matchRate()), totals.breaks(), totals.templateCount()));
        for (CompareService.CompareRow row : compare.compareTemplates(selectedIds, byBatch)) {
            System.out.printf(Locale.ROOT, "tplrow spread=%s delta=%s missing=%s label=%s%n",
                    format(row.spread()), format(row.delta()), row.missingFromSome(), row.label());
        }

        System.out.println("## HISTORY");
        for (BatchRow batch : batches) {
            HistoryService.History found = history.batchHistory(batch.batchId());
            System.out.printf(Locale.ROOT, "history batch=%d points=%d paths=%d best=%s worst=%s%n",
                    batch.batchId(), found.executions().size(), found.templatePaths().size(),
                    format(found.bestRate()), format(found.worstRate()));
            for (HistoryService.Execution execution : found.executions()) {
                System.out.printf(Locale.ROOT, "  point batch=%d recs=%d rate=%s breaks=%s "
                                + "passed=%d failed=%d rateDelta=%s%n",
                        execution.batch().batchId(), execution.reconciliations(),
                        format(execution.matchRate()), execution.breaks(),
                        execution.passed(), execution.failed(), format(execution.rateDelta()));
            }
        }
    }

    private static String levelNames(PivotTreeBuilder.Tree tree) {
        List<String> names = new ArrayList<>();
        for (PivotTreeBuilder.Level level : tree.levels()) {
            names.add(level.name());
        }
        return String.join("|", names);
    }

    private static String format(Double value) {
        return value == null ? "null" : String.format(Locale.ROOT, "%.6f", value);
    }
}
