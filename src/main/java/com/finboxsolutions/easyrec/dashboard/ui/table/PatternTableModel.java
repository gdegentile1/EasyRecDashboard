package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;

import java.util.List;

/** The patterns of one reconciliation, most occurrences first, with their movement. */
public class PatternTableModel extends DashboardTableModel<PatternService.PatternLine> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "Pattern", "Trend", "Occurrences", "Change", "% of Rows", "Previous",
            "First Seen Run", "Root Cause", "Owner", "Ticket");

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
            case "Occurrences", "Change", "Previous" -> Long.class;
            case "% of Rows" -> Double.class;
            case "First Seen Run" -> Integer.class;
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
            case "First Seen Run" -> pattern.firstSeenRun();
            case "Root Cause" -> orDash(pattern.rootCause());
            case "Owner" -> orDash(pattern.ownerName());
            case "Ticket" -> orDash(pattern.ticketRef());
            default -> null;
        };
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
