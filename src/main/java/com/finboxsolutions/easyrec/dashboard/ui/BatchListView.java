package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.common.gui.text.HintTextField;
import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.service.CompareService;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.table.BatchTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Every batch, narrowed by date, with the selection that feeds a comparison.
 *
 * <p>Date is the only filter the screen carries. User, Project, Source and Target used to
 * have dropdowns of their own; every one of them is a column of this table, and the grid's
 * header menus already filter a column by its distinct values - so the dropdowns were a
 * second, worse way to do what the column above them does, and one that cost a query
 * against ER_DASHBOARD_RUN_CONTEXT on first load.
 *
 * <p>Date could not go the same way. The table shows a batch's date as text, so a header
 * filter over it would pick values rather than a range, and SYS_DATE is an epoch-millisecond
 * string that {@link DashboardService#findBatchesInRange} has to read in Java anyway.
 */
public class BatchListView extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String DATE_HINT = "yyyy-mm-dd";

    /**
     * An explicit width on the date fields.
     *
     * <p>MigLayout shrinks a component below its preferred size before it lets a row
     * overflow, which had squeezed these two down to a few pixels.
     */
    private static final String DATE_WIDTH = "w 95!";

    private final DashboardDao dao;
    private final DashboardService service;
    private final DashboardNavigator navigator;

    private final HintTextField fromField = new HintTextField(DATE_HINT, 9);
    private final HintTextField toField = new HintTextField(DATE_HINT, 9);
    private final JButton compareButton = Sections.createButton(DashboardIcons.ICON_COMPARE,
            "Compare selected", "Put the selected batches side by side", null);

    private final BatchTableModel tableModel = new BatchTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection = Tables.section("Batches", table);

    public BatchListView(DashboardDao dao, DashboardService service, DashboardNavigator navigator) {
        super(new MigLayout("insets 12, fill, wrap 1", "[grow,fill]", "[][grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.navigator = navigator;

        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> updateCompareButton());

        add(buildFilterSection());
        add(tableSection, "grow, push, gaptop 6");

        applyRenderers();
        Tables.onRowActivated(table, row -> navigator.showBatchDetail(
                tableModel.rowAt(row).batch().batchId()));
        updateCompareButton();
    }

    /**
     * The date range and the comparison, in the section panel the options screens use.
     *
     * <p>The note is what stops the removed dropdowns reading as a loss: it says where the
     * filtering they did now lives.
     */
    private JPanel buildFilterSection() {
        JPanel section = Sections.createSection("Filters",
                "insets 8 12 8 12", "[]8[][]4[][]8[]2[]16[]push");

        section.add(new JLabel(DashboardIcons.ICON_CALENDAR));
        section.add(Sections.createFieldLabel("From"));
        section.add(fromField, DATE_WIDTH);
        section.add(Sections.createFieldLabel("To"));
        section.add(toField, DATE_WIDTH);

        section.add(Sections.createIconButton(DashboardIcons.ICON_SEARCH,
                "Apply this date range", event -> reload()));
        section.add(Sections.createIconButton(DashboardIcons.ICON_FILTER_CLEAR,
                "Clear the date range", event -> {
                    fromField.setText("");
                    toField.setText("");
                    reload();
                }));

        compareButton.addActionListener(event -> compareSelection());
        section.add(compareButton);

        section.add(Sections.createHint("Every other column filters from its own header:"
                + " click the arrow beside User, Project, Source or Target to pick from the"
                + " values that column actually holds."), "gapleft 16");
        return section;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Batch", Renderers.identifier());
        Tables.renderer(table, "Status", Renderers.status());
        Tables.renderer(table, "Match Rate", Renderers.matchRate());
        Tables.renderer(table, "Passed", Renderers.numeric(0, Palette.success()));
        Tables.renderer(table, "Failed", Renderers.numeric(0, Palette.error()));
        Tables.renderer(table, "Description", Renderers.foldedText());
        for (String column : DashboardService.RUN_COLUMNS) {
            Tables.renderer(table, column, Renderers.foldedText());
        }
    }

    public void reload() {
        LocalDate from = parse(fromField.getText());
        LocalDate to = parse(toField.getText());

        DashboardTask.run(this, "The batch list", () -> {
            List<BatchRow> batches = dao.findAllBatches();
            if (from != null || to != null) {
                batches = service.findBatchesInRange(batches, from, to);
            }
            return service.summarise(batches);
        }, this::apply);
    }

    private void apply(List<DashboardService.BatchSummary> summaries) {
        tableModel.setRows(summaries);
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, summaries.size() + " batches"
                + "  -  select up to " + CompareService.MAX_COMPARE_BATCHES
                + " of them to compare");
        updateCompareButton();
    }

    private void updateCompareButton() {
        int selected = table.getSelectedRowCount();
        compareButton.setEnabled(selected >= 2);
        compareButton.setText(selected >= 2
                ? "Compare " + Math.min(selected, CompareService.MAX_COMPARE_BATCHES) + " batches"
                : "Compare selected");
    }

    private void compareSelection() {
        List<Integer> chosen = new ArrayList<>();
        for (int viewRow : table.getSelectedRows()) {
            chosen.add(tableModel.rowAt(table.convertRowIndexToModel(viewRow)).batch().batchId());
        }
        if (chosen.size() < 2) {
            return;
        }
        if (chosen.size() > CompareService.MAX_COMPARE_BATCHES) {
            // Silently truncating would misreport what is on screen, so it is said out loud.
            JOptionPane.showMessageDialog(this,
                    "Comparing the first " + CompareService.MAX_COMPARE_BATCHES
                            + " of " + chosen.size() + " selected batches.",
                    "EasyRec Dashboard", JOptionPane.INFORMATION_MESSAGE);
        }
        navigator.showCompare(chosen);
    }

    private static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
