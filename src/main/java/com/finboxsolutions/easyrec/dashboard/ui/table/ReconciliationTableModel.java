package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.service.ContextValues;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.Rates;

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

    /**
     * What the row is, and then how it turned out.
     *
     * <p>The verdict sits against the template path rather than at the end of the row. The
     * reason to open a batch is to find out which of its templates failed, and that question
     * is answered by two columns which were separated from the name by five context columns
     * the reader has to cross to pair them up.
     */
    private static final List<String> FIXED_LEADING =
            List.of("Template ID", "Template Path", "Status", "Match Rate");

    /**
     * The volume, at the end.
     *
     * <p>How many rows came in on each side is what the match rate above was computed over -
     * background to the verdict rather than part of it, and read once the verdict has raised
     * the question.
     */
    private static final List<String> FIXED_TRAILING = List.of(SOURCE_ROWS, TARGET_ROWS);

    public ReconciliationTableModel() {
        super(allColumns());
    }

    private static List<String> allColumns() {
        List<String> all = new ArrayList<>(FIXED_LEADING);
        all.addAll(DashboardService.CONTEXT_COLUMNS);
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
            case SOURCE_ROWS, TARGET_ROWS -> Long.class;
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
            case "Source" -> ContextValues.display(rec.context().sourceLabel());
            case "Target" -> ContextValues.display(rec.context().targetLabel());
            case "Category 1" -> ContextValues.display(rec.context().category1());
            case "Category 2" -> ContextValues.display(rec.context().category2());
            case "Category 3" -> ContextValues.display(rec.context().category3());
            // Null, not zero, when the reconciliation recorded no statistics: it did not
            // read nothing, it did not report. Match Rate beside it answers the same way, and
            // the grid's ObjectComparator sorts nulls to one end rather than throwing.
            case SOURCE_ROWS -> rec.stats() == null ? null : Long.valueOf(rec.stats().rowsSource());
            case TARGET_ROWS -> rec.stats() == null ? null : Long.valueOf(rec.stats().rowsTarget());
            case "Status" -> rec.status();
            case "Match Rate" -> Rates.fraction(rec.matchRate());
            default -> "-";
        };
    }
}
