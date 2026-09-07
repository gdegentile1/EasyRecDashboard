package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.service.HistoryService;

import java.util.List;

/** The executions behind a trend chart, newest first, with their movement. */
public class HistoryTableModel extends DashboardTableModel<HistoryService.Execution> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "When", "Batch", "Reconciliations", "Match Rate", "Rate Change",
            "Breaks", "Breaks Change", "Passed", "Failed");

    public HistoryTableModel() {
        super(COLUMNS);
    }

    public void setRows(List<HistoryService.Execution> executions) {
        setRecords(executions);
    }

    public HistoryService.Execution rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "When" -> String.class;
            case "Batch", "Reconciliations", "Passed", "Failed" -> Integer.class;
            case "Breaks", "Breaks Change" -> Long.class;
            default -> Double.class;
        };
    }

    @Override
    protected Object valueOf(HistoryService.Execution execution, String column) {
        return switch (column) {
            case "When" -> execution.longLabel();
            case "Batch" -> execution.batch().batchId();
            case "Reconciliations" -> execution.reconciliations();
            case "Match Rate" -> execution.matchRate();
            case "Rate Change" -> execution.rateDelta();
            case "Breaks" -> execution.breaks();
            case "Breaks Change" -> execution.breaksDelta();
            case "Passed" -> execution.passed();
            case "Failed" -> execution.failed();
            default -> null;
        };
    }

    /** True when this row is the batch the history was opened from. */
    public boolean isCurrent(int row) {
        return recordAt(row).current();
    }
}
