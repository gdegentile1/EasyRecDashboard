package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.easyrec.dashboard.ui.table.DashboardTableModel;
import com.finboxsolutions.viewer.JViewer;

import javax.swing.JPanel;
import javax.swing.table.TableCellRenderer;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;

/** How every dashboard table is set up, so they all behave the same way. */
public final class Tables {

    private static final org.apache.logging.log4j.Logger LOG =
            org.apache.logging.log4j.LogManager.getLogger(Tables.class);

    private Tables() {
    }

    /**
     * A dashboard table over one of the {@link DashboardTableModel}s.
     *
     * <p>The column control that used to matter here - the reconciliation table has ten
     * columns and the pivot table can carry twenty-one - is now the grid's own: every header
     * carries a filter menu that hides, shows and filters by that column's distinct values.
     */
    public static DashboardTable create(DashboardTableModel<?> model) {
        return new DashboardTable(model);
    }

    /**
     * A table in the viewer, inside the titled section every dashboard screen puts one in.
     *
     * <p>{@link JViewer} is what turns the grid into a screen: the scroll pane that keeps
     * the header in view, the status bar with the row count and the export and
     * clear-filters actions, and the search dialog on Ctrl-F. It is the same wrapper
     * {@code PanelPivot} puts round the pivot breakdown.
     */
    public static JPanel section(String title, DashboardTable table) {
        return Sections.createFilledSection(title, viewer(table));
    }

    /** The viewer round a table, without the section frame. */
    public static JViewer viewer(DashboardTable table) {
        JViewer viewer = new JViewer();
        try {
            // enableTableMenu adds the right-click column menu; refreshTable would apply
            // filters and spans to a table that has no rows yet, so it waits for the load.
            viewer.setTable(table, true, false);
        } catch (Exception failure) {
            // JViewer's own constructor answers this with a modal error dialog, which would
            // fire while the dashboard tab is still being built. A table in a bare viewer is
            // still a readable table.
            LOG.error("Could not install the dashboard table in its viewer", failure);
        }
        // The resize mode the scroll pane chose is revisited on every load, once the packed
        // widths are known; see DashboardTable.pack.
        table.setViewer(viewer);
        return viewer;
    }

    /** Runs {@code action} with the model row index when a row is double-clicked. */
    public static void onRowActivated(DashboardTable table, IntConsumer action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2 || event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                int viewRow = table.rowAtPoint(event.getPoint());
                if (viewRow >= 0) {
                    // The view row is not the model row once a sort or a filter is applied,
                    // and these tables are sorted by default.
                    action.accept(table.convertRowIndexToModel(viewRow));
                }
            }
        });
    }

    /** Applies a renderer to a column looked up by header name, ignoring unknown names. */
    public static void renderer(DashboardTable table, String columnName, TableCellRenderer renderer) {
        for (int index = 0; index < table.getColumnCount(); index++) {
            if (columnName.equals(table.getColumnName(index))) {
                table.getColumnModel().getColumn(index).setCellRenderer(renderer);
                return;
            }
        }
    }

    /**
     * What every screen calls once a load has landed: size the columns, update the count.
     *
     * <p>The count is not automatic. {@code JViewer} refreshes its status bar from a
     * {@code TableModelListener} it registers when the table is installed, and Swing
     * notifies that listener before the table itself has taken the change - so it reads the
     * row count the table had a moment ago, which for a screen that loads after it is built
     * is zero, forever.
     */
    public static void refresh(DashboardTable table) {
        table.applyFonts();
        table.syncZoom();
        table.pack();
        table.refreshViewer();
    }
}
