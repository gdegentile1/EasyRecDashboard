package com.finboxsolutions.easyrec.dashboard.ui.table;

import com.finboxsolutions.easyrec.dashboard.service.CompareService;

import java.util.ArrayList;
import java.util.List;

/**
 * A comparison table: one row per template path or column label, one column per batch.
 *
 * <p>Spread and delta sit at the right-hand end rather than the left because the batch
 * columns are what the eye scans first; the two summary columns are the answer to "which
 * of these moved", read after.
 *
 * <p>The only dashboard model whose columns change after construction - a comparison of four
 * batches has four of them - so it is also the only one whose table has to rebuild its
 * header and renderers on every load.
 */
public class CompareTableModel extends DashboardTableModel<CompareService.CompareRow> {

    private static final long serialVersionUID = 1L;

    /** What a cell shows when a batch has a row: the comparison is not always about rates. */
    public enum CellMeasure { MATCH_RATE, MATCH_PERCENTAGE }

    private static final String SPREAD = "Spread";
    private static final String DELTA = "Delta";

    private final transient List<Integer> batchIds = new ArrayList<>();
    private String labelColumn = "Template Path";
    private CellMeasure measure = CellMeasure.MATCH_RATE;

    public CompareTableModel() {
        super(List.of("Template Path", SPREAD, DELTA));
    }

    public void setRows(String label, List<Integer> selectedBatchIds,
                        List<CompareService.CompareRow> compareRows, CellMeasure cellMeasure) {
        this.labelColumn = label;
        this.measure = cellMeasure;
        batchIds.clear();
        batchIds.addAll(selectedBatchIds);

        List<String> columns = new ArrayList<>();
        columns.add(label);
        for (Integer batchId : selectedBatchIds) {
            columns.add("Batch " + batchId);
        }
        columns.add(SPREAD);
        columns.add(DELTA);
        setColumns(columns);

        setRecords(compareRows);
    }

    public CompareService.CompareRow rowAt(int row) {
        return recordAt(row);
    }

    /** The batch a column belongs to, or null for the label, spread and delta columns. */
    public Integer batchAt(int column) {
        int index = column - 1;
        return index >= 0 && index < batchIds.size() ? batchIds.get(index) : null;
    }

    public CellMeasure measure() {
        return measure;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return column == 0 ? String.class : Double.class;
    }

    @Override
    protected Object valueOf(CompareService.CompareRow row, String column) {
        if (column.equals(labelColumn)) {
            return row.label();
        }
        if (SPREAD.equals(column)) {
            return row.spread();
        }
        if (DELTA.equals(column)) {
            return row.delta();
        }
        // A null cell means this batch has no such row at all, which is different from a
        // row it has with no measurable value; the renderer distinguishes the two.
        CompareService.Cell cell = row.cells().get(columns().indexOf(column) - 1);
        return cell == null ? null : cell.value();
    }

    /** True when the batch in this column did not have this row at all. */
    public boolean isAbsent(int row, int column) {
        Integer batchId = batchAt(column);
        return batchId != null && recordAt(row).cells().get(column - 1) == null;
    }

    public String labelColumn() {
        return labelColumn;
    }
}
