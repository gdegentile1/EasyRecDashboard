package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.common.gui.text.HintTextField;
import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.SegmentedControl;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.table.BatchTableModel;
import net.miginfocom.swing.MigLayout;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The landing screen: one-click date windows, headline figures, and the batches that fall
 * in the chosen range.
 *
 * <p>The period buttons carry an explicit date range rather than a period name, so the
 * range stays the single source of truth: a button reads as selected exactly when the
 * current range equals its own, whether the range came from the button or from the two
 * date fields beside it.
 */
public class HomeView extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final String DATE_HINT = "yyyy-mm-dd";

    private final DashboardDao dao;
    private final DashboardService service;
    private final DashboardNavigator navigator;

    /**
     * The period buttons, as one control with three positions rather than three buttons -
     * the same move the pivot bar makes for its level buttons.
     */
    private final SegmentedControl periodBar = new SegmentedControl();

    private final HintTextField fromField = new HintTextField(DATE_HINT, 10);
    private final HintTextField toField = new HintTextField(DATE_HINT, 10);

    private final KpiCard batchesCard = new KpiCard("Batches in range", DashboardIcons.ICON_BATCHES);
    private final KpiCard rateCard = new KpiCard("Match rate", DashboardIcons.ICON_RATE);
    private final KpiCard passedCard = new KpiCard("Reconciliations passed", DashboardIcons.ICON_PASSED);
    private final KpiCard failedCard = new KpiCard("Reconciliations failed", DashboardIcons.ICON_FAILED);

    private final BatchTableModel tableModel = new BatchTableModel();
    private final DashboardTable table = Tables.create(tableModel);
    private final JPanel tableSection =
            Tables.section("Recent batches", table, "views/dashboard_main.xml");

    private LocalDate from;
    private LocalDate to;

    public HomeView(DashboardDao dao, DashboardService service, DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]12[grow,fill]"));
        this.dao = dao;
        this.service = service;
        this.navigator = navigator;

        // The widest window is also the default, so the button highlighted on first load
        // agrees with what the table below it shows.
        DashboardService.Period widest = DashboardService.PERIODS.get(DashboardService.PERIODS.size() - 1);
        this.to = LocalDate.now();
        this.from = to.minusDays(widest.days() - 1L);

        add(buildToolBar());
        add(buildKpiBar(), "gapx 12 12, gaptop 10");
        add(tableSection, "grow, push, gapx 12 12, gapbottom 8");

        applyRenderers();
        Tables.onRowActivated(table, row -> navigator.showBatchDetail(
                tableModel.rowAt(row).batch().batchId()));
    }

    // ========================================================================= GUI assembly

    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8",
                "[]8[]8[][]4[][]4[][]4[]push[]", "[]"));

        bar.add(new JLabel(DashboardIcons.ICON_CLOCK));
        bar.add(periodBar);
        bar.add(Sections.separator());

        bar.add(new JLabel(DashboardIcons.ICON_CALENDAR));
        bar.add(Sections.createFieldLabel("From"));
        bar.add(fromField);
        bar.add(Sections.createFieldLabel("To"));
        bar.add(toField);

        bar.add(Sections.createIconButton(DashboardIcons.ICON_FILTER,
                "Apply the typed date range", event -> {
                    from = parse(fromField.getText(), from);
                    to = parse(toField.getText(), to);
                    reload();
                }));

        bar.add(Sections.createButton(DashboardIcons.ICON_OPEN, "All batches",
                "Open the full batch list, with its own filters",
                event -> navigator.showBatchList()));
        return bar;
    }

    private JPanel buildKpiBar() {
        JPanel bar = new JPanel(new MigLayout("insets 0, fillx",
                "[grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi][grow,fill,sg kpi]"));
        bar.setOpaque(false);
        bar.add(batchesCard);
        bar.add(rateCard);
        bar.add(passedCard);
        bar.add(failedCard);
        return bar;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Batch", Renderers.identifier());
        Tables.renderer(table, "Status", Renderers.status());
        Tables.renderer(table, "Match Rate", Renderers.matchRate());
        Tables.renderer(table, "Passed", Renderers.numeric(0, Palette.success()));
        Tables.renderer(table, "Failed", Renderers.numeric(0, Palette.error()));
        for (String column : DashboardService.RUN_COLUMNS) {
            Tables.renderer(table, column, Renderers.foldedText());
        }
        Tables.renderer(table, "Description", Renderers.foldedText());
    }

    // ================================================================================ load

    /** Reloads from the database. Safe to call repeatedly; the previous view stays until done. */
    public void reload() {
        fromField.setText(ISO.format(from));
        toField.setText(ISO.format(to));

        LocalDate rangeFrom = from;
        LocalDate rangeTo = to;
        DashboardTask.run(this, "The dashboard", () -> {
            List<BatchRow> all = dao.findAllBatches();
            List<BatchRow> inRange = service.findBatchesInRange(all, rangeFrom, rangeTo);
            List<BatchRow> recent = inRange.size() > DashboardService.HOME_RECENT_LIMIT
                    ? inRange.subList(0, DashboardService.HOME_RECENT_LIMIT)
                    : inRange;

            // The buttons and the table both break batches down into passed and failed
            // reconciliations, so they are counted once over the union: the periods overlap
            // each other, and a hand-picked range can fall outside them all.
            List<Integer> union = new ArrayList<>();
            for (BatchRow batch : recent) {
                union.add(batch.batchId());
            }
            for (DashboardService.Period period : DashboardService.PERIODS) {
                LocalDate start = rangeTo.minusDays(period.days() - 1L);
                for (BatchRow batch : service.findBatchesInRange(all, start, LocalDate.now())) {
                    if (!union.contains(batch.batchId())) {
                        union.add(batch.batchId());
                    }
                }
            }
            Map<Integer, int[]> counts = service.reconciliationStatusCounts(union);

            Map<String, Object> loaded = new LinkedHashMap<>();
            loaded.put("summaries", service.summarise(recent));
            loaded.put("cards", service.periodCards(all, LocalDate.now(), counts));
            loaded.put("total", inRange.size());
            return loaded;
        }, this::apply);
    }

    @SuppressWarnings("unchecked")
    private void apply(Map<String, Object> loaded) {
        List<DashboardService.BatchSummary> summaries =
                (List<DashboardService.BatchSummary>) loaded.get("summaries");
        List<DashboardService.PeriodCard> cards =
                (List<DashboardService.PeriodCard>) loaded.get("cards");
        int total = (Integer) loaded.get("total");

        rebuildPeriodButtons(cards);
        tableModel.setRows(summaries);
        Tables.refresh(table);

        int passed = 0;
        int failed = 0;
        for (DashboardService.BatchSummary summary : summaries) {
            passed += summary.reconciliationsPassed();
            failed += summary.reconciliationsFailed();
        }
        Double rate = null;
        for (DashboardService.BatchSummary summary : summaries) {
            if (summary.matchRate() != null) {
                // The list already carries a per-batch rate; the headline is the mean of the
                // batches on show, which is what the caption says it is.
                rate = rate == null ? summary.matchRate() : rate + summary.matchRate();
            }
        }
        if (rate != null && !summaries.isEmpty()) {
            rate = rate / summaries.size();
        }

        batchesCard.setValue(String.valueOf(total), Palette.text());
        batchesCard.setDetail(total > summaries.size()
                ? "showing the " + summaries.size() + " most recent" : " ");
        rateCard.setValue(rate == null ? "-" : String.format(Locale.ROOT, "%.2f%%", rate),
                Palette.forRate(rate));
        rateCard.setDetail("mean across listed batches");
        passedCard.setValue(String.valueOf(passed), Palette.success());
        passedCard.setDetail(" ");
        failedCard.setValue(String.valueOf(failed), Palette.error());
        failedCard.setDetail(" ");

        Sections.setSectionTitle(tableSection,
                "Recent batches (" + summaries.size() + " of " + total + ")"
                        + "  -  double-click a row to open it");
    }

    /** Rebuilds the period control from the counts the load came back with. */
    private void rebuildPeriodButtons(List<DashboardService.PeriodCard> cards) {
        periodBar.clearSegments();
        for (DashboardService.PeriodCard card : cards) {
            periodBar.addSegment(card.period().label(),
                    String.valueOf(card.count()),
                    card.count() + (card.count() == 1 ? " batch" : " batches") + ", "
                            + card.passed() + " reconciliations passed and "
                            + card.failed() + " failed",
                    card.from().equals(from) && card.to().equals(to),
                    () -> {
                        from = card.from();
                        to = card.to();
                        reload();
                    });
        }
        periodBar.revalidate();
        periodBar.repaint();
    }

    /** Falls back to the current value on an unparseable date rather than failing the screen. */
    private static LocalDate parse(String raw, LocalDate fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(raw.trim(), ISO);
        } catch (DateTimeParseException ignored) {
            return fallback;
        }
    }
}
