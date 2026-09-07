package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.service.CompareService;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.HistoryService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import net.miginfocom.swing.MigLayout;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * The dashboard module's root panel: drop this into a tab of the EasyRec window.
 *
 * <p>Holds one instance of each screen and swaps between them with a CardLayout. The
 * screens are built once and reloaded on navigation rather than recreated, so a table's
 * column widths and sort survive a drill-down and a return.
 *
 * <p>Nothing here talks to JDBC. Every screen loads through {@link DashboardTask}, which
 * keeps the queries off the EDT.
 */
public class DashboardPanel extends JPanel implements DashboardNavigator {

    private static final long serialVersionUID = 1L;

    private static final String HOME = "home";
    private static final String BATCH_LIST = "batches";
    private static final String BATCH_DETAIL = "batch";
    private static final String RECONCILIATION = "reconciliation";
    private static final String COMPARE = "compare";
    private static final String HISTORY = "history";

    /** One entry of the trail: a card, how to reload it, and what to call it. */
    private record Step(String card, Runnable reload, String breadcrumb, Icon icon) {
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel breadcrumbBar = new JPanel(new MigLayout("insets 0, gap 2", "[]", "[]"));
    private final JButton backButton;

    /**
     * The trail, oldest first. Also the back stack: Back is "the step before the last one",
     * and a breadcrumb click is the same move with a further-back target.
     */
    private final List<Step> trail = new ArrayList<>();

    private final DashboardDao dao;
    private final DashboardService dashboard;
    private final CompareService compare;
    private final HistoryService historyService;

    private final HomeView homeView;
    private final BatchListView batchListView;
    private final BatchDetailView batchDetailView;
    private final ReconciliationView reconciliationView;
    private final CompareView compareView;
    private final HistoryView historyView;

    public DashboardPanel(DashboardDao dao) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[grow,fill]"));
        this.dao = dao;
        this.dashboard = new DashboardService(dao);
        this.compare = new CompareService(dao, dashboard);
        this.historyService = new HistoryService(dao, dashboard);

        this.homeView = new HomeView(dao, dashboard, this);
        this.batchListView = new BatchListView(dao, dashboard, this);
        this.batchDetailView = new BatchDetailView(dao, dashboard, this);
        this.reconciliationView = new ReconciliationView(dao, dashboard, this);
        this.compareView = new CompareView(dao, dashboard, compare, this);
        this.historyView = new HistoryView(historyService, this);

        this.backButton = Sections.createIconButton(DashboardIcons.ICON_BACK, "Back",
                event -> goBack());

        content.add(homeView, HOME);
        content.add(batchListView, BATCH_LIST);
        content.add(batchDetailView, BATCH_DETAIL);
        content.add(reconciliationView, RECONCILIATION);
        content.add(compareView, COMPARE);
        content.add(historyView, HISTORY);

        add(buildToolBar());
        add(content, "grow, push");

        showHome();
    }

    // ========================================================================= GUI assembly

    /**
     * The action bar: navigation on the left, the trail in the middle, refresh on the right.
     *
     * <p>Laid out like {@code PanelPivotActionBar} - grouped buttons, a {@code " | "}
     * between groups, and a pushing spacer that holds the trailing action against the right
     * edge whatever the window width.
     */
    private JPanel buildToolBar() {
        JPanel bar = Sections.createToolBar();
        bar.setLayout(new MigLayout("insets 4 8 4 8", "[][][]4[][grow,fill][]", "[]"));

        bar.add(backButton);
        bar.add(Sections.createIconButton(DashboardIcons.ICON_HOME, "Dashboard home",
                event -> showHome()));
        bar.add(Sections.createIconButton(DashboardIcons.ICON_BATCHES, "All batches",
                event -> showBatchList()));
        bar.add(Sections.separator());
        bar.add(breadcrumbBar, "growx");
        bar.add(Sections.createIconButton(DashboardIcons.ICON_REFRESH,
                "Reload the current screen", event -> reloadCurrent()));
        return bar;
    }

    /**
     * Repaints the trail as a row of links ending in the screen on show.
     *
     * <p>Every crumb but the last is clickable, and clicking one unwinds to it rather than
     * pushing a new step: a trail whose own entries grew the stack would never shorten.
     */
    private void rebuildBreadcrumb() {
        breadcrumbBar.removeAll();
        breadcrumbBar.add(crumb("Dashboard", DashboardIcons.ICON_HOME, !trail.isEmpty(),
                this::showHome));
        for (int index = 0; index < trail.size(); index++) {
            Step step = trail.get(index);
            boolean last = index == trail.size() - 1;
            int target = index;
            breadcrumbBar.add(new JLabel(DashboardIcons.ICON_CRUMB));
            breadcrumbBar.add(crumb(step.breadcrumb(), step.icon(), !last, () -> unwindTo(target)));
        }
        backButton.setEnabled(trail.size() > 1);
        breadcrumbBar.revalidate();
        breadcrumbBar.repaint();
    }

    /** One crumb: a link while there is something beyond it, plain bold once it is the screen. */
    private JLabel crumb(String text, Icon icon, boolean clickable, Runnable action) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setIconTextGap(5);
        label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        if (clickable) {
            label.setForeground(Palette.accent());
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
            label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            label.setToolTipText("Back to " + text);
            label.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent event) {
                    action.run();
                }
            });
        } else {
            label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        }
        return label;
    }

    // ========================================================================== navigation

    @Override
    public void showHome() {
        trail.clear();
        cards.show(content, HOME);
        rebuildBreadcrumb();
        homeView.reload();
    }

    @Override
    public void showBatchList() {
        push(BATCH_LIST, this::showBatchList, "Batches", DashboardIcons.ICON_BATCHES);
        cards.show(content, BATCH_LIST);
        batchListView.reload();
    }

    @Override
    public void showBatchDetail(int batchId) {
        push(BATCH_DETAIL, () -> showBatchDetail(batchId), "Batch " + batchId,
                DashboardIcons.ICON_BATCH);
        cards.show(content, BATCH_DETAIL);
        batchDetailView.load(batchId);
    }

    @Override
    public void showReconciliation(int runId, int templateId) {
        push(RECONCILIATION, () -> showReconciliation(runId, templateId),
                "Run " + runId + " \u2022 template " + templateId,
                DashboardIcons.ICON_RECONCILIATION);
        cards.show(content, RECONCILIATION);
        reconciliationView.load(runId, templateId);
    }

    @Override
    public void showBatchHistory(int batchId) {
        push(HISTORY, () -> showBatchHistory(batchId), "History \u2022 batch " + batchId,
                DashboardIcons.ICON_HISTORY);
        cards.show(content, HISTORY);
        historyView.loadBatchHistory(batchId);
    }

    @Override
    public void showTemplateHistory(int templateId) {
        push(HISTORY, () -> showTemplateHistory(templateId),
                "History \u2022 template " + templateId, DashboardIcons.ICON_HISTORY);
        cards.show(content, HISTORY);
        historyView.loadTemplateHistory(templateId);
    }

    @Override
    public void showCompare(List<Integer> batchIds) {
        List<Integer> selection = List.copyOf(batchIds);
        push(COMPARE, () -> showCompare(selection), "Comparing " + selection.size() + " batches",
                DashboardIcons.ICON_COMPARE);
        cards.show(content, COMPARE);
        compareView.loadTemplates(selection);
    }

    @Override
    public void showTemplateCompare(List<Integer> batchIds, String templatePath) {
        List<Integer> selection = List.copyOf(batchIds);
        push(COMPARE, () -> showTemplateCompare(selection, templatePath),
                "Columns \u2022 " + shortName(templatePath), DashboardIcons.ICON_COMPARE);
        cards.show(content, COMPARE);
        compareView.loadColumns(selection, templatePath);
    }

    @Override
    public void goBack() {
        if (trail.size() < 2) {
            showHome();
            return;
        }
        unwindTo(trail.size() - 2);
    }

    /**
     * Returns to the step at {@code index}, dropping everything after it.
     *
     * <p>The step itself is dropped too: its own reload pushes it back, which is what keeps
     * one code path responsible for what the trail contains.
     */
    private void unwindTo(int index) {
        if (index < 0 || index >= trail.size()) {
            showHome();
            return;
        }
        Step target = trail.get(index);
        while (trail.size() > index) {
            trail.remove(trail.size() - 1);
        }
        target.reload().run();
    }

    /** Reloads the screen on show, without disturbing the trail. */
    private void reloadCurrent() {
        if (trail.isEmpty()) {
            showHome();
            return;
        }
        trail.get(trail.size() - 1).reload().run();
    }

    /**
     * Records a step, unless it is the same screen being reloaded in place.
     *
     * <p>Without that guard a re-sort or a filter change would grow the trail, and Back
     * would walk through states of one screen rather than back out of it.
     */
    private void push(String card, Runnable reload, String label, Icon icon) {
        if (!trail.isEmpty()) {
            Step current = trail.get(trail.size() - 1);
            if (current.card().equals(card) && current.breadcrumb().equals(label)) {
                trail.remove(trail.size() - 1);
            }
        }
        trail.add(new Step(card, reload, label, icon));
        rebuildBreadcrumb();
    }

    private static String shortName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return cut < 0 ? path : path.substring(cut + 1);
    }

    /** Exposed so an embedding frame can reuse the same services. */
    public DashboardService service() {
        return dashboard;
    }

    public DashboardDao dao() {
        return dao;
    }
}
