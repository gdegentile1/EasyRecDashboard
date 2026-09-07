package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.Rates;
import com.finboxsolutions.common.gui.utils.JSearchTextField;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.table.ReconciliationTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One batch: its headline figures and the reconciliations it ran.
 *
 * <p>A batch holds a single run, so listing runs would say nothing the batch does not
 * already say. The table lists the run's context rows instead, one reconciliation per
 * template, which is what there is to drill into.
 */
public class BatchDetailView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DashboardDao dao;
    private final DashboardService service;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_BATCH);
    private final JLabel subtitle = new JLabel();
    private final JSearchTextField templateFilter = new JSearchTextField(24);

    /**
     * The batch's description, read-only until the pencil is pressed.
     *
     * <p>The dashboard is a reading screen and this is the one field on it that writes, so
     * it does not sit there editable waiting to catch a stray keystroke: the pencil arms it,
     * the tick commits, the cross puts back what was there. That is how EasyRec's own option
     * panels edit a single field - see {@code DefaultOptionPanel.createEditButton}.
     */
    private final JTextField descriptionField = new JTextField(36);
    private final JButton editButton = Sections.createIconButton(DashboardIcons.ICON_EDIT,
            "Edit the description", event -> beginEditing());
    private final JButton saveButton = Sections.createIconButton(DashboardIcons.ICON_SAVE,
            "Save the description", event -> commitEditing());
    private final JButton cancelButton = Sections.createIconButton(DashboardIcons.ICON_CANCEL,
            "Discard the change", event -> cancelEditing());

    /** What the description was when the field was last loaded or saved. */
    private String storedDescription = "";

    private final KpiCard rateCard = new KpiCard("Match rate", DashboardIcons.ICON_RATE);
    private final KpiCard matchedCard = new KpiCard("Matched rows", DashboardIcons.ICON_ROWS);
    private final KpiCard breaksCard = new KpiCard("Breaks", DashboardIcons.ICON_BREAKS);
    private final KpiCard reconciliationsCard =
            new KpiCard("Reconciliations", DashboardIcons.ICON_RECONCILIATION);

    private final ReconciliationTableModel tableModel = new ReconciliationTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection = Tables.section("Reconciliations", table);

    private int batchId;

    public BatchDetailView(DashboardDao dao, DashboardService service, DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]12[grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildKpiBar(), "gapx 12 12, gaptop 10");
        add(tableSection, "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
        Tables.onRowActivated(table, row -> {
            DashboardService.Reconciliation rec = tableModel.rowAt(row);
            if (rec.templateId() != null) {
                navigator.showReconciliation(rec.runId(), rec.templateId());
            }
        });
    }

    /**
     * The action bar: what this screen is on the left, what it can do on the right.
     *
     * <p>The batch's own identity sits in the bar rather than in a heading below it, so the
     * four KPI tiles start at the top of the content and the whole screen is one row
     * shorter - which on a laptop is a row of the table.
     */
    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[grow,fill]12[][]4[]", "[]"));

        // The growing column is the labels', so the description can use the width the header
        // has going spare rather than being sized to a column count and leaving it empty.
        JPanel labels = new JPanel(
                new MigLayout("insets 0, wrap 1, gapy 0, fillx", "[grow,fill]"));
        labels.setOpaque(false);
        subtitle.setForeground(Palette.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        labels.add(title);
        labels.add(subtitle);
        labels.add(buildDescriptionRow(), "gaptop 4");

        bar.add(labels, "growx");

        templateFilter.setToolTipText("Show only the reconciliations whose template path"
                + " contains this text");
        templateFilter.addActionListener(event -> load(batchId));
        bar.add(Sections.createFieldLabel("Template path"));
        bar.add(templateFilter);
        bar.add(Sections.createIconButton(DashboardIcons.ICON_HISTORY,
                "History of this suite", event -> navigator.showBatchHistory(batchId)));
        return bar;
    }

    /**
     * The description, with the three buttons that edit it.
     *
     * <p>In the header rather than folded into the subtitle line beside the date and the
     * user: it is the one thing on this screen an operator writes, and a field they can type
     * into has to look like a field, not like the tail of a sentence.
     */
    private JPanel buildDescriptionRow() {
        // The cap is on the column, not the field. Capping the component leaves the cell
        // growing past it, and the buttons follow the cell - stranding them a hand's width
        // from the field they act on.
        JPanel row = new JPanel(new MigLayout("insets 0, fillx",
                "[]6[::760,grow,fill]2[]0[]0[]push", "[]"));
        row.setOpaque(false);

        descriptionField.setToolTipText("The batch's description, as stored on"
                + " ER_DASHBOARD_BATCH");
        // A placeholder rather than a HintTextField: that class reports an empty string from
        // getText whenever the text happens to equal its hint, so a batch actually described
        // as "No description" would be saved back as nothing. FlatLaf honours this property
        // and the other look and feels ignore it, which costs a prompt, not a description.
        descriptionField.putClientProperty("JTextField.placeholderText", "No description");
        descriptionField.addActionListener(event -> commitEditing());
        descriptionField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancelEditing();
                }
            }
        });

        row.add(Sections.createFieldLabel("Description"));
        // Wide enough to hold a sentence; the column caps it short of a monitor's width.
        row.add(descriptionField, "growx");
        row.add(editButton);
        row.add(saveButton);
        row.add(cancelButton);

        setEditing(false);
        return row;
    }

    /** Which of the three buttons are on show, and whether the field takes typing. */
    private void setEditing(boolean editing) {
        descriptionField.setEditable(editing);
        descriptionField.setFocusable(editing);
        editButton.setVisible(!editing);
        saveButton.setVisible(editing);
        cancelButton.setVisible(editing);
    }

    private void beginEditing() {
        setEditing(true);
        descriptionField.requestFocusInWindow();
        descriptionField.selectAll();
    }

    private void cancelEditing() {
        descriptionField.setText(storedDescription);
        setEditing(false);
    }

    /**
     * Writes the description back, off the EDT.
     *
     * <p>The field is put back to read-only only once the write has returned, so a save that
     * fails leaves the operator looking at what they typed rather than at the old value with
     * their edit silently gone.
     */
    private void commitEditing() {
        if (!descriptionField.isEditable()) {
            return;
        }
        String edited = descriptionField.getText().trim();
        if (edited.equals(storedDescription)) {
            setEditing(false);
            return;
        }
        int target = batchId;
        DashboardTask.run(this, "The description of batch " + target,
                () -> dao.updateBatchDescription(target, edited),
                updated -> {
                    if (updated > 0) {
                        storedDescription = edited;
                        setEditing(false);
                    } else {
                        // No row matched: the batch is gone, or was never there. Saying so is
                        // better than a silent no-op that looks like a save.
                        JOptionPane.showMessageDialog(this,
                                "Batch " + target + " was not found, so nothing was saved.",
                                "EasyRec Dashboard", JOptionPane.WARNING_MESSAGE);
                    }
                });
    }

    private JPanel buildKpiBar() {
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi]"));
        bar.add(rateCard);
        bar.add(matchedCard);
        bar.add(breaksCard);
        bar.add(reconciliationsCard);
        return bar;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Template ID", Renderers.identifier());
        Tables.renderer(table, "Status", Renderers.status());
        Tables.renderer(table, "Match Rate", Renderers.matchRate());
        for (String column : DashboardService.PROJECT_COLUMNS) {
            Tables.renderer(table, column, Renderers.foldedText());
        }
        Tables.renderer(table, "Template Path", Renderers.foldedText());
    }

    public void load(int requestedBatchId) {
        this.batchId = requestedBatchId;
        String fragment = templateFilter.getText();

        DashboardTask.run(this, "Batch " + requestedBatchId, () -> {
            Map<String, Object> loaded = new LinkedHashMap<>();
            BatchRow batch = dao.findBatch(requestedBatchId);
            List<RunRow> runs = dao.findRunsByBatch(List.of(requestedBatchId))
                    .getOrDefault(requestedBatchId, List.of());
            List<Integer> runIds = new ArrayList<>(runs.size());
            for (RunRow run : runs) {
                runIds.add(run.runId());
            }
            loaded.put("batch", batch);
            loaded.put("runs", runs);
            loaded.put("reconciliations", service.findReconciliations(runIds, fragment));
            // The unfiltered count is what tells the viewer the filter is hiding something.
            loaded.put("total", service.findReconciliations(runIds, null).size());
            loaded.put("stats", dao.findRowStats(runIds));
            return loaded;
        }, this::apply);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> loaded) {
        BatchRow batch = (BatchRow) loaded.get("batch");
        List<RunRow> runs = (List<RunRow>) loaded.get("runs");
        List<DashboardService.Reconciliation> reconciliations =
                (List<DashboardService.Reconciliation>) loaded.get("reconciliations");
        List<RowStats> stats = (List<RowStats>) loaded.get("stats");
        int total = (Integer) loaded.get("total");

        title.setText(batch == null ? "Batch " + batchId
                : "Batch " + batch.batchId() + "  \u2022  " + batch.status());
        subtitle.setText(batch == null ? " " : describe(batch, runs));

        storedDescription = batch == null || batch.description() == null
                ? "" : batch.description().trim();
        descriptionField.setText(storedDescription);
        setEditing(false);
        editButton.setEnabled(batch != null);

        long matched = 0;
        long breaks = 0;
        long rowsSource = 0;
        long rowsTarget = 0;
        for (RowStats row : stats) {
            matched += row.matched();
            breaks += row.breakCount();
            rowsSource += row.rowsSource();
            rowsTarget += row.rowsTarget();
        }
        Double rate = Rates.matchRate(matched, rowsSource, rowsTarget);

        rateCard.setValue(rate == null ? "-" : String.format(Locale.ROOT, "%.2f%%", rate),
                Palette.forRate(rate));
        rateCard.setDetail(String.format(Locale.ROOT, "over %,d rows",
                Math.max(rowsSource, rowsTarget)));
        matchedCard.setValue(String.format(Locale.ROOT, "%,d", matched), Palette.success());
        matchedCard.setDetail(" ");
        breaksCard.setValue(String.format(Locale.ROOT, "%,d", breaks),
                breaks == 0L ? Palette.success() : Palette.error());
        breaksCard.setDetail("missing source, missing target and unmatched");
        reconciliationsCard.setValue(String.valueOf(total), Palette.text());
        reconciliationsCard.setDetail(runs.size() + (runs.size() == 1 ? " run" : " runs"));

        tableModel.setRows(reconciliations);
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, reconciliations.size() == total
                ? total + " reconciliations  -  double-click one to open it"
                : reconciliations.size() + " of " + total + " reconciliations shown");
    }

    private String describe(BatchRow batch, List<RunRow> runs) {
        StringBuilder text = new StringBuilder();
        if (batch.when() != null) {
            text.append(batch.when());
        }
        if (batch.userName() != null && !batch.userName().isBlank()) {
            text.append(text.length() == 0 ? "" : "  \u2022  ").append(batch.userName());
        }
        if (batch.runMode() != null && !batch.runMode().isBlank()) {
            text.append(text.length() == 0 ? "" : "  \u2022  ").append(batch.runMode());
        }
        if (!runs.isEmpty()) {
            text.append(text.length() == 0 ? "" : "  \u2022  ").append("run ").append(runs.get(0).runId());
        }
        // The description is not appended here any more: it has a field of its own below.
        return text.length() == 0 ? " " : text.toString();
    }
}
