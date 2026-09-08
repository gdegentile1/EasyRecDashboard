package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.service.CompareService;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.table.CompareTableModel;
import com.finboxsolutions.swing.jtable.renderers.StandardCellRenderer;

import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Two to six batches side by side, at template level and then at column level.
 *
 * <p>Rows are ordered by spread, so the reconciliations that moved most are at the top
 * rather than buried alphabetically. Spread says how far apart the batches are; the delta
 * beside it says whether the movement from the oldest to the newest was an improvement.
 */
public class CompareView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DashboardDao dao;
    private final DashboardService service;
    private final CompareService compare;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_COMPARE);
    private final JLabel hint = new JLabel();
    private final JPanel totalsBar = new JPanel(new MigLayout("insets 0, fillx"));

    private final CompareTableModel tableModel = new CompareTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection = Tables.section("Comparison", table);

    private List<Integer> selection = List.of();

    public CompareView(DashboardDao dao, DashboardService service, CompareService compare,
                       DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]10[grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.compare = compare;
        this.navigator = navigator;

        add(buildToolBar());
        add(totalsBar, "gapx 12 12, gaptop 10");
        add(tableSection, "grow, push, gapx 12 12, gapbottom 8");

        Tables.onRowActivated(table, row -> {
            if (tableModel.measure() == CompareTableModel.CellMeasure.MATCH_RATE) {
                navigator.showTemplateCompare(selection, tableModel.rowAt(row).label());
            }
        });
    }

    /** The screen's identity and the rule it is ordered by, in the application's action bar. */
    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[]12[grow,fill]", "[]"));

        JPanel labels = new JPanel(new MigLayout("insets 0, wrap 1, gapy 0", "[]"));
        labels.setOpaque(false);
        hint.setForeground(Palette.muted());
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        labels.add(title);
        labels.add(hint);

        bar.add(labels);
        bar.add(Sections.spacer(), "growx");
        return bar;
    }

    /** Template-level comparison: one row per template FULL_PATH. */
    public void loadTemplates(List<Integer> batchIds) {
        this.selection = List.copyOf(batchIds);
        title.setText("Comparing " + batchIds.size() + " batches");
        hint.setText("Ordered by spread. Double-click a template to compare its columns.");
        load(batchIds, null);
    }

    /** Column-level comparison of one template path across the same selection. */
    public void loadColumns(List<Integer> batchIds, String templatePath) {
        this.selection = List.copyOf(batchIds);
        title.setText("Comparing columns of " + shortName(templatePath));
        hint.setText(templatePath);
        load(batchIds, templatePath);
    }

    private void load(List<Integer> batchIds, String templatePath) {
        DashboardTask.run(this, "The comparison", () -> {
            Map<String, Object> loaded = new LinkedHashMap<>();
            List<BatchRow> batches = compare.resolveSelection(dao.findAllBatches(), batchIds);
            List<Integer> ordered = new ArrayList<>(batches.size());
            for (BatchRow batch : batches) {
                ordered.add(batch.batchId());
            }
            Map<Integer, List<RunRow>> runsByBatch = dao.findRunsByBatch(ordered);
            Map<Integer, List<DashboardService.Reconciliation>> byBatch = new LinkedHashMap<>();
            for (Integer batchId : ordered) {
                List<Integer> runIds = new ArrayList<>();
                for (RunRow run : runsByBatch.getOrDefault(batchId, List.of())) {
                    runIds.add(run.runId());
                }
                byBatch.put(batchId, service.findReconciliations(runIds, null));
            }
            loaded.put("batches", batches);
            loaded.put("ordered", ordered);
            loaded.put("totals", compare.totals(ordered, byBatch));
            loaded.put("rows", templatePath == null
                    ? compare.compareTemplates(ordered, byBatch)
                    : compare.compareColumns(ordered, byBatch, templatePath));
            loaded.put("columnLevel", templatePath != null);
            return loaded;
        }, this::apply);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> loaded) {
        List<BatchRow> batches = (List<BatchRow>) loaded.get("batches");
        List<Integer> ordered = (List<Integer>) loaded.get("ordered");
        Map<Integer, CompareService.BatchTotals> totals =
                (Map<Integer, CompareService.BatchTotals>) loaded.get("totals");
        List<CompareService.CompareRow> rows =
                (List<CompareService.CompareRow>) loaded.get("rows");
        boolean columnLevel = (Boolean) loaded.get("columnLevel");

        this.selection = ordered;
        rebuildTotals(batches, totals);
        tableModel.setRows(columnLevel ? "Column" : "Template Path", ordered, rows,
                columnLevel ? CompareTableModel.CellMeasure.MATCH_PERCENTAGE
                        : CompareTableModel.CellMeasure.MATCH_RATE);
        table.structureChanged();
        table.setSortable(false);
        applyRenderers();
        Tables.refresh(table);
        Sections.setSectionTitle(tableSection, columnLevel
                ? rows.size() + " columns, widest spread first"
                : rows.size() + " templates, widest spread first"
                        + "  -  double-click one to compare its columns");
    }

    private void applyRenderers() {
        table.getColumnModel().getColumn(0).setCellRenderer(Renderers.foldedText());
        for (int index = 1; index < table.getColumnCount() - 2; index++) {
            table.getColumnModel().getColumn(index).setCellRenderer(new CompareCellRenderer(index));
        }
        Tables.renderer(table, "Spread", Renderers.numeric(2, Palette.muted()));
        // A wider spread is worse, and an improving delta is a rising match rate.
        Tables.renderer(table, "Delta", Renderers.delta("", true));
    }

    /** The batch columns as cards, oldest first, so the leftmost column is the baseline. */
    private void rebuildTotals(List<BatchRow> batches,
                               Map<Integer, CompareService.BatchTotals> totals) {
        totalsBar.removeAll();
        totalsBar.setLayout(new MigLayout("insets 0, fillx",
                "[100:100,grow,fill,sg batch]".repeat(Math.max(batches.size(), 1))));
        for (BatchRow batch : batches) {
            CompareService.BatchTotals found = totals.get(batch.batchId());
            JPanel card = new JPanel(new MigLayout("insets 10, wrap 1", "[grow,fill]"));
            Sections.styleCardPanel(card);

            JLabel heading = new JLabel("Batch " + batch.batchId(), DashboardIcons.ICON_BATCH,
                    javax.swing.SwingConstants.LEADING);
            heading.setIconTextGap(6);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 12f));
            card.add(heading);

            JLabel when = new JLabel(batch.when() == null ? "-" : batch.when().toString());
            when.setForeground(Palette.muted());
            when.setFont(when.getFont().deriveFont(Font.PLAIN, 11f));
            card.add(when);

            Double rate = found == null ? null : found.matchRate();
            JLabel rateLabel = new JLabel(rate == null ? "-"
                    : String.format(Locale.ROOT, "%.2f%%", rate));
            rateLabel.setFont(rateLabel.getFont().deriveFont(Font.BOLD, 18f));
            rateLabel.setForeground(Palette.forRate(rate));
            card.add(rateLabel);

            JLabel detail = new JLabel(found == null ? " "
                    : String.format(Locale.ROOT, "%,d breaks \u2022 %d templates",
                            found.breaks(), found.templateCount()));
            detail.setForeground(Palette.muted());
            detail.setFont(detail.getFont().deriveFont(Font.PLAIN, 11f));
            card.add(detail);

            // How much data each batch actually reconciled. Two batches whose match rates
            // are a point apart are not comparable at all if one of them ran a tenth of the
            // volume, and the rate above says nothing about that. The spreads and deltas in
            // the table below are read against these.
            JLabel volume = new JLabel(found == null ? " "
                    : String.format(Locale.ROOT, "%,d source \u2022 %,d target rows",
                            found.rowsSource(), found.rowsTarget()));
            volume.setForeground(Palette.muted());
            volume.setFont(volume.getFont().deriveFont(Font.PLAIN, 11f));
            card.add(volume);

            totalsBar.add(card);
        }
        totalsBar.revalidate();
        totalsBar.repaint();
    }

    /**
     * One batch's cell.
     *
     * <p>A batch that never ran this row is drawn differently from one that ran it and has
     * no measurable value: the first is a gap in coverage, the second is a real result, and
     * a comparison that conflated them would be misleading.
     */
    private final class CompareCellRenderer extends StandardCellRenderer {

        private static final long serialVersionUID = 1L;

        /** What a batch that never ran this row shows: an em dash, not a zero. */
        private static final String ABSENT = "\u2014";

        private final int columnIndex;

        private CompareCellRenderer(int columnIndex) {
            this.columnIndex = columnIndex;
            getColumnAttribute().setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable rendered, Object value,
                boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(rendered, value, selected, focused, row, column);
            boolean absent = tableModel.isAbsent(rendered.convertRowIndexToModel(row), columnIndex);
            setToolTipText(absent ? "This batch did not run it" : null);
            if (absent) {
                setText(ABSENT);
                if (!selected) {
                    setForeground(Palette.muted());
                }
            }
            return this;
        }

        @Override
        protected void setColor(JTable rendered, Object value, boolean selected) {
            if (selected) {
                setForeground(rendered.getSelectionForeground());
                setBackground(rendered.getSelectionBackground());
                return;
            }
            setForeground(Palette.forRate(value instanceof Number number
                    ? number.doubleValue() : null));
            setBackground(rendered.getBackground());
        }

        @Override
        public String toString(Object value) {
            return value instanceof Number number
                    ? String.format(Locale.ROOT, "%.2f%%", number.doubleValue())
                    : "-";
        }
    }

    private static String shortName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return cut < 0 ? path : path.substring(cut + 1);
    }
}
