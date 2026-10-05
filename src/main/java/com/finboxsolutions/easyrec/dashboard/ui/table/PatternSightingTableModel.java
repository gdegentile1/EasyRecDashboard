package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.PatternSighting;

import java.util.List;

/**
 * The templates one signature was found on, with their count on the selected run.
 *
 * <p>An empty "Occurrences" is a template the run has no stat row for - it did not reconcile
 * it, or skipped its export - which is not a zero.
 */
public class PatternSightingTableModel extends DashboardTableModel<PatternSighting> {

    private static final long serialVersionUID = 1L;

    private static final List<String> COLUMNS = List.of(
            "Template Path", "Template", "Pattern", "Occurrences", "First Seen Run");

    public PatternSightingTableModel() {
        super(COLUMNS);
    }

    public void setRows(List<PatternSighting> sightings) {
        setRecords(sightings);
    }

    public PatternSighting rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "Template", "Pattern", "First Seen Run" -> Integer.class;
            case "Occurrences" -> Long.class;
            default -> String.class;
        };
    }

    @Override
    protected Object valueOf(PatternSighting sighting, String column) {
        return switch (column) {
            case "Template Path" -> sighting.templatePath();
            case "Template" -> sighting.pattern().templateId();
            case "Pattern" -> sighting.pattern().patternId();
            case "Occurrences" -> sighting.occurrences();
            case "First Seen Run" -> sighting.pattern().firstSeenRun();
            default -> null;
        };
    }
}
