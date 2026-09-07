package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;
import com.finboxsolutions.easyrec.dashboard.service.ContextValues;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.pivot.DashboardPivotPanel;
import com.finboxsolutions.easyrec.dashboard.ui.table.ColumnStatsTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One reconciliation: its metadata, its row-level KPIs, its column statistics and its
 * pivot breakdown.
 *
 * <p>Always scoped to a single template. A run covers many templates at once, so an
 * unscoped run screen would pile every column statistic of every template onto one view.
 */
public class ReconciliationView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DashboardDao dao;
    private final DashboardService service;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_RECONCILIATION);
    private final JLabel subtitle = new JLabel();

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
    private final JPanel columnSection = Tables.section("Column statistics", columnTable);

    private final DashboardPivotPanel pivotPanel = new DashboardPivotPanel();

    private int runId;
    private int templateId;

    public ReconciliationView(DashboardDao dao, DashboardService service,
                              DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]6[]6[grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildKpiBar(), "gapx 12 12, gaptop 10");
        add(contextCard, "gapx 12 12");
        add(buildBody(), "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
    }

    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[]12[grow,fill][]", "[]"));

        JPanel labels = new JPanel(new MigLayout("insets 0, wrap 1, gapy 0", "[]"));
        labels.setOpaque(false);
        subtitle.setForeground(Palette.muted());
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 11f));
        labels.add(title);
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
     * Column statistics above, the pivot breakdown below.
     *
     * <p>A split rather than tabs: the two answer different halves of the same question -
     * which field disagrees, and where - and comparing them is the usual reason to be here.
     */
    private JSplitPane buildBody() {
        // The pivot half is EasyRec's own component, action bar included, so it carries its
        // own toolbar and is framed rather than titled.
        JPanel pivotSection = Sections.createFilledSection("Pivot breakdown", pivotPanel);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, columnSection, pivotSection);
        split.setResizeWeight(0.5d);
        split.setBorder(null);
        return split;
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
            loaded.put("template", dao.findTemplate(requestedTemplateId));
            if (target != null && target.statsTemplateId() != null) {
                loaded.put("columns", dao.findColumnStats(requestedRunId, target.statsTemplateId()));
                loaded.put("pivots", dao.findPivots(requestedRunId, target.statsTemplateId()));
            } else {
                loaded.put("columns", List.<ColumnStats>of());
                loaded.put("pivots", List.<PivotRow>of());
            }
            return loaded;
        }, this::apply);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> loaded) {
        DashboardService.Reconciliation rec =
                (DashboardService.Reconciliation) loaded.get("reconciliation");
        TemplateRow template = (TemplateRow) loaded.get("template");
        List<ColumnStats> columns = (List<ColumnStats>) loaded.get("columns");
        List<PivotRow> pivots = (List<PivotRow>) loaded.get("pivots");

        title.setText(template == null ? "Template " + templateId : template.shortName());
        subtitle.setText(template == null || template.fullPath() == null
                ? "Run " + runId : template.fullPath());

        RowStats stats = rec == null ? null : rec.stats();
        applyKpis(stats);
        applyContext(rec == null ? null : rec.context());

        columnModel.setRows(columns);
        Tables.refresh(columnTable);

        // Folded here, with synthesised parents summed and ratios recomputed, then handed to
        // the pivot component; nothing downstream aggregates again.
        pivotPanel.setTree(PivotTreeBuilder.build(pivots,
                stats == null ? null : stats.pivotBreakdown()));
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

    private void applyContext(RunContextRow context) {
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
            addContextEntry("Status", context.statusCode() == null ? null : context.status().name());
            addContextEntry("Due date", context.dueDate() == null ? null : context.dueDate().toString());
            addContextEntry("Description", ContextValues.clean(context.description()));
            if (contextCard.getComponentCount() == 0) {
                contextCard.add(new JLabel("No metadata set on this reconciliation."), "span");
            }
        }
        contextCard.revalidate();
        contextCard.repaint();
    }

    private void addContextEntry(String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        contextCard.add(Sections.createFieldLabel(label));
        contextCard.add(new JLabel(value));
    }

}
