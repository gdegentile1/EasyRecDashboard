package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.EditableField;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.component.TrendChart;
import com.finboxsolutions.easyrec.dashboard.ui.table.PatternHistoryTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One pattern over time: its count on every run that looked for it, and what an operator
 * has recorded about it.
 *
 * <p>The qualification - root cause, owner, ticket - sits at the top because it is the one
 * thing on this screen a person adds. EasyRec detects the pattern; nothing in it can say why
 * the pattern exists or who is fixing it, and ER_DASHBOARD_PATTERN keeps those columns for
 * exactly that.
 */
public class PatternView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final PatternService patterns;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_PATTERN);
    private final JLabel subtitle = new JLabel();

    private final EditableField rootCauseField =
            new EditableField("Root cause", "Not qualified yet", edited -> saveQualification(edited, null, null));
    private final EditableField ownerField =
            new EditableField("Owner", "No owner", edited -> saveQualification(null, edited, null));
    private final EditableField ticketField =
            new EditableField("Ticket", "No ticket", edited -> saveQualification(null, null, edited));
    private final JLabel linkLabel = Sections.createHint("");

    private final KpiCard latestCard = new KpiCard("Latest occurrences", DashboardIcons.ICON_BREAKS);
    private final KpiCard peakCard = new KpiCard("Peak", DashboardIcons.ICON_CHART);
    private final KpiCard detectedCard = new KpiCard("Detected on", DashboardIcons.ICON_CLOCK);
    private final KpiCard firstSeenCard = new KpiCard("First seen", DashboardIcons.ICON_NEW);

    private final TrendChart occurrencesChart = new TrendChart();

    private final PatternHistoryTableModel tableModel = new PatternHistoryTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection = Tables.section("Runs", table);

    private int patternId;
    private transient PatternService.PatternHistory shown;

    public PatternView(PatternService patterns, DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]6[]6[]6[grow,fill]"));
        this.patterns = patterns;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildQualification(), "gapx 12 12, gaptop 10");
        add(buildKpiBar(), "gapx 12 12");
        add(Sections.createFilledSection("Occurrences over time", occurrencesChart), "gapx 12 12");
        add(tableSection, "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
        Tables.onRowActivated(table, row -> {
            PatternService.PatternPoint point = tableModel.rowAt(row);
            if (point.contextTemplateId() != null) {
                navigator.showReconciliation(point.runId(), point.contextTemplateId(),
                        shown == null || shown.template() == null ? null : shown.template().fullPath());
            } else if (point.batch() != null) {
                navigator.showBatchDetail(point.batch().batchId());
            }
        });
    }

    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[]12[grow,fill]", "[]"));

        JPanel labels = new JPanel(new MigLayout("insets 0, wrap 1, gapy 0", "[]"));
        labels.setOpaque(false);
        subtitle.setForeground(Palette.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        labels.add(title);
        labels.add(subtitle);

        bar.add(labels);
        bar.add(Sections.spacer(), "growx");
        return bar;
    }

    private JPanel buildQualification() {
        JPanel section = Sections.createSection("Qualification", "insets 8 12 8 12, wrap 1, gapy 2",
                "[grow,fill]");
        rootCauseField.setToolTipText(null, "Why this pattern exists - never inferred by EasyRec");
        ownerField.setToolTipText(null, "Who is following the pattern up");
        ticketField.setToolTipText(null, "The ticket tracking the fix");
        section.add(rootCauseField);
        section.add(ownerField);
        section.add(ticketField);
        section.add(linkLabel);
        return section;
    }

    private JPanel buildKpiBar() {
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi]"));
        bar.add(latestCard);
        bar.add(peakCard);
        bar.add(detectedCard);
        bar.add(firstSeenCard);
        return bar;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Batch", Renderers.identifier());
        Tables.renderer(table, "Run", Renderers.identifier());
        Tables.renderer(table, "Trend", Renderers.trend());
        Tables.renderer(table, "Occurrences", Renderers.count());
        // Fewer occurrences is the improvement, and a count of breaks is a whole number.
        Tables.renderer(table, "Change", Renderers.delta("", false, 0));
        Tables.renderer(table, "% of Rows", Renderers.numeric(2, null));
    }

    public void load(int requestedPatternId) {
        this.patternId = requestedPatternId;
        title.setText("Pattern " + requestedPatternId);
        DashboardTask.run(this, "Pattern " + requestedPatternId,
                () -> patterns.patternHistory(requestedPatternId), this::apply);
    }

    private void apply(PatternService.PatternHistory history) {
        shown = history;
        if (history == null) {
            title.setText("Pattern " + patternId);
            subtitle.setText("This pattern is no longer in the dashboard database.");
            for (EditableField field : List.of(rootCauseField, ownerField, ticketField)) {
                field.show(null);
                field.setEditingAllowed(false);
            }
            linkLabel.setText(" ");
            occurrencesChart.setPoints(List.of(), "", null);
            tableModel.setRows(List.of());
            Tables.refresh(table);
            return;
        }

        PatternRow pattern = history.pattern();
        title.setText(pattern.label());
        title.setToolTipText("Signature " + pattern.signature());
        subtitle.setText((history.template() == null || history.template().fullPath() == null
                ? "Template " + pattern.templateId() : history.template().fullPath())
                + "  \u2022  pattern " + pattern.patternId());

        showQualification(pattern);
        PatternRow linked = history.linkedPattern();
        linkLabel.setText(linked == null ? " "
                : "Continues pattern " + linked.patternId() + ": " + linked.label());

        applyKpis(history);

        List<TrendChart.Point> points = new ArrayList<>();
        for (PatternService.PatternPoint point : history.points()) {
            PatternService.PatternLine line = point.line();
            String tooltip = point.longLabel() + " \u2022 run " + point.runId()
                    + String.format(Locale.ROOT, " \u2022 %,d occurrences", line.occurrences())
                    + " \u2022 " + line.trend().label()
                    + (line.configChanged() ? " \u2022 configuration changed" : "");
            points.add(new TrendChart.Point(point.shortLabel(), (double) line.occurrences(),
                    tooltip, line.trend().needsAttention()));
        }
        occurrencesChart.setPoints(points, "", null);

        tableModel.setRows(history.newestFirst());
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, history.points().size()
                + " runs, newest first  -  double-click a row to open that reconciliation");
    }

    private void showQualification(PatternRow pattern) {
        rootCauseField.show(pattern.rootCause());
        ownerField.show(pattern.ownerName());
        ticketField.show(pattern.ticketRef());
        for (EditableField field : List.of(rootCauseField, ownerField, ticketField)) {
            field.setEditingAllowed(true);
        }
    }

    private void applyKpis(PatternService.PatternHistory history) {
        PatternService.PatternPoint latest = history.latest();
        if (latest == null) {
            latestCard.setValue("-", Palette.muted());
            latestCard.setDetail("never counted");
        } else {
            long occurrences = latest.line().occurrences();
            latestCard.setValue(String.format(Locale.ROOT, "%,d", occurrences),
                    Palette.forTrend(latest.line().trend()));
            // Not setDelta: it prints two decimals, and a count of breaks has none. The colour
            // of the figure above already carries the direction.
            Long change = latest.line().change();
            latestCard.setDetail(latest.line().trend().label()
                    + (change == null ? "" : String.format(Locale.ROOT, " (%+,d)", change))
                    + " \u2022 " + latest.longLabel());
        }

        PatternService.PatternPoint peak = history.peak();
        peakCard.setValue(peak == null ? "-" : String.format(Locale.ROOT, "%,d",
                peak.line().occurrences()), Palette.text());
        peakCard.setDetail(peak == null ? " " : peak.longLabel());

        detectedCard.setValue(history.detectedRuns() + " of " + history.points().size(),
                Palette.text());
        detectedCard.setDetail("runs that looked for it");

        Integer firstRun = history.pattern().firstSeenRun();
        PatternService.PatternPoint first = null;
        for (PatternService.PatternPoint point : history.points()) {
            if (firstRun != null && point.runId() == firstRun) {
                first = point;
                break;
            }
        }
        firstSeenCard.setValue(first == null ? (firstRun == null ? "-" : "Run " + firstRun)
                : first.longLabel(), Palette.text());
        firstSeenCard.setDetail(firstRun == null ? " " : "run " + firstRun);
    }

    /**
     * Writes the qualification back, off the EDT.
     *
     * <p>The three columns are written together - that is the engine's own statement - so a
     * save from one field carries the stored values of the other two. Whichever argument is
     * non-null is the field being saved.
     */
    private void saveQualification(String rootCause, String owner, String ticket) {
        int target = patternId;
        String newRootCause = rootCause != null ? rootCause : rootCauseField.stored();
        String newOwner = owner != null ? owner : ownerField.stored();
        String newTicket = ticket != null ? ticket : ticketField.stored();
        DashboardTask.run(this, "The qualification of pattern " + target,
                () -> patterns.qualify(target, newRootCause, newOwner, newTicket),
                updated -> {
                    if (updated > 0) {
                        // Only the field that was saved: another one may be armed with an
                        // edit of its own, which saved() would throw away.
                        if (rootCause != null) {
                            rootCauseField.saved(newRootCause);
                        } else if (owner != null) {
                            ownerField.saved(newOwner);
                        } else {
                            ticketField.saved(newTicket);
                        }
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Pattern " + target + " was not found, so nothing was saved.",
                                "EasyRec Dashboard", JOptionPane.WARNING_MESSAGE);
                    }
                });
    }
}
