package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;

import java.util.List;

/** The runs behind a pattern's trend chart, newest first. */
public class PatternHistoryTableModel extends DashboardTableModel<PatternService.PatternPoint> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "When", "Batch", "Run", "Trend", "Occurrences", "Change", "% of Rows");

    public PatternHistoryTableModel() {
        super(COLUMNS);
    }

    public void setRows(List<PatternService.PatternPoint> points) {
        setRecords(points);
    }

    public PatternService.PatternPoint rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "When" -> String.class;
            case "Batch", "Run" -> Integer.class;
            case "Trend" -> PatternTrend.class;
            case "Occurrences", "Change" -> Long.class;
            default -> Double.class;
        };
    }

    @Override
    protected Object valueOf(PatternService.PatternPoint point, String column) {
        return switch (column) {
            case "When" -> point.longLabel();
            case "Batch" -> point.batch() == null ? null : point.batch().batchId();
            case "Run" -> point.runId();
            case "Trend" -> point.line().trend();
            case "Occurrences" -> point.line().occurrences();
            case "Change" -> point.line().change();
            case "% of Rows" -> point.line().shareOfRows();
            default -> null;
        };
    }
}
