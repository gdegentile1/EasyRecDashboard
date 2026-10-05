package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;

import java.util.List;

/**
 * The patterns of one reconciliation, most occurrences first, with their movement.
 *
 * <p>"Column Unexplained" is filled for a pattern not detected on the run only: it is the
 * count of its column's unexplained pattern on the same run, where a rule's breaks go once
 * it falls below the engine's support floor. Shown beside the zero so a zero is not read as
 * a fix.
 */
public class PatternTableModel extends DashboardTableModel<PatternService.PatternLine> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "Pattern", "Trend", "Occurrences", "Change", "% of Rows", "Previous",
            "Column Unexplained", "First Seen Run", "Linked To");

    public PatternTableModel() {
        super(COLUMNS);
    }

    public void setRows(List<PatternService.PatternLine> lines) {
        setRecords(lines);
    }

    public PatternService.PatternLine rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "Trend" -> PatternTrend.class;
            case "Occurrences", "Change", "Previous", "Column Unexplained" -> Long.class;
            case "% of Rows" -> Double.class;
            case "First Seen Run", "Linked To" -> Integer.class;
            default -> String.class;
        };
    }

    @Override
    protected Object valueOf(PatternService.PatternLine line, String column) {
        PatternRow pattern = line.pattern();
        return switch (column) {
            case "Pattern" -> pattern.label();
            case "Trend" -> line.trend();
            case "Occurrences" -> line.occurrences();
            case "Change" -> line.change();
            case "% of Rows" -> line.shareOfRows();
            case "Previous" -> line.previous();
            case "Column Unexplained" -> line.unexplainedOnColumn();
            case "First Seen Run" -> pattern.firstSeenRun();
            case "Linked To" -> pattern.linkedPatternId();
            default -> null;
        };
    }
}
