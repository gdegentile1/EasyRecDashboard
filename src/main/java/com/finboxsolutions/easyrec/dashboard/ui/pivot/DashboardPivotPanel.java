package com.finboxsolutions.easyrec.dashboard.ui.pivot;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.finboxsolutions.common.gui.utils.ComponentUtils;
import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.ui.component.Fonts;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;
import com.finboxsolutions.swing.treetable.ExcelTreeTable;
import com.finboxsolutions.swing.treetable.model.FilterableExcelTreeTableModel;
import com.finboxsolutions.viewer.JViewer;
import com.finboxsolutions.viewer.utils.TableViewUtils;

/**
 * The stored pivot breakdown, shown with EasyRec's own pivot component.
 *
 * <p>This is deliberately not a new pivot table. It is {@code PanelPivotActionBar} over
 * {@code ExcelTreeTable} inside a {@code JViewer}, laid out and wired exactly as
 * {@code PanelPivot} does, so the breakdown a user reads on the dashboard looks and behaves
 * like the one they read on a live reconciliation: the same level buttons, the same
 * expand and collapse, the same search box, the same view files.
 *
 * <p>What differs is where the numbers come from. A live pivot is recomputed from the
 * reconciliation in memory; this one was computed when the run happened and read back out
 * of ER_DASHBOARD_PIVOT. Four of the action bar's commands depend on recomputing and are
 * therefore disabled here rather than silently doing nothing:
 *
 * <ul>
 *   <li><b>Edit Pivot</b> and <b>Include Pivot</b> change the breakdown, which would mean
 *       re-running the reconciliation.</li>
 *   <li><b>Apply Tolerances</b> changes how rows are matched, likewise.</li>
 *   <li><b>Refresh</b> and <b>Clear Filters</b> act on a template that is being run; the
 *       dashboard is looking at one that already ran.</li>
 * </ul>
 *
 * <p><b>Show Breaks</b> and <b>Export to Excel</b> are disabled for a different reason: both
 * reach for objects only a live session has - the diff table and the {@code TemplateBean}.
 * Both could be re-enabled, see the notes on {@link #setBreakHandler}.
 */
public class DashboardPivotPanel extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    /** The same view files the live pivot screen uses, so the columns render identically. */
    private static final String VIEW_RELATIVE = "views/break_statistics_amount.xml";
    private static final String VIEW_PERCENT = "views/break_statistics_percent.xml";

    /** Commands whose meaning depends on recomputing the reconciliation. */
    private static final Set<String> UNSUPPORTED_COMMANDS = Set.of(
            PanelPivotActionBar.COMMAND_EDIT_PIVOT,
            PanelPivotActionBar.COMMAND_INCLUDE_PIVOT,
            PanelPivotActionBar.COMMAND_TOLERANCE,
            PanelPivotActionBar.COMMAND_REFRESH,
            PanelPivotActionBar.COMMAND_CLEAR_FILTERS,
            PanelPivotActionBar.COMMAND_TOTAL,
            PanelPivotActionBar.COMMAND_EXCEL,
            PanelPivotActionBar.COMMAND_BREAK);

    /** The highest level the action bar offers a button for. */
    private static final int MAX_LEVEL = 6;

    /** What "expand everything" is passed as, matching PanelPivot. */
    private static final int EXPAND_ALL = 99;

    private final transient org.apache.logging.log4j.Logger log =
            org.apache.logging.log4j.LogManager.getLogger(DashboardPivotPanel.class);

    private final PanelPivotActionBar actionBar;
    private final JViewer viewer;
    private final JLabel emptyLabel = new JLabel("No pivot breakdown recorded for this reconciliation.");

    private transient ExcelTreeTable treeTable;
    private transient PivotTreeBuilder.Tree tree =
            new PivotTreeBuilder.Tree(List.of(), 0, List.of());
    private transient List<PivotMetric> metrics = PivotMetric.DEFAULT_COLUMNS;
    private transient BreakHandler breakHandler;

    private String view = VIEW_RELATIVE;
    private int selectedLevel = 1;

    /** Opens the rows behind a pivot cell. See {@link #setBreakHandler}. */
    @FunctionalInterface
    public interface BreakHandler {
        void showBreaks(String pivotPath, String columnName);
    }

    public DashboardPivotPanel() {
        super(new BorderLayout());

        this.actionBar = new PanelPivotActionBar(this);
        this.treeTable = new ExcelTreeTable();
        this.viewer = new JViewer(treeTable);

        add(actionBar, BorderLayout.NORTH);
        add(viewer, BorderLayout.CENTER);

        actionBar.setSelectedLevel(selectedLevel);
        actionBar.getSearchTextField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent event) {
                applyFilter();
            }
        });
        disableUnsupportedCommands();
    }

    /**
     * Which measures the tree shows, in column order.
     *
     * <p>Set this before {@link #setTree}. The names become the column names, so a view file
     * only applies to columns whose names it knows: keep these aligned with what
     * {@code PivotBuilder} produces if the shipped view files are to select them.
     */
    public void setMetrics(List<PivotMetric> chosen) {
        this.metrics = List.copyOf(chosen);
    }

    /**
     * Shows one reconciliation's stored breakdown. Call on the EDT.
     *
     * <p>The tree arrives already folded, with synthesised parents summed and ratios
     * recomputed, so nothing here aggregates.
     */
    public void setTree(PivotTreeBuilder.Tree newTree) {
        this.tree = newTree;
        removeAll();
        add(actionBar, BorderLayout.NORTH);

        if (newTree.isEmpty()) {
            // An empty breakdown is normal - not every reconciliation has one - so it says so
            // rather than showing an empty grid that reads like a failed load.
            add(emptyLabel, BorderLayout.CENTER);
            setActionBarEnabled(false);
            revalidate();
            repaint();
            return;
        }

        setActionBarEnabled(true);
        FilterableExcelTreeTableModel model = DashboardPivotNodes.toTreeTableModel(newTree, metrics);
        this.treeTable = new DashboardPivotTable(model);
        try {
            // JViewer.setTable is declared to throw, so a failure here has to be handled
            // rather than propagated: setTree is called from a load callback that cannot
            // throw, and losing the breakdown should not take the whole screen down.
            viewer.setTable(treeTable);
        } catch (Exception failure) {
            log.error("Could not install the pivot table in the viewer", failure);
            add(emptyLabel, BorderLayout.CENTER);
            setActionBarEnabled(false);
            revalidate();
            repaint();
            return;
        }
        add(viewer, BorderLayout.CENTER);

        applyView();
        // After the view, which is what creates the columns this sizes.
        Fonts.applyTo(treeTable);
        expandTree(selectedLevel);
        revalidate();
        repaint();
    }

    /**
     * Wires the Show Breaks button, which is otherwise disabled.
     *
     * <p>The live screen answers this by filtering the diff table beside it. The dashboard
     * has no diff table: the rows behind a stored pivot cell are not in the database, only
     * their totals are. Reopening the reconciliation would be the way to offer this, which
     * is a decision for the host rather than for this panel, so it is left as a hook.
     */
    public void setBreakHandler(BreakHandler handler) {
        this.breakHandler = handler;
        setCommandEnabled(PanelPivotActionBar.COMMAND_BREAK, handler != null);
    }

    public ExcelTreeTable getTable() {
        return treeTable;
    }

    public JViewer getViewer() {
        return viewer;
    }

    // ------------------------------------------------------------------------- commands

    @Override
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();
        if (command == null || UNSUPPORTED_COMMANDS.contains(command)) {
            return;
        }
        try {
            if (PanelPivotActionBar.COMMAND_EXPAND.equals(command)) {
                expandTree(EXPAND_ALL);
            } else if (PanelPivotActionBar.COMMAND_COLLAPSE.equals(command)) {
                expandTree(0);
            } else if (PanelPivotActionBar.COMMAND_VIEW_PCT.equals(command)) {
                view = VIEW_PERCENT;
                applyView();
            } else if (PanelPivotActionBar.COMMAND_VIEW_RLT.equals(command)) {
                view = VIEW_RELATIVE;
                applyView();
            } else {
                int level = levelOf(command);
                if (level > 0) {
                    expandTree(level);
                }
            }
        } catch (RuntimeException failure) {
            log.error("Error while executing pivot command [{}]", command, failure);
        }
    }

    /** The level a LEVELn command asks for, or 0 when the command is not one of those. */
    private static int levelOf(String command) {
        for (int level = 1; level <= MAX_LEVEL; level++) {
            if (("Level" + level).equals(command)) {
                return level;
            }
        }
        return 0;
    }

    private void expandTree(int level) {
        if (level <= MAX_LEVEL) {
            selectedLevel = level;
            treeTable.expandTreeInBackground(selectedLevel);
            actionBar.setSelectedLevel(selectedLevel);
        } else {
            treeTable.expandAllInBackground();
        }
    }

    private void applyFilter() {
        if (treeTable.getTreeTableModel() instanceof FilterableExcelTreeTableModel filterable) {
            filterable.setFilter(actionBar.getSearchTextField().getText());
            // Filtering rebuilds the tree, which collapses it, so the chosen level is restored.
            expandTree(selectedLevel);
            actionBar.setSelectedLevel(selectedLevel);
        }
    }

    private void applyView() {
        try {
            // Already on the EDT here, so the view is loaded without another invokeLater.
            TableViewUtils.loadView(viewer, view, false);
        } catch (Exception failure) {
            // A missing or stale view file costs the column layout, not the data, so the
            // table is left as it is rather than the screen failing.
            log.warn("Could not apply pivot view [{}]", view, failure);
        }
    }

    // ------------------------------------------------------------------ action bar state

    /**
     * Greys out the commands the dashboard cannot honour.
     *
     * <p>Done through {@code PanelPivotActionBar}'s own getters, so the live pivot screen's
     * class is left untouched. Two buttons have no getter - Refresh and Show Breaks - so
     * they stay clickable and are ignored in {@link #actionPerformed} instead; adding
     * {@code getButtonRefresh()} and {@code getButtonViewBreak()} to the action bar would
     * let them be greyed out like the rest.
     *
     * <p>{@code setEnabledRecursive} rather than {@code setEnabled}: a FontIconButton is a
     * composite, and disabling the wrapper alone leaves the button inside it live.
     */
    private void disableUnsupportedCommands() {
        ComponentUtils.setEnabledRecursive(actionBar.getButtonEditPivot(), false);
        ComponentUtils.setEnabledRecursive(actionBar.getButtonExcelReport(), false);
        ComponentUtils.setEnabledRecursive(actionBar.getButtonClearFilters(), false);
        ComponentUtils.setEnabledRecursive(actionBar.getButtonSubTotals(), false);
        // Refresh and Show Breaks were left clickable-and-ignored for want of an accessor.
        // A command that does nothing when pressed reads as a broken screen; a greyed one
        // reads as a command this screen does not have.
        ComponentUtils.setEnabledRecursive(actionBar.getButtonRefresh(), false);
        ComponentUtils.setEnabledRecursive(actionBar.getButtonViewBreak(), false);
        ComponentUtils.setEnabledRecursive(actionBar.getButtonIncludePivot(), false);
        actionBar.getCheckboxApplyTolerances().setEnabled(false);

        // Belt and braces: anything else carrying an unsupported command is greyed too.
        for (String command : UNSUPPORTED_COMMANDS) {
            setCommandEnabled(command, false);
        }
    }

    private void setActionBarEnabled(boolean enabled) {
        setCommandEnabled(PanelPivotActionBar.COMMAND_EXPAND, enabled);
        setCommandEnabled(PanelPivotActionBar.COMMAND_COLLAPSE, enabled);
        for (int level = 1; level <= MAX_LEVEL; level++) {
            setCommandEnabled("Level" + level, enabled);
        }
        actionBar.getSearchTextField().setEnabled(enabled);
    }

    private void setCommandEnabled(String command, boolean enabled) {
        for (AbstractButton button : buttonsOf(actionBar)) {
            if (command.equals(button.getActionCommand())) {
                button.setEnabled(enabled);
            }
        }
    }

    /** Every button under a container, however deeply the bar nests its panels. */
    private static List<AbstractButton> buttonsOf(Container container) {
        List<AbstractButton> found = new java.util.ArrayList<>();
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton button) {
                found.add(button);
            }
            if (child instanceof Container nested) {
                found.addAll(buttonsOf(nested));
            }
        }
        return found;
    }
}
