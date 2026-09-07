package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.service.ContextValues;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;

import java.util.ArrayList;
import java.util.List;

/**
 * The reconciliations of one batch: one row per ER_DASHBOARD_RUN_CONTEXT entry.
 *
 * <p>A batch holds a single run, so listing runs would say nothing; these rows are what the
 * batch screen drills into.
 */
public class ReconciliationTableModel
        extends DashboardTableModel<DashboardService.Reconciliation> {

    private static final long serialVersionUID = 1L;

    private static final List<String> FIXED_LEADING = List.of("Template ID", "Template Path");
    private static final List<String> FIXED_TRAILING = List.of("Status", "Match Rate");

    public ReconciliationTableModel() {
        super(allColumns());
    }

    private static List<String> allColumns() {
        List<String> all = new ArrayList<>(FIXED_LEADING);
        all.addAll(DashboardService.PROJECT_COLUMNS);
        all.addAll(FIXED_TRAILING);
        return all;
    }

    public void setRows(List<DashboardService.Reconciliation> reconciliations) {
        setRecords(reconciliations);
    }

    public DashboardService.Reconciliation rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "Template ID" -> Integer.class;
            case "Match Rate" -> Double.class;
            case "Status" -> StatusLabel.class;
            default -> String.class;
        };
    }

    @Override
    protected Object valueOf(DashboardService.Reconciliation rec, String column) {
        return switch (column) {
            case "Template ID" -> rec.templateId();
            case "Template Path" -> rec.templatePath() == null ? "-" : rec.templatePath();
            case "Project" -> ContextValues.display(rec.context().name());
            case "Source" -> ContextValues.display(rec.context().sourceLabel());
            case "Target" -> ContextValues.display(rec.context().targetLabel());
            case "Category 1" -> ContextValues.display(rec.context().category1());
            case "Category 2" -> ContextValues.display(rec.context().category2());
            case "Category 3" -> ContextValues.display(rec.context().category3());
            case "Status" -> rec.status();
            case "Match Rate" -> rec.matchRate();
            default -> "-";
        };
    }
}
