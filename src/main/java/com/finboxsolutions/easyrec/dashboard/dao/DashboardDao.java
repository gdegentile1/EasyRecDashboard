package com.finboxsolutions.easyrec.dashboard.dao;

import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternSighting;
import com.finboxsolutions.easyrec.dashboard.model.PatternStat;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Read access to the ER_DASHBOARD_* tables, plus the few columns the dashboard writes back.
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

    // ---------------------------------------------------------------------------- patterns

    /** Runs by id, keyed by RUN_ID. Ids with no run are absent from the map. */
    Map<Integer, RunRow> findRuns(Collection<Integer> runIds);

    /**
     * Every ER_DASHBOARD_PATTERN_STAT row of every pattern of one template, ordered by
     * (PATTERN_ID, RUN_ID) - the whole history the trends are derived from. The engine's
     * {@code QUERY_ER_PATTERN_TREND_BY_TEMPLATE_ID}: each row carries the row counts of its
     * run on the template, for rates.
     *
     * <p>ER_DASHBOARD_PATTERN and ER_DASHBOARD_PATTERN_STAT come with a later engine than
     * the other dashboard tables, so a datasource can hold the rest without them. That case
     * throws {@code DashboardSchemaMissingException} like any missing table; the service
     * decides it means "no patterns" rather than an error.
     *
     * @param templateId the reconciliation template's TEMPLATE_ID, the one ER_DASHBOARD_STAT_ROWS
     *                   carries
     */
    List<PatternStat> findPatternHistory(int templateId);

    PatternRow findPattern(int patternId);

    /** Every pattern of the given templates, ordered by PATTERN_ID. */
    List<PatternRow> findPatternsByTemplates(Collection<Integer> templateIds);

    /**
     * Every pattern sharing {@code signature}, one per template, with its count on
     * {@code runId} - the engine's {@code QUERY_ER_PATTERN_BY_SIGNATURE}. Ordered by template
     * path.
     */
    List<PatternSighting> findPatternsBySignature(String signature, int runId);

    /**
     * Sets LINKED_PATTERN_ID on one pattern, or clears it with null - the engine's
     * {@code UPDATE_ER_PATTERN_LINK}. Returns the number of rows changed.
     *
     * <p>Only ever called on a user's request; the checks against a link to itself or a
     * cycle are the service's.
     */
    int updatePatternLink(int patternId, Integer linkedPatternId);

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
     * <p>Patterns are shared across runs too, so ER_DASHBOARD_PATTERN only loses the
     * patterns left with no occurrence row at all. One whose FIRST_SEEN_RUN is deleted but
     * that was counted on a later run is kept, its link included, and moved to the earliest
     * run that still counts it.
     *
     * @return how many rows were removed per table name
     */
    Map<String, Integer> deleteBatches(Collection<Integer> batchIds);
}
