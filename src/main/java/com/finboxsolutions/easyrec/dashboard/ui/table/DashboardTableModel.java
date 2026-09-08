package com.finboxsolutions.easyrec.dashboard.ui.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import com.finboxsolutions.swing.jtable.excel.ExcelTableModel;

/**
 * What every dashboard table model is: an {@link ExcelTableModel} over a list of records.
 *
 * <p>The dashboard's tables are EasyRec's own {@code ExcelTable}, and that table is built on
 * {@code ExcelTableModel} - a {@code DefaultTableModel} that holds its cells rather than
 * computing them. Its filter menu offers the distinct values of a column, its row sorter
 * reads the stored objects, and its span manager sizes cell attributes to the data; none of
 * that works against a model that answers {@code getValueAt} on demand.
 *
 * <p>So the cells are materialised once per load, by {@link #valueOf}, and the records that
 * produced them are kept alongside. That list is what {@link #recordAt} returns, which is
 * how a double-clicked row still leads back to the batch or reconciliation behind it.
 *
 * @param <T> the record behind one row
 */
public abstract class DashboardTableModel<T> extends ExcelTableModel {

    private static final long serialVersionUID = 1L;

    /**
     * A heading two models share, so the same measure is not called two things.
     *
     * <p>ER_DASHBOARD_STAT_ROWS.ROWS_SOURCE and ROWS_TARGET: the batch list totals them over
     * a batch, the batch screen breaks the same total down per template. A reader moving
     * between the two screens is looking at one number twice, and it has to be spelled the
     * same way in both places for that to be apparent.
     */
    public static final String SOURCE_ROWS = "Source Rows";
    public static final String TARGET_ROWS = "Target Rows";

    private final transient List<T> records = new ArrayList<>();
    private final List<String> columns = new ArrayList<>();

    protected DashboardTableModel(List<String> columnNames) {
        setColumns(columnNames);
    }

    /**
     * Declares the columns. Fires a structure change, so a table showing this model has to
     * re-apply its header renderers and cell renderers afterwards - see
     * {@code DashboardTable.structureChanged}.
     *
     * <p>Only the comparison changes its columns after construction; every other model calls
     * this once and then only ever replaces rows, which is what lets a column width, a sort
     * and a filter survive a reload.
     */
    protected final void setColumns(List<String> columnNames) {
        columns.clear();
        columns.addAll(columnNames);
        setColumnIdentifiers(new Vector<>(columnNames));
    }

    /** The name of a column by model index. */
    protected final String columnName(int index) {
        return columns.get(index);
    }

    protected final List<String> columns() {
        return columns;
    }

    /** One cell: the value of {@code column} for {@code record}. */
    protected abstract Object valueOf(T record, String column);

    /** Replaces every row, leaving the columns - and so the table's own state - alone. */
    protected final void setRecords(List<T> newRecords) {
        records.clear();
        records.addAll(newRecords);

        Vector<Vector<Object>> data = new Vector<>(newRecords.size());
        for (T record : newRecords) {
            Vector<Object> row = new Vector<>(columns.size());
            for (String column : columns) {
                row.add(valueOf(record, column));
            }
            data.add(row);
        }
        replaceRows(data);
    }

    /**
     * Swaps the data vector in one go.
     *
     * <p>{@code setDataVector} would fire a structure change and {@code addRow} an event per
     * row - four thousand of them for one reconciliation's column statistics. The cell
     * attributes are re-created because the span manager indexes them by row, and a stale
     * one sized to the previous load is how that manager goes out of bounds.
     */
    @SuppressWarnings("unchecked")
    private void replaceRows(Vector<Vector<Object>> data) {
        dataVector.clear();
        dataVector.addAll(data);
        initCellAttribute();
        fireTableDataChanged();
    }

    /** The record behind a model row. */
    public final T recordAt(int row) {
        return records.get(row);
    }

    protected final List<T> records() {
        return records;
    }

    /**
     * Never editable.
     *
     * <p>{@code setValueAt} is swallowed as well, but a cell that reports itself editable
     * still opens an editor on the second click - and a double-click on these tables means
     * "open this row", not "edit this cell".
     */
    @Override
    public final boolean isCellEditable(int row, int column) {
        return false;
    }
}
