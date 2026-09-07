package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.model.RowStats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the TEMPLATE_ID a run's statistics are actually filed under.
 *
 * <p>ER_DASHBOARD_TEMPLATE holds two rows per reconciliation, sharing FULL_PATH and
 * KEY_COLUMNS on consecutive ids, and the two are referenced inconsistently:
 * ER_DASHBOARD_RUN_CONTEXT points at the first, while ER_DASHBOARD_STAT_ROWS and
 * ER_DASHBOARD_STAT_COLS point at the second. Statistics for a context row therefore live
 * one id along.
 *
 * <p>An exact match is always preferred, so a deployment that writes the two tables
 * consistently keeps working and the offset stays a fallback rather than an assumption.
 */
public final class TemplateIds {

    /** How far along the statistics id sits from the context id. */
    public static final int STATS_TEMPLATE_OFFSET = 1;

    private TemplateIds() {
    }

    /**
     * @param statsTemplateIds the TEMPLATE_IDs that ER_DASHBOARD_STAT_ROWS holds for this run
     * @return the id to query statistics with, or the input when neither candidate exists
     */
    public static Integer resolve(Integer contextTemplateId, Set<Integer> statsTemplateIds) {
        if (contextTemplateId == null) {
            return null;
        }
        if (statsTemplateIds.contains(contextTemplateId)) {
            return contextTemplateId;
        }
        int paired = contextTemplateId + STATS_TEMPLATE_OFFSET;
        return statsTemplateIds.contains(paired) ? paired : contextTemplateId;
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
}
