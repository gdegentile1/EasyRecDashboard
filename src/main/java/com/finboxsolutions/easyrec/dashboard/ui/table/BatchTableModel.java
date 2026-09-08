package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.Rates;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The batch list and the home screen's recent-batches table. */
public class BatchTableModel extends DashboardTableModel<DashboardService.BatchSummary> {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * The columns whose position is decided here, in display order.
     *
     * <p>Project and Description follow the match rate rather than trailing the counts: they
     * are what names the row. A reader scanning the list is looking for a particular night's
     * run of a particular project, and having to cross four numeric columns to find out which
     * one a row is makes the identifying columns the hardest to read.
     *
     * <p>{@link #SOURCE_ROWS} and {@link #TARGET_ROWS} - the batch's whole volume on each
     * side - sit beside Passed and Failed because they answer the question those two raise:
     * a 99% match rate over 12 rows and the same rate over two million are not the same
     * result, and the two sides differing is itself the first sign of a feed problem.
     *
     * <p>Project is one of {@link DashboardService#RUN_COLUMNS} and is still folded from the
     * runs like the rest of them; naming it here moves it without taking it out of that set.
     */
    private static final List<String> LEADING_COLUMNS = List.of(
            "Batch", "Date", "Time", "User", "Status", "Match Rate", "Project", "Description",
            "Passed", "Failed", SOURCE_ROWS, TARGET_ROWS);

    /**
     * How long the batch took, kept last.
     *
     * <p>It sat between the counts and the description, in the middle of what the reader is
     * scanning - and it is the one figure on the row that is never the reason for opening a
     * batch. A run that took four minutes rather than three is worth knowing after the fact,
     * not while looking for the failures, so it goes to the end where a timing belongs.
     */
    private static final String DURATION_COLUMN = "Duration";

    public BatchTableModel() {
        super(allColumns());
    }

    private static List<String> allColumns() {
        List<String> all = new ArrayList<>(LEADING_COLUMNS);
        // Whatever LEADING_COLUMNS has already placed keeps that place; the rest follow in
        // their own order. That is what lets one run column be pulled forward without the
        // service and the model disagreeing about which run columns exist.
        for (String column : DashboardService.RUN_COLUMNS) {
            if (!all.contains(column)) {
                all.add(column);
            }
        }
        all.add(DURATION_COLUMN);
        return all;
    }

    public void setRows(List<DashboardService.BatchSummary> summaries) {
        setRecords(summaries);
    }

    public DashboardService.BatchSummary rowAt(int row) {
        return recordAt(row);
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return switch (columnName(column)) {
            case "Batch", "Passed", "Failed" -> Integer.class;
            case SOURCE_ROWS, TARGET_ROWS -> Long.class;
            case "Match Rate" -> Double.class;
            case "Status" -> StatusLabel.class;
            default -> String.class;
        };
    }

    @Override
    protected Object valueOf(DashboardService.BatchSummary summary, String column) {
        BatchRow batch = summary.batch();
        LocalDateTime when = batch.when();
        return switch (column) {
            case "Batch" -> batch.batchId();
            case "Date" -> when == null ? "-" : DATE.format(when);
            case "Time" -> when == null ? "-" : TIME.format(when);
            case "User" -> batch.userName() == null ? "-" : batch.userName();
            case "Status" -> batch.status();
            case "Match Rate" -> Rates.fraction(summary.matchRate());
            case "Passed" -> summary.reconciliationsPassed();
            case "Failed" -> summary.reconciliationsFailed();
            case SOURCE_ROWS -> summary.sourceRows();
            case TARGET_ROWS -> summary.targetRows();
            case "Duration" -> duration(batch.durationMillis());
            case "Description" -> preview(batch.description());
            default -> summary.runCells().getOrDefault(column, "-");
        };
    }

    /** Seconds, minutes or hours, whichever keeps the figure readable. */
    private static String duration(Long millis) {
        if (millis == null || millis <= 0L) {
            return "-";
        }
        double seconds = millis / 1000.0d;
        if (seconds < 60.0d) {
            return String.format(Locale.ROOT, "%.1fs", seconds);
        }
        double minutes = seconds / 60.0d;
        if (minutes < 60.0d) {
            return String.format(Locale.ROOT, "%.1fmin", minutes);
        }
        return String.format(Locale.ROOT, "%.1fh", minutes / 60.0d);
    }

    /** How much of a description the table shows before truncating. */
    public static final int DESCRIPTION_PREVIEW_LENGTH = 60;

    /**
     * A description collapsed onto one line and truncated.
     *
     * <p>Descriptions may contain newlines, so whitespace is collapsed first: a table cell
     * shows one line, and the renderer keeps the full text in the tooltip.
     */
    public static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        String collapsed = text.trim().replaceAll("\\s+", " ");
        return collapsed.length() <= DESCRIPTION_PREVIEW_LENGTH
                ? collapsed
                : collapsed.substring(0, DESCRIPTION_PREVIEW_LENGTH - 1).stripTrailing() + "\u2026";
    }
}
