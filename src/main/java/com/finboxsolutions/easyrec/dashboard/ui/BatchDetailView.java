package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.Rates;
import com.finboxsolutions.common.gui.utils.JSearchTextField;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.EditableField;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.StatusBadge;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.table.DashboardTableModel;
import com.finboxsolutions.easyrec.dashboard.ui.table.ReconciliationTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Font;
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

    /** The batch's outcome, as the same pill the Status column draws. */
    private final StatusBadge statusBadge = new StatusBadge(null);
    private final JSearchTextField templateFilter = new JSearchTextField(24);

    /** The project the batch was run from - ER_DASHBOARD_RUN.PROJECT_PATH. */
    private final EditableField projectField =
            new EditableField("Project", "No project", this::saveProject);

    /** The batch's description - ER_DASHBOARD_BATCH.DESCRIPTION. */
    private final EditableField descriptionField =
            new EditableField("Description", "No description", this::saveDescription);

    /** The runs of the batch on show, which is where a project path is written. */
    private final transient List<Integer> runIds = new ArrayList<>();

    private final KpiCard rateCard = new KpiCard("Match rate", DashboardIcons.ICON_RATE);

    /**
     * How much data the batch reconciled, on each side.
     *
     * <p>The match rate above them is a ratio and a ratio hides its own scale: 99% of twelve
     * rows and 99% of a quarter of a million are the same figure and not the same result.
     * These say which one is on screen.
     *
     * <p>They share an icon on purpose - they are one measure read twice, and the pairing is
     * the point. The gap between them is reported under the target, because two sides that
     * disagree on how many rows they even hold is usually a feed problem rather than a
     * reconciliation one, and it is invisible if the reader has to subtract.
     */
    private final KpiCard sourceRowsCard = new KpiCard("Source rows", DashboardIcons.ICON_ROWS);
    private final KpiCard targetRowsCard = new KpiCard("Target rows", DashboardIcons.ICON_ROWS);

    private final KpiCard matchedCard = new KpiCard("Matched rows", DashboardIcons.ICON_ROWS);
    private final KpiCard breaksCard = new KpiCard("Breaks", DashboardIcons.ICON_BREAKS);
    private final KpiCard reconciliationsCard =
            new KpiCard("Reconciliations", DashboardIcons.ICON_RECONCILIATION);

    private final ReconciliationTableModel tableModel = new ReconciliationTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection =
            Tables.section("Reconciliations", table, "views/dashboard_batch.xml");

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
                navigator.showReconciliation(rec.runId(), rec.templateId(),
                        rec.templatePath());
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
        labels.add(buildTitleRow());
        labels.add(subtitle);
        labels.add(projectField, "gaptop 4, growx");
        labels.add(descriptionField, "growx");

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
     * What the batch is: its reference and its outcome.
     *
     * <p>The outcome was a word appended to the reference - "Batch 12 - PASSED" - which reads
     * as part of the title rather than as a verdict on it. The same pill the Status column
     * draws says it at a glance, and puts the header and the table in one language.
     *
     * <p>The project moved out of this row and into a field of its own below, once it became
     * something an operator writes rather than only reads. A value that can be typed into has
     * to look like a field, and it reads better stacked with the description than crammed
     * beside the reference.
     */
    private JPanel buildTitleRow() {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 0, fillx", "[]10[]push", "[]"));
        row.setOpaque(false);
        row.add(title);
        row.add(statusBadge);
        return row;
    }

    /**
     * Writes a project path back, off the EDT, onto every run of the batch.
     *
     * <p>A batch holds a single run in practice, but the header shows one project for the
     * batch, so a write from here has to reach whatever the header was summarising or the
     * two would disagree the moment there were two runs.
     */
    private void saveProject(String edited) {
        if (runIds.isEmpty()) {
            return;
        }
        List<Integer> targets = List.copyOf(runIds);
        DashboardTask.run(this, "The project of batch " + batchId,
                () -> dao.updateRunProjectPath(targets, edited),
                updated -> {
                    if (updated > 0) {
                        projectField.saved(edited);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "The runs of batch " + batchId + " were not found,"
                                        + " so nothing was saved.",
                                "EasyRec Dashboard", JOptionPane.WARNING_MESSAGE);
                    }
                });
    }

    /**
     * Writes the description back, off the EDT.
     *
     * <p>The field returns to read-only only once the write has returned, so a save that
     * fails leaves the operator looking at what they typed rather than at the old value with
     * their edit silently gone.
     */
    private void saveDescription(String edited) {
        int target = batchId;
        DashboardTask.run(this, "The description of batch " + target,
                () -> dao.updateBatchDescription(target, edited),
                updated -> {
                    if (updated > 0) {
                        descriptionField.saved(edited);
                    } else {
                        // No row matched: the batch is gone, or was never there. Saying so is
                        // better than a silent no-op that looks like a save.
                        JOptionPane.showMessageDialog(this,
                                "Batch " + target + " was not found, so nothing was saved.",
                                "EasyRec Dashboard", JOptionPane.WARNING_MESSAGE);
                    }
                });
    }

    /**
     * How the target side differs from the source, in words rather than a signed number.
     *
     * <p>Neither side is the right one, so this is stated rather than judged: no green for
     * more and no red for fewer. It says nothing at all when they agree, which is the case
     * that needs no comment.
     */
    private static String rowGap(long rowsSource, long rowsTarget) {
        long difference = rowsTarget - rowsSource;
        if (difference == 0L) {
            return rowsSource == 0L ? " " : "same as source";
        }
        return String.format(Locale.ROOT, "%,d %s than source",
                Math.abs(difference), difference > 0L ? "more" : "fewer");
    }

    /**
     * The headline figures, ordered as the batch is read: how well it matched, how much came
     * in on each side, how much of it matched, how much broke, and how many reconciliations
     * it took. The size group keeps all six the same width however long the numbers get.
     *
     * <p>The explicit 100px minimum is what stops a caption from resizing the application.
     * Left to themselves the columns take their minimum from the widest card, the size group
     * applies that to all six, and a bar six times the widest card is wider than the window -
     * which does not clip the bar, it widens the whole screen behind it, and the first
     * casualties are the header field and the table's last column. Stating the minimum means
     * a caption too long for its card is clipped inside that card, where the damage belongs.
     */
    private JPanel buildKpiBar() {
        String column = "[100:100,grow,fill,sg kpi]";
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx", column.repeat(6)));
        bar.add(rateCard);
        bar.add(sourceRowsCard);
        bar.add(targetRowsCard);
        bar.add(matchedCard);
        bar.add(breaksCard);
        bar.add(reconciliationsCard);
        return bar;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Template ID", Renderers.identifier());
        Tables.renderer(table, "Status", Renderers.status());
        Tables.renderer(table, "Match Rate", Renderers.matchRate());
        Tables.renderer(table, DashboardTableModel.SOURCE_ROWS, Renderers.count());
        Tables.renderer(table, DashboardTableModel.TARGET_ROWS, Renderers.count());
        for (String column : DashboardService.CONTEXT_COLUMNS) {
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

        title.setText("Batch " + (batch == null ? batchId : batch.batchId()));
        statusBadge.setStatus(batch == null ? null : StatusBadge.of(batch.status()));
        statusBadge.setVisible(batch != null);

        subtitle.setText(batch == null ? " " : describe(batch, runs));

        descriptionField.show(batch == null ? null : batch.description());
        descriptionField.setEditingAllowed(batch != null);

        runIds.clear();
        for (RunRow run : runs) {
            runIds.add(run.runId());
        }
        projectField.show(projectPathOf(runs));
        projectField.setEditingAllowed(!runIds.isEmpty());

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
        sourceRowsCard.setValue(String.format(Locale.ROOT, "%,d", rowsSource), Palette.text());
        sourceRowsCard.setDetail(" ");
        targetRowsCard.setValue(String.format(Locale.ROOT, "%,d", rowsTarget), Palette.text());
        targetRowsCard.setDetail(rowGap(rowsSource, rowsTarget));
        matchedCard.setValue(String.format(Locale.ROOT, "%,d", matched), Palette.success());
        matchedCard.setDetail(" ");
        breaksCard.setValue(String.format(Locale.ROOT, "%,d", breaks),
                breaks == 0L ? Palette.success() : Palette.error());
        breaksCard.setDetail("missing on either side, and unmatched");
        reconciliationsCard.setValue(String.valueOf(total), Palette.text());
        reconciliationsCard.setDetail(runs.size() + (runs.size() == 1 ? " run" : " runs"));

        tableModel.setRows(reconciliations);
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, reconciliations.size() == total
                ? total + " reconciliations  -  double-click one to open it"
                : reconciliations.size() + " of " + total + " reconciliations shown");
    }

    /**
     * The project path of the batch's run.
     *
     * <p>A batch holds a single run, so there is normally one. Where a deployment writes
     * several, the distinct paths are joined rather than the first one being shown as though
     * it were the whole story.
     */
    private static String projectPathOf(List<RunRow> runs) {
        List<String> paths = new ArrayList<>();
        for (RunRow run : runs) {
            String path = run.projectPath();
            if (path != null && !path.isBlank() && !paths.contains(path.trim())) {
                paths.add(path.trim());
            }
        }
        return paths.isEmpty() ? null : String.join("  |  ", paths);
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
