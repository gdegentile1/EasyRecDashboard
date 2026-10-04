package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;
import com.finboxsolutions.easyrec.dashboard.service.ContextValues;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.StatusBadge;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.pivot.DashboardPivotPanel;
import com.finboxsolutions.easyrec.dashboard.ui.table.ColumnStatsTableModel;
import com.finboxsolutions.easyrec.dashboard.ui.table.PatternTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One reconciliation: its metadata, its row-level KPIs, its column statistics, the break
 * patterns the engine found in it and its pivot breakdown.
 *
 * <p>Always scoped to a single template. A run covers many templates at once, so an
 * unscoped run screen would pile every column statistic of every template onto one view.
 */
public class ReconciliationView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DashboardDao dao;
    private final DashboardService service;
    private final PatternService patterns;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_RECONCILIATION);
    private final JLabel subtitle = new JLabel();

    /**
     * How the run finished, from ER_DASHBOARD_RUN.STATUS.
     *
     * <p>EasyRec's own {@code KpiStatus} is the contract for that column: 1 SUCCESS, 0
     * FAILED, -1 for a run that never reached its end. It is the opposite of the code on the
     * context row below, which follows the per-template convention where 0 is a match - which
     * is why the two are read through {@code StatusScope} and never by bare number.
     */
    private final StatusBadge runBadge = new StatusBadge(null);

    /**
     * The reconciliation's metadata, in the section panel the options screens use.
     *
     * <p>Six pairs to a row: a name and its value read as one unit, and three units across
     * fit the twelve fields this table can carry into two rows rather than six.
     */
    private final JPanel contextCard = Sections.createSection("Context",
            "insets 8 12 8 12, wrap 6", "[]8[]28[]8[]28[]8[]push");

    private final KpiCard rateCard = new KpiCard("Match rate", DashboardIcons.ICON_RATE);
    private final KpiCard unmatchedCard = new KpiCard("Unmatched", DashboardIcons.ICON_FAILED);
    private final KpiCard missingSourceCard = new KpiCard("Missing source", DashboardIcons.ICON_BREAKS);
    private final KpiCard missingTargetCard = new KpiCard("Missing target", DashboardIcons.ICON_BREAKS);
    private final KpiCard forcedCard = new KpiCard("Force matched", DashboardIcons.ICON_FORCED);

    private final ColumnStatsTableModel columnModel = new ColumnStatsTableModel();
    private final DashboardTable columnTable = Tables.create(columnModel);
    private final JPanel columnSection =
            Tables.section("Column statistics", columnTable, "views/dashboard_template.xml");

    // The pivot is EasyRec's own component, action bar included, so it carries its own
    // toolbar; the tab names it.
    private final DashboardPivotPanel pivotPanel = new DashboardPivotPanel();

    /** The patterns the engine exported for this run, under a line summing up what moved. */
    private final PatternTableModel patternModel = new PatternTableModel();
    private final DashboardTable patternTable = Tables.create(patternModel);
    private final JLabel patternSummary = new JLabel();
    private final JPanel patternPanel = new JPanel(
            new MigLayout("insets 6 4 0 4, fill, wrap 1", "[grow,fill]", "[]4[grow,fill]"));

    /**
     * The pivot breakdown and the patterns, one tab each, under the column statistics.
     *
     * <p>Below rather than beside: the column statistics carry sixteen columns and need the
     * full width, and so does a pattern description. Tabs rather than a third pane, because
     * a screen split three ways leaves none of the tables enough rows to read.
     */
    private final JTabbedPane lowerTabs = new JTabbedPane();

    private final JSplitPane split =
            new JSplitPane(JSplitPane.VERTICAL_SPLIT, columnSection, lowerTabs);

    /** Holds either the split or the column statistics alone - see {@link #showBody}. */
    private final JPanel body = new JPanel(new MigLayout("insets 0, fill", "[grow,fill]",
            "[grow,fill]"));

    private static final String PIVOT_TAB = "Pivot breakdown";
    private static final String PATTERN_TAB = "Patterns";

    /** The tab the reader last chose, kept across reconciliations while it exists. */
    private String preferredTab = PIVOT_TAB;

    private int runId;
    private int templateId;

    public ReconciliationView(DashboardDao dao, DashboardService service, PatternService patterns,
                              DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]6[]6[grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.patterns = patterns;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildKpiBar(), "gapx 12 12, gaptop 10");
        add(contextCard, "gapx 12 12");
        add(buildBody(), "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
        Tables.onRowActivated(patternTable, row -> {
            PatternService.PatternLine line = patternModel.rowAt(row);
            navigator.showPatternHistory(line.pattern().patternId(), line.pattern().label());
        });
    }

    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[]12[grow,fill][]", "[]"));

        JPanel labels = new JPanel(new MigLayout("insets 0, wrap 1, gapy 0", "[]"));
        labels.setOpaque(false);
        subtitle.setForeground(Palette.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));

        JPanel titleRow = new JPanel(new MigLayout("insets 0, gap 0, fillx", "[]10[]push", "[]"));
        titleRow.setOpaque(false);
        titleRow.add(title);
        titleRow.add(runBadge);

        labels.add(titleRow);
        labels.add(subtitle);

        bar.add(labels);
        bar.add(Sections.spacer(), "growx");
        bar.add(Sections.createIconButton(DashboardIcons.ICON_HISTORY,
                "History of this template", event -> navigator.showTemplateHistory(templateId)));
        return bar;
    }

    private JPanel buildKpiBar() {
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi]"));
        bar.add(rateCard);
        bar.add(unmatchedCard);
        bar.add(missingSourceCard);
        bar.add(missingTargetCard);
        bar.add(forcedCard);
        return bar;
    }

    /**
     * Column statistics above, the pivot breakdown and the patterns below.
     *
     * <p>A split between the two halves: they answer different parts of the same question -
     * which field disagrees, and where or how - and comparing them is the usual reason to be
     * here.
     */
    private JPanel buildBody() {
        split.setResizeWeight(0.5d);
        split.setBorder(null);

        patternSummary.setForeground(Palette.muted());
        patternPanel.add(patternSummary);
        patternPanel.add(Tables.viewer(patternTable), "grow, push");

        lowerTabs.addChangeListener(event -> {
            int selected = lowerTabs.getSelectedIndex();
            // Only a choice the reader made: the tabs also change while showBody rebuilds them.
            if (selected >= 0 && !rebuildingTabs) {
                preferredTab = (String) ((javax.swing.JComponent) lowerTabs.getComponentAt(selected))
                        .getClientProperty(TAB_KEY);
            }
        });
        body.add(columnSection, "grow, push");
        return body;
    }

    private static final String TAB_KEY = "dashboard.tab";

    /** True while showBody rebuilds the tabs, whose selection events are not the reader's. */
    private boolean rebuildingTabs;

    /**
     * Which tabs are offered under the column statistics: the pivot breakdown when the
     * reconciliation pivoted, the patterns when the template is tracked.
     *
     * <p>A template reconciled without a pivot breakdown - most of them - has nothing to put
     * in that tab, and an empty panel reads as something that failed to load. So a tab with
     * nothing behind it is not offered, and with neither the lower half is removed and the
     * column statistics take the whole body.
     *
     * <p>The tabs are rebuilt on every load, which is cheap, but the split is only put back
     * when it was out: re-opening a reconciliation that has a lower half leaves the divider
     * where the reader last dragged it.
     */
    private void showBody(boolean pivoted, boolean withPatterns, String patternTabTitle) {
        rebuildingTabs = true;
        try {
            lowerTabs.removeAll();
            if (pivoted) {
                addTab(PIVOT_TAB, PIVOT_TAB, DashboardIcons.ICON_PIVOT, pivotPanel,
                        "The breaks, broken down by the template's pivot fields");
            }
            if (withPatterns) {
                addTab(PATTERN_TAB, patternTabTitle, DashboardIcons.ICON_PATTERN, patternPanel,
                        "The break patterns the engine found on this run");
            }
            for (int index = 0; index < lowerTabs.getTabCount(); index++) {
                if (preferredTab.equals(((javax.swing.JComponent) lowerTabs.getComponentAt(index))
                        .getClientProperty(TAB_KEY))) {
                    lowerTabs.setSelectedIndex(index);
                }
            }
        } finally {
            rebuildingTabs = false;
        }

        boolean split = lowerTabs.getTabCount() > 0;
        boolean splitShown = body.getComponentCount() == 1 && body.getComponent(0) == this.split;
        if (split == splitShown) {
            return;
        }
        body.removeAll();
        if (split) {
            this.split.setTopComponent(columnSection);
            this.split.setBottomComponent(lowerTabs);
            body.add(this.split, "grow, push");
            SwingUtilities.invokeLater(() -> this.split.setDividerLocation(0.5d));
        } else {
            body.add(columnSection, "grow, push");
        }
        body.revalidate();
        body.repaint();
    }

    private void addTab(String key, String title, javax.swing.Icon icon,
                        javax.swing.JComponent content, String tooltip) {
        content.putClientProperty(TAB_KEY, key);
        lowerTabs.addTab(title, icon, content, tooltip);
    }

    private void applyRenderers() {
        Tables.renderer(columnTable, "Match %", Renderers.matchRate());
        Tables.renderer(columnTable, "Unmatched", Renderers.numeric(0, Palette.error()));
        Tables.renderer(columnTable, "Exact", Renderers.numeric(0, Palette.success()));
        for (String column : List.of("In Tolerance", "Forced")) {
            Tables.renderer(columnTable, column, Renderers.count());
        }
        for (String column : List.of("Impact", "Impact (Abs)", "Average", "Std Dev",
                "Min Diff", "Max Diff", "Min Diff %", "Max Diff %")) {
            Tables.renderer(columnTable, column, Renderers.numeric(2, null));
        }
        // COL_IMPACT is a signed difference, so its sign is the meaningful part;
        // COL_IMPACT_ABS is a magnitude and is deliberately left uncoloured.
        Tables.renderer(columnTable, "Impact", Renderers.delta("", true));

        Tables.renderer(patternTable, "Trend", Renderers.trend());
        Tables.renderer(patternTable, "Occurrences", Renderers.count());
        Tables.renderer(patternTable, "Previous", Renderers.count());
        // Fewer occurrences is the improvement, and a count of breaks is a whole number.
        Tables.renderer(patternTable, "Change", Renderers.delta("", false, 0));
        Tables.renderer(patternTable, "% of Rows", Renderers.numeric(2, null));
        Tables.renderer(patternTable, "First Seen Run", Renderers.identifier());

    }

    public void load(int requestedRunId, int requestedTemplateId) {
        this.runId = requestedRunId;
        this.templateId = requestedTemplateId;

        DashboardTask.run(this, "Run " + requestedRunId + " template " + requestedTemplateId, () -> {
            Map<String, Object> loaded = new LinkedHashMap<>();
            List<DashboardService.Reconciliation> found =
                    service.findReconciliations(List.of(requestedRunId), null);
            DashboardService.Reconciliation target = null;
            for (DashboardService.Reconciliation rec : found) {
                if (rec.templateId() != null && rec.templateId() == requestedTemplateId) {
                    target = rec;
                    break;
                }
            }
            loaded.put("reconciliation", target);
            loaded.put("run", dao.findRun(requestedRunId));
            loaded.put("template", dao.findTemplate(requestedTemplateId));
            if (target != null && target.statsTemplateId() != null) {
                loaded.put("columns", dao.findColumnStats(requestedRunId, target.statsTemplateId()));
                loaded.put("pivots", dao.findPivots(requestedRunId, target.statsTemplateId()));
                loaded.put("patterns", patterns.patternsOf(requestedRunId,
                        target.statsTemplateId(), target.stats()));
            } else {
                loaded.put("columns", List.<ColumnStats>of());
                loaded.put("pivots", List.<PivotRow>of());
                loaded.put("patterns", PatternService.RunPatterns.NONE);
            }
            return loaded;
        }, this::apply);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> loaded) {
        DashboardService.Reconciliation rec =
                (DashboardService.Reconciliation) loaded.get("reconciliation");
        RunRow run = (RunRow) loaded.get("run");
        TemplateRow template = (TemplateRow) loaded.get("template");
        List<ColumnStats> columns = (List<ColumnStats>) loaded.get("columns");
        List<PivotRow> pivots = (List<PivotRow>) loaded.get("pivots");
        PatternService.RunPatterns found = (PatternService.RunPatterns) loaded.get("patterns");

        title.setText(template == null ? "Template " + templateId : template.shortName());
        runBadge.setStatus(run == null ? null : StatusBadge.of(run.status()));
        runBadge.setVisible(run != null);
        runBadge.setToolTipText(run == null ? null : "How run " + run.runId() + " finished");
        subtitle.setText(template == null || template.fullPath() == null
                ? "Run " + runId : template.fullPath());

        RowStats stats = rec == null ? null : rec.stats();
        applyKpis(stats);
        applyContext(rec == null ? null : rec.context(),
                rec == null ? StatusLabel.UNKNOWN : rec.status());

        columnModel.setRows(columns);
        Tables.refresh(columnTable);

        // Folded here, with synthesised parents summed and ratios recomputed, then handed to
        // the pivot component; nothing downstream aggregates again.
        String breakdown = stats == null ? null : stats.pivotBreakdown();
        pivotPanel.setTree(PivotTreeBuilder.build(pivots, breakdown), breakdown);
        patternModel.setRows(found.lines());
        Tables.refresh(patternTable);
        patternSummary.setText(patternSummary(found));

        // Asked of ER_DASHBOARD_PIVOT rather than of the tree above: a breakdown that folds
        // to nothing is still a breakdown that was recorded, and the reason to show or hide
        // the tab is whether this reconciliation pivoted at all.
        showBody(!pivots.isEmpty(), found.tracked(), patternTabTitle(found));
    }

    /** The tab's own name, with the count of patterns detected so it reads unopened. */
    private static String patternTabTitle(PatternService.RunPatterns found) {
        return found.exported() ? PATTERN_TAB + " (" + found.presentCount() + ")" : PATTERN_TAB;
    }

    /**
     * The line above the patterns table: how many were detected and what moved.
     *
     * <p>A tracked template with no row on this run is said to be so rather than shown as an
     * empty table: the export skips a run whose diff was truncated or unavailable, and an
     * empty list would read as "no patterns", which is the opposite of what is known.
     */
    private static String patternSummary(PatternService.RunPatterns found) {
        if (!found.exported()) {
            return "Not exported on this run (" + found.knownCount()
                    + " known on this template)";
        }
        StringBuilder text = new StringBuilder()
                .append(found.presentCount()).append(" detected");
        for (PatternTrend trend : List.of(PatternTrend.NEW, PatternTrend.REAPPEARED,
                PatternTrend.INCREASING, PatternTrend.RESOLVED)) {
            long count = found.count(trend);
            if (count > 0) {
                text.append(", ").append(count).append(' ')
                        .append(trend.label().toLowerCase(Locale.ROOT));
            }
        }
        return text.append("  -  double-click for its history").toString();
    }

    private void applyKpis(RowStats stats) {
        if (stats == null) {
            for (KpiCard card : List.of(rateCard, unmatchedCard, missingSourceCard,
                    missingTargetCard, forcedCard)) {
                card.setValue("-", Palette.muted());
                card.setDetail("no statistics recorded");
            }
            return;
        }
        Double rate = stats.matchRate();
        rateCard.setValue(rate == null ? "-" : String.format(Locale.ROOT, "%.2f%%", rate),
                Palette.forRate(rate));
        rateCard.setDetail(String.format(Locale.ROOT, "%,d of %,d rows",
                stats.matched(), stats.totalRows()));
        tile(unmatchedCard, stats.unmatched(), stats.shareOfRows(stats.unmatched()), Palette.error());
        tile(missingSourceCard, stats.missingSource(),
                stats.shareOfRows(stats.missingSource()), Palette.warning());
        tile(missingTargetCard, stats.missingTarget(),
                stats.shareOfRows(stats.missingTarget()), Palette.warning());
        tile(forcedCard, stats.forceMatched(),
                stats.shareOfRows(stats.forceMatched()), Palette.accent());
    }

    /** All four break tiles share the denominator of the match rate, so they are comparable. */
    private void tile(KpiCard card, long value, Double share, java.awt.Color colour) {
        card.setValue(String.format(Locale.ROOT, "%,d", value),
                value == 0L ? Palette.muted() : colour);
        card.setDetail(share == null ? " " : String.format(Locale.ROOT, "%.2f%% of rows", share));
    }

    private void applyContext(RunContextRow context, StatusLabel reconciliationStatus) {
        contextCard.removeAll();
        if (context == null) {
            contextCard.add(new JLabel("No context recorded for this reconciliation."), "span");
        } else {
            // Only populated fields are shown, so the card stays compact; a status of 0 is a
            // real value and is kept.
            addContextEntry("Project", ContextValues.clean(context.name()));
            addContextEntry("Source", ContextValues.clean(context.sourceLabel()));
            addContextEntry("Target", ContextValues.clean(context.targetLabel()));
            addContextEntry("Category 1", ContextValues.clean(context.category1()));
            addContextEntry("Category 2", ContextValues.clean(context.category2()));
            addContextEntry("Category 3", ContextValues.clean(context.category3()));
            addContextEntry("Priority", ContextValues.clean(context.priority()));
            addContextEntry("Owner", ContextValues.clean(context.userName()));
            addContextEntry("Group", ContextValues.clean(context.groupName()));
            addContextBadge("Status", reconciliationStatus);
            addContextEntry("Due date", context.dueDate() == null ? null : context.dueDate().toString());
            addContextEntry("Description", ContextValues.clean(context.description()));
            if (contextCard.getComponentCount() == 0) {
                contextCard.add(new JLabel("No metadata set on this reconciliation."), "span");
            }
        }
        contextCard.revalidate();
        contextCard.repaint();
    }

    /**
     * The reconciliation's outcome, drawn as the badge the tables use.
     *
     * <p>Taken from {@code Reconciliation.status}, which reads ER_DASHBOARD_STAT_ROWS - not
     * from the STATUS on the context row beside it, which EasyRec inserts as 0 and never
     * updates, and which therefore read PASSED on every reconciliation on the screen however
     * many breaks it had found.
     *
     * <p>It is the opposite mapping to the run badge in the header - 0 is a match here, 1 a
     * mismatch - which is why both are read through {@code StatusScope} and never by bare
     * number.
     */
    private void addContextBadge(String label, StatusLabel status) {
        if (status == null || status == StatusLabel.UNKNOWN) {
            return;
        }
        contextCard.add(Sections.createFieldLabel(label));
        StatusBadge badge = new StatusBadge(StatusBadge.of(status));
        badge.setToolTipText("The reconciliation's own outcome, as recorded on its context row");
        contextCard.add(badge);
    }

    private void addContextEntry(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        contextCard.add(Sections.createFieldLabel(label));
        contextCard.add(new JLabel(value));
    }

}
