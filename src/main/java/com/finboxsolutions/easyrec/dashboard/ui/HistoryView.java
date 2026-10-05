package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.service.HistoryService;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.component.TrendChart;
import com.finboxsolutions.easyrec.dashboard.ui.table.HistoryTableModel;
import com.finboxsolutions.swing.jtable.renderers.StandardCellRenderer;

import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The same reconciliations over time, as two trends and the executions behind them.
 *
 * <p>Two charts rather than one with two axes: match rate and break count move on
 * unrelated scales, and overlaying them would make the smaller of the two unreadable. The
 * rate chart is pinned to a 0-100 axis so two of these screens stay comparable at a glance;
 * the break chart fits its own data.
 *
 * <p>A template's history adds the breaks with no rule found to the break chart, as a second
 * line: the total of the template's unexplained patterns, what is still to investigate. An
 * execution whose pattern export was skipped leaves a gap in that line, not a zero.
 */
public class HistoryView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final HistoryService history;
    private final PatternService patterns;
    private final DashboardNavigator navigator;

    /** A history and, for a template's, its unexplained breaks per run; empty otherwise. */
    private record Loaded(HistoryService.History history, Map<Integer, Long> unexplainedByRun) {
    }

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_HISTORY);
    private final JLabel subtitle = new JLabel();

    private final KpiCard latestCard = new KpiCard("Latest match rate", DashboardIcons.ICON_RATE);
    private final KpiCard bestCard = new KpiCard("Best", DashboardIcons.ICON_PASSED);
    private final KpiCard worstCard = new KpiCard("Worst", DashboardIcons.ICON_FAILED);
    private final KpiCard runsCard = new KpiCard("Executions", DashboardIcons.ICON_CLOCK);

    private final TrendChart rateChart = new TrendChart();
    private final TrendChart breaksChart = new TrendChart();

    private final HistoryTableModel tableModel = new HistoryTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection = Tables.section("Executions", table);

    public HistoryView(HistoryService history, PatternService patterns, DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]6[]6[grow,fill]"));
        this.history = history;
        this.patterns = patterns;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildKpiBar(), "gapx 12 12, gaptop 10");
        add(buildCharts(), "gapx 12 12");
        add(tableSection, "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
        Tables.onRowActivated(table, row ->
                navigator.showBatchDetail(tableModel.rowAt(row).batch().batchId()));
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

    private JPanel buildKpiBar() {
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi]"));
        bar.add(latestCard);
        bar.add(bestCard);
        bar.add(worstCard);
        bar.add(runsCard);
        return bar;
    }

    private JPanel buildCharts() {
        JPanel charts = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg chart][grow,fill,sg chart]"));
        charts.setOpaque(false);
        charts.add(titled("Match rate over time", rateChart));
        charts.add(titled("Breaks over time", breaksChart));
        return charts;
    }

    private JPanel titled(String caption, Component chart) {
        return Sections.createFilledSection(caption, chart);
    }

    private void applyRenderers() {
        Tables.renderer(table, "Batch", Renderers.identifier());
        Tables.renderer(table, "Match Rate", Renderers.matchRate());
        Tables.renderer(table, "Rate Change", Renderers.delta("%", true));
        Tables.renderer(table, "Breaks", Renderers.count());
        // Fewer breaks is better, so a negative change is the improvement here, and a count
        // of rows is a whole number.
        Tables.renderer(table, "Breaks Change", Renderers.delta("", false, 0));
        Tables.renderer(table, "Passed", Renderers.numeric(0, Palette.success()));
        Tables.renderer(table, "Failed", Renderers.numeric(0, Palette.error()));
        table.getColumnModel().getColumn(0).setCellRenderer(new CurrentRowRenderer());
    }

    public void loadBatchHistory(int batchId) {
        title.setText("History of batch " + batchId);
        DashboardTask.run(this, "The batch history",
                () -> new Loaded(history.batchHistory(batchId), Map.of()), this::apply);
    }

    public void loadTemplateHistory(int templateId) {
        title.setText("History of template " + templateId);
        DashboardTask.run(this, "The template history", () -> {
            HistoryService.History found = history.templateHistory(templateId);
            List<Integer> runIds = new ArrayList<>();
            for (HistoryService.Execution execution : found.executions()) {
                runIds.addAll(execution.runIds());
            }
            return new Loaded(found, patterns.unexplainedByRun(templateId, runIds));
        }, this::apply);
    }

    private void apply(Loaded loaded) {
        HistoryService.History found = loaded.history();
        subtitle.setText(found.templatePaths().isEmpty()
                ? found.executions().size() + " executions"
                : found.executions().size() + " executions over "
                        + found.templatePaths().size() + " templates");

        boolean withUnexplained = !loaded.unexplainedByRun().isEmpty();
        List<TrendChart.Point> ratePoints = new ArrayList<>();
        List<TrendChart.Point> breakPoints = new ArrayList<>();
        List<Double> unexplainedPoints = new ArrayList<>();
        for (HistoryService.Execution execution : found.executions()) {
            Double unexplained = withUnexplained
                    ? unexplainedOf(execution, loaded.unexplainedByRun()) : null;
            String tooltip = execution.longLabel() + " \u2022 batch " + execution.batch().batchId()
                    + (execution.matchRate() == null ? ""
                            : String.format(Locale.ROOT, " \u2022 %.2f%%", execution.matchRate()))
                    + (execution.breaks() == null ? ""
                            : String.format(Locale.ROOT, " \u2022 %,d breaks", execution.breaks()))
                    + (!withUnexplained ? ""
                            : unexplained == null ? " \u2022 no pattern data"
                            : String.format(Locale.ROOT, " \u2022 %,.0f with no rule found", unexplained));
            ratePoints.add(new TrendChart.Point(execution.shortLabel(), execution.matchRate(),
                    tooltip, execution.current()));
            breakPoints.add(new TrendChart.Point(execution.shortLabel(),
                    execution.breaks() == null ? null : execution.breaks().doubleValue(),
                    tooltip, execution.current()));
            unexplainedPoints.add(unexplained);
        }
        // The rate axis is fixed at 100 so two history screens read on the same scale.
        rateChart.setPoints(ratePoints, "%", 100.0d);
        breaksChart.setPoints(breakPoints, "", null);
        if (withUnexplained) {
            breaksChart.setSecondary(unexplainedPoints, "All breaks", "No rule found");
        }

        HistoryService.Execution latest = found.latest();
        latestCard.setValue(latest == null || latest.matchRate() == null ? "-"
                        : String.format(Locale.ROOT, "%.2f%%", latest.matchRate()),
                Palette.forRate(latest == null ? null : latest.matchRate()));
        latestCard.setDelta(latest == null ? null : latest.rateDelta(), "%", true);
        bestCard.setValue(format(found.bestRate()), Palette.success());
        bestCard.setDetail(" ");
        worstCard.setValue(format(found.worstRate()), Palette.error());
        worstCard.setDetail(" ");
        runsCard.setValue(String.valueOf(found.executions().size()), Palette.text());
        runsCard.setDetail(" ");

        tableModel.setRows(found.newestFirst());
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, found.executions().size()
                + " executions, newest first  -  double-click a row to open that batch");
    }

    /**
     * The unexplained breaks of an execution's runs, or null when none of them has pattern
     * data - a skipped export is a gap, not a zero.
     */
    private static Double unexplainedOf(HistoryService.Execution execution,
                                        Map<Integer, Long> unexplainedByRun) {
        Long total = null;
        for (Integer runId : execution.runIds()) {
            Long count = unexplainedByRun.get(runId);
            if (count != null) {
                total = total == null ? count : total + count;
            }
        }
        return total == null ? null : total.doubleValue();
    }

    private static String format(Double rate) {
        return rate == null ? "-" : String.format(Locale.ROOT, "%.2f%%", rate);
    }

    /** Marks the batch the history was opened from, so it can be found in a long list. */
    private final class CurrentRowRenderer extends StandardCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable rendered, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(rendered, value, selected, focused, row, column);
            if (tableModel.isCurrent(rendered.convertRowIndexToModel(row))) {
                setFont(getFont().deriveFont(Font.BOLD));
                if (!selected) {
                    setForeground(Palette.accent());
                }
            }
            return this;
        }

        @Override
        protected void setColor(JTable rendered, Object value, boolean selected) {
            // Unconditionally, both branches. The colour of the row marked current is
            // applied on top of this, and a base that only ever set it for that one row
            // would leave the colour standing for every row painted after it.
            if (selected) {
                setForeground(rendered.getSelectionForeground());
                setBackground(rendered.getSelectionBackground());
                return;
            }
            setForeground(rendered.getForeground());
            setBackground(rendered.getBackground());
        }
    }
}
