package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;

import java.util.List;

/** The per-column statistics of one reconciliation, worst-matching first. */
public class ColumnStatsTableModel extends DashboardTableModel<ColumnStats> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "Column", "Class", "Tolerance", "Match %", "Unmatched", "Exact", "In Tolerance",
            "Forced", "Impact", "Impact (Abs)", "Average", "Std Dev",
            "Min Diff", "Max Diff", "Min Diff %", "Max Diff %");

    public ColumnStatsTableModel() {
        super(COLUMNS);
    }

    public void setRows(List<ColumnStats> stats) {
        setRecords(stats);
    }

    public ColumnStats rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "Column", "Class", "Tolerance" -> String.class;
            case "Unmatched", "Exact", "In Tolerance", "Forced" -> Long.class;
            default -> Double.class;
        };
    }

    @Override
    protected Object valueOf(ColumnStats stats, String column) {
        return switch (column) {
            case "Column" -> stats.colLabel();
            case "Class" -> stats.colClass() == null ? "-" : stats.colClass();
            case "Tolerance" -> stats.colTolerance() == null ? "-" : stats.colTolerance();
            // Stored as a ratio; scaled here for display only.
            // COL_MATCH_PCT is stored as a ratio, so this column needs no conversion at
            // all - it is the one figure in the application that was already in the units
            // the renderer wants. matchPercentage() scales it for the captions instead.
            case "Match %" -> stats.colMatchPct();
            case "Unmatched" -> stats.colNbUnmatch();
            case "Exact" -> stats.colNbExactMatch();
            case "In Tolerance" -> stats.colNbToleranceMatch();
            case "Forced" -> stats.colNbForceMatch();
            case "Impact" -> stats.colImpact();
            case "Impact (Abs)" -> stats.colImpactAbs();
            case "Average" -> stats.colAverage();
            case "Std Dev" -> stats.colStdDeviation();
            case "Min Diff" -> stats.colMinDiffAbs();
            case "Max Diff" -> stats.colMaxDiffAbs();
            case "Min Diff %" -> stats.colMinDiffPct();
            case "Max Diff %" -> stats.colMaxDiffPct();
            default -> null;
        };
    }
}
