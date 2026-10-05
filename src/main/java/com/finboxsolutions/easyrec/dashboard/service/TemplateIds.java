package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the TEMPLATE_ID a reconciliation's statistics are filed under.
 *
 * <p>The rule EasyRec follows: ER_DASHBOARD_RUN.TEMPLATE_ID is the project's main template, a
 * container with no statistics of its own; everything per template - ER_DASHBOARD_STAT_ROWS,
 * STAT_COLS, PIVOT, RUN_CONTEXT and PATTERN - carries the reconciliation template's id. So a
 * template screen takes its id from the context row or the statistics, never from the run,
 * and the context id normally <em>is</em> the statistics id.
 *
 * <p>Databases written by earlier engines filed the statistics one id along from the context
 * row, on a second ER_DASHBOARD_TEMPLATE row sharing FULL_PATH and KEY_COLUMNS. The
 * {@code +1} pairing is kept as a fallback for those, and only taken when the neighbouring id
 * is not a reconciliation of the same run in its own right: on current data, a template with
 * no statistics row (it failed, say) must not borrow the next template's.
 */
public final class TemplateIds {

    /** How far along the statistics id sat from the context id on older databases. */
    public static final int STATS_TEMPLATE_OFFSET = 1;

    private TemplateIds() {
    }

    /**
     * @param statsTemplateIds the TEMPLATE_IDs that ER_DASHBOARD_STAT_ROWS holds for this run
     * @return the id to query statistics with, or the input when neither candidate exists
     */
    public static Integer resolve(Integer contextTemplateId, Set<Integer> statsTemplateIds) {
        return resolve(contextTemplateId, statsTemplateIds, Set.of());
    }

    /**
     * @param statsTemplateIds   the TEMPLATE_IDs that ER_DASHBOARD_STAT_ROWS holds for this run
     * @param contextTemplateIds the TEMPLATE_IDs of this run's context rows, so the fallback
     *                           never pairs a template with another reconciliation of the run
     * @return the id to query statistics with, or the input when neither candidate exists
     */
    public static Integer resolve(Integer contextTemplateId, Set<Integer> statsTemplateIds,
                                  Set<Integer> contextTemplateIds) {
        if (contextTemplateId == null) {
            return null;
        }
        if (statsTemplateIds.contains(contextTemplateId)) {
            return contextTemplateId;
        }
        int paired = contextTemplateId + STATS_TEMPLATE_OFFSET;
        return statsTemplateIds.contains(paired) && !contextTemplateIds.contains(paired)
                ? paired : contextTemplateId;
    }

    /** The statistics template ids present per run, for use with {@link #resolve}. */
    public static Map<Integer, Set<Integer>> statsTemplateIdsByRun(List<RowStats> stats) {
        Map<Integer, Set<Integer>> byRun = new HashMap<>();
        for (RowStats row : stats) {
            if (row.templateId() != null) {
                byRun.computeIfAbsent(row.runId(), key -> new HashSet<>()).add(row.templateId());
            }
        }
        return byRun;
    }

    /** The context template ids present per run, for use with {@link #resolve}. */
    public static Map<Integer, Set<Integer>> contextTemplateIdsByRun(List<RunContextRow> contexts) {
        Map<Integer, Set<Integer>> byRun = new HashMap<>();
        for (RunContextRow row : contexts) {
            if (row.templateId() != null) {
                byRun.computeIfAbsent(row.runId(), key -> new HashSet<>()).add(row.templateId());
            }
        }
        return byRun;
    }
}
