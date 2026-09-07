package com.finboxsolutions.easyrec.dashboard.ui.component;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;

import javax.swing.DefaultRowSorter;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import com.finboxsolutions.swing.jtable.excel.ExcelTable;
import com.finboxsolutions.swing.jtable.excel.ExcelTableModel;
import com.finboxsolutions.swing.jtable.renderers.zoom.ZoomRenderer;
import com.finboxsolutions.swing.jtable.renderers.zoom.ZoomWheelListener;
import com.finboxsolutions.viewer.JViewer;
import com.finboxsolutions.viewer.utils.TableViewUtils;

/**
 * The dashboard's table: EasyRec's own {@code ExcelTable}, set up the way
 * {@code UneditableExcelTable} sets one up.
 *
 * <p>This is the same grid the reconciliation screens and the pivot breakdown are built
 * from, so a dashboard table comes with the behaviour a user already has in their hands -
 * the Excel filter menu on each header with that column's distinct values, the sort
 * indicators, the column show and hide, and the status bar underneath when it sits in a
 * {@code JViewer}. None of that had to be written here; the previous {@code JXTable} could
 * only have imitated it.
 *
 * <p>Two things are set against the defaults:
 *
 * <ul>
 *   <li><b>Cell spanning is off.</b> {@code ExcelTable} merges equal neighbouring cells by
 *       default, which is right for a report and wrong here: on the batch list a run of
 *       merged cells would say two batches share a user by drawing them as one row, and the
 *       repetition it hides is exactly what a dashboard is read for.</li>
 *   <li><b>Grid lines are forced on</b> with a one-pixel intercell spacing, as
 *       {@code UneditableJXTable} forces them, because FlatLaf turns them off and these
 *       tables are read across a row.</li>
 * </ul>
 *
 * <p>Search is not wired here either: {@code JViewer}, which these tables sit in, already
 * opens {@code DialogTableSearch} on its own key handler.
 */
public class DashboardTable extends ExcelTable {

    private static final long serialVersionUID = 1L;

    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(DashboardTable.class);

    /** No single column may dominate the width, however long one template path is. */
    private static final int MAX_PACKED_WIDTH = 320;

    /** Room for a column's filter button and the header's own margins. */
    private static final int HEADER_PADDING = 38;

    /** Cells are measured over this many rows; beyond it the widest is near enough. */
    private static final int ROWS_MEASURED = 200;

    /**
     * Slack added to a measured cell.
     *
     * <p>A renderer's preferred width is the text plus its own border, and the table then
     * draws it inside the cell's margins. Measuring exactly is measuring a pixel or two
     * short, which is the difference between "batch.scheduler" and "batch.schedu...".
     */
    private static final int CELL_PADDING = 12;

    private transient JViewer viewer;
    private transient String viewPath;

    public DashboardTable(ExcelTableModel model) {
        super(model);
        applyDashboardDefaults();
    }

    /**
     * Not called {@code init}: {@code ExcelTable} has a protected {@code init} of its own
     * that its constructor runs, and a same-named method here would either override it -
     * losing the span UI, the header and the row sorter it installs - or fail to compile.
     */
    private void applyDashboardDefaults() {
        setSpanMode(SPAN_MODE_OFF);
        clearDefaultSort();
        applyDefaultNumberRenderers();
        applyFonts();
        setFillsViewportHeight(true);
        setRowHeight(Fonts.rowHeight());
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Force grid lines for FlatLaf.
        setShowGrid(true);
        setIntercellSpacing(new Dimension(1, 1));
    }

    /**
     * Sets the dashboard's type size on every renderer this table draws with.
     *
     * <p>Called again after every load, because the grid re-creates its header renderers
     * whenever the model changes its columns.
     */
    final void applyFonts() {
        Fonts.applyTo(this);
    }

    /**
     * Brings this table's own column renderers under the grid's zoom.
     *
     * <p>The grid wraps the renderers it knows about when the table is built; ours are
     * attached per column afterwards, so without this they are the only cells that do not
     * answer a ctrl-wheel zoom - and, if the row height ever differs from the theme's, the
     * only ones left at their original size while the rest grow.
     */
    void syncZoom() {
        ZoomWheelListener.installZoomRenderer(this);
    }

    /**
     * Numbers a column has not asked for a renderer for.
     *
     * <p>The grid formats every {@code Number} to two decimals, which is right for an amount
     * and wrong for a count: a reconciliation count reading "5.00" claims a precision the
     * figure does not have. Counts are whole and grouped, amounts keep their decimals, and a
     * column that wants something else still overrides both.
     */
    private void applyDefaultNumberRenderers() {
        setDefaultRenderer(Integer.class, Renderers.count());
        setDefaultRenderer(Long.class, Renderers.count());
        setDefaultRenderer(Double.class, Renderers.numeric(2, null));
    }

    /**
     * Leaves the rows in the order the model supplied them.
     *
     * <p>{@code ExcelRowSorter} is built with an ascending sort key on every column, so a
     * table adopting it sorts itself by its first column. Every one of these tables arrives
     * already ordered by something the screen means - batches newest first, comparison rows
     * by widest spread, column statistics worst-matching first - and that ordering is the
     * answer the screen was opened for. Clearing the keys keeps it, and leaves the header's
     * filter menu free to sort on demand.
     */
    private void clearDefaultSort() {
        RowSorter<?> sorter = getRowSorter();
        if (sorter != null) {
            sorter.setSortKeys(null);
        }
    }

    /**
     * Re-establishes the header after the model changed its columns.
     *
     * <p>{@code ExcelTable} decorates every column header with a {@code JXLabel} when it is
     * built, and that is what its filter menu looks for. A structure change makes Swing
     * rebuild the columns with plain string headers, and the filter icons quietly stop
     * appearing - so the comparison screen, the only one whose columns change, calls this
     * after each load.
     */
    public void structureChanged() {
        initHeaderRenderers();
    }

    /**
     * Sizes every column to the wider of its header and its content, capped.
     *
     * <p>Not {@code packAll}: the shared {@code TablePacker} measures a header whose value is
     * a {@code JLabel} - which is every header here, since {@code ExcelTable} decorates them -
     * by its character count rather than its pixel width, so "Match Rate" asks for ten
     * pixels and the header renders as "Mat...". Content is measured through the real
     * renderers, which is what makes a coloured percentage size correctly.
     */
    public void pack() {
        FontMetrics headerMetrics = getTableHeader().getFontMetrics(getTableHeader().getFont());
        int rowsToMeasure = Math.min(getRowCount(), ROWS_MEASURED);
        int total = 0;

        for (int index = 0; index < getColumnCount(); index++) {
            TableColumn column = getColumnModel().getColumn(index);
            int width = headerMetrics.stringWidth(getColumnName(index)) + HEADER_PADDING;

            for (int row = 0; row < rowsToMeasure; row++) {
                // Through prepareRenderer, so a column is measured in the type it is painted
                // in rather than the type its renderer was configured with.
                Component cell = prepareRenderer(getCellRenderer(row, index), row, index);
                width = Math.max(width, cell.getPreferredSize().width + CELL_PADDING);
            }
            int packed = Math.min(width, MAX_PACKED_WIDTH);
            column.setPreferredWidth(packed);
            total += packed;
        }
        fitOrScroll(total);
    }

    /**
     * Fills the viewport when the columns fit in it, scrolls when they do not.
     *
     * <p>The two resize modes each look wrong half the time: auto-resize squeezes the batch
     * list's seventeen columns into whatever the window is, and no auto-resize leaves the
     * five-column comparison stranded against a band of empty grey. Which one is right is a
     * question about this load's data, so it is answered on every load.
     */
    private void fitOrScroll(int packedWidth) {
        Container parent = getParent();
        int available = parent instanceof JViewport viewport
                ? viewport.getExtentSize().width
                : getWidth();
        setAutoResizeMode(packedWidth > available && available > 0
                ? JTable.AUTO_RESIZE_OFF
                : JTable.AUTO_RESIZE_ALL_COLUMNS);
    }

    /**
     * Turns sorting off for every column, keeping the filter menu.
     *
     * <p>The comparison screen orders its rows by spread, and that ordering is the point of
     * the screen; a click on a header that reordered them would throw it away.
     */
    public void setSortable(boolean sortable) {
        RowSorter<?> sorter = getRowSorter();
        if (sorter instanceof DefaultRowSorter<?, ?> defaultSorter) {
            for (int index = 0; index < getModel().getColumnCount(); index++) {
                defaultSorter.setSortable(index, sortable);
            }
        }
    }

    /**
     * Loads this table's view, if it has one and one can be found.
     *
     * <p>{@code TableViewUtils} looks on the filesystem first and then on the classpath under
     * {@code resources/}, which is how a deployment overrides a shipped view by dropping a
     * file beside the application. A view that is not there is not an error - the table keeps
     * the columns the model gave it - so a miss is logged and nothing else happens.
     *
     * <p>Applied once, as the table is built rather than on every load: a view is the default
     * layout, and re-applying it would undo a column the reader had widened or hidden.
     */
    void applyView() {
        if (viewPath == null || viewer == null) {
            return;
        }
        try {
            // Already on the EDT, so the renderers are set without another invokeLater.
            TableViewUtils.loadView(viewer, viewPath, false);
        } catch (Exception failure) {
            // A stale view names columns this table does not have. That costs the layout,
            // not the data, so the table is left as the model built it.
            LOG.warn("Could not apply the view [{}]", viewPath, failure);
        }
    }

    void setViewPath(String path) {
        this.viewPath = path;
    }

    /** The viewer this table was installed in, so a load can refresh its status bar. */
    void setViewer(JViewer installedIn) {
        this.viewer = installedIn;
    }

    /** Brings the viewer's row count and filter indicator up to date. */
    void refreshViewer() {
        if (viewer != null) {
            viewer.refreshStatusBar();
        }
    }

    /**
     * Settles the type size after every other renderer has had its say.
     *
     * <p>This is the last word before a cell is painted, which is the only place the size can
     * be made to hold: the grid's zoom wrapper sits between the column's renderer and here,
     * and rewrites the font on the way through. See {@link Fonts#sized}.
     */
    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        component.setFont(Fonts.sized(component.getFont(), baseSizeOf(renderer), getRowHeight()));
        return component;
    }

    /** A badge is set at its own size; everything else at the cell size. */
    private static float baseSizeOf(TableCellRenderer renderer) {
        TableCellRenderer target = renderer instanceof ZoomRenderer zoom
                ? zoom.getRendererDelegate()
                : renderer;
        return target instanceof StatusBadgeCellRenderer ? Fonts.badgeSize() : Fonts.cellSize();
    }

    /** Uneditable: the dashboard reads statistics, it does not write them. */
    @Override
    public void setValueAt(Object value, int row, int column) {
        // Deliberately empty, as in UneditableExcelTable.
    }
}
