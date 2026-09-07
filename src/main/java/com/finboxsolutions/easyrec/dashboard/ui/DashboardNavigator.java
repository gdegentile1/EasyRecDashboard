package com.finboxsolutions.easyrec.dashboard.ui;

import java.util.List;

/**
 * The screens the dashboard can move between.
 *
 * <p>These are the routes the web dashboard exposed as URLs. Naming them in one interface
 * keeps every view free of any knowledge of the others: a table row that drills down calls
 * a method here, not a constructor.
 */
public interface DashboardNavigator {

    void showHome();

    void showBatchList();

    void showBatchDetail(int batchId);

    /** One reconciliation, always scoped to a template: a run covers many at once. */
    /**
     * @param templateName what to call the reconciliation in the trail, or null for its id.
     *                     The caller passes it because it already has it; looking it up here
     *                     would mean a query on the EDT to name a breadcrumb.
     */
    void showReconciliation(int runId, int templateId, String templateName);

    /** This batch's whole reconciliation suite over time. */
    void showBatchHistory(int batchId);

    /** Every execution of one template. */
    void showTemplateHistory(int templateId);

    void showCompare(List<Integer> batchIds);

    /** Column-level comparison of a single template path across the same selection. */
    void showTemplateCompare(List<Integer> batchIds, String templatePath);

    /** Returns to the previous screen, if there is one. */
    void goBack();
}
