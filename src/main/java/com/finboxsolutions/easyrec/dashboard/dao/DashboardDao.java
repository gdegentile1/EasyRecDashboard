package com.finboxsolutions.easyrec.dashboard.dao;

import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read access to the ER_DASHBOARD_* tables, plus the two columns the dashboard writes back.
 *
 * <p>Every lookup takes a collection of ids rather than a single one. The web dashboard
 * this replaces had to be rewritten twice to remove N+1 queries - the template history
 * screen used to be one page per run - so the interface simply does not offer the shape
 * that reintroduces them.
 *
 * <p>Nothing here touches Swing, and nothing here is on the EDT. Call it from a
 * SwingWorker.
 */
public interface DashboardDao {

    /** Every batch, newest first. The table is small; the filtering happens in the service. */
    List<BatchRow> findAllBatches();

    BatchRow findBatch(int batchId);

    /** Runs of the given batches, keyed by BATCH_ID. */
    Map<Integer, List<RunRow>> findRunsByBatch(Collection<Integer> batchIds);

    RunRow findRun(int runId);

    /** Context rows of the given runs, ordered by (RUN_ID, TEMPLATE_ID). */
    List<RunContextRow> findRunContexts(Collection<Integer> runIds);

    /** Row statistics of the given runs. A run has one row per template it reconciled. */
    List<RowStats> findRowStats(Collection<Integer> runIds);

    List<ColumnStats> findColumnStats(int runId, int templateId);

    /** Column statistics for several (run, template) pairs at once, keyed by run id. */
    Map<Integer, List<ColumnStats>> findColumnStats(Map<Integer, Integer> templateIdByRun);

    List<PivotRow> findPivots(int runId, int templateId);

    Map<Integer, TemplateRow> findTemplates(Collection<Integer> templateIds);

    TemplateRow findTemplate(int templateId);

    /** Template ids whose FULL_PATH contains {@code fragment}, case-insensitively. */
    List<Integer> findTemplateIdsByPathFragment(String fragment);

    /** Template ids whose FULL_PATH is exactly one of {@code paths}. */
    Map<Integer, String> findTemplateIdsByPaths(Collection<String> paths);

    /** Distinct non-blank USER_NAME values of ER_DASHBOARD_BATCH, for the filter dropdown. */
    List<String> findBatchUserNames();

    /**
     * Distinct non-blank values of one ER_DASHBOARD_RUN_CONTEXT column, for a filter
     * dropdown. EasyRec's {@code <Undefined>} placeholder is filtered out by the caller.
     */
    List<String> findContextValues(String columnName);

    /** Updates DESCRIPTION on one batch. Returns the number of rows changed. */
    int updateBatchDescription(int batchId, String description);

    /**
     * Updates PROJECT_PATH on the given runs. Returns the number of rows changed.
     *
     * <p>Takes runs rather than a batch because that is where the column lives. The batch
     * screen shows one project for the batch and passes every run it holds - normally one -
     * so what is shown and what is written stay the same thing.
     */
    int updateRunProjectPath(Collection<Integer> runIds, String projectPath);

    /**
     * Updates the operator-maintained columns of one context row.
     *
     * <p>Constrained on BOTH RUN_ID and TEMPLATE_ID: ER_DASHBOARD_RUN_CONTEXT has no
     * primary key, so a write on RUN_ID alone rewrites every context row of that run.
     */
    int updateRunContext(int runId, int templateId, Map<String, Object> values);

    /**
     * Deletes a batch and everything keyed to its runs, children first.
     *
     * <p>ER_DASHBOARD_TEMPLATE is deliberately left alone: templates are shared definitions
     * referenced by every run that reconciled them, not rows a batch owns.
     *
     * @return how many rows were removed per table name
     */
    Map<String, Integer> deleteBatches(Collection<Integer> batchIds);
}
