package com.finboxsolutions.easyrec.dashboard.ui;

import com.finboxsolutions.easyrec.dashboard.model.PatternKind;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternSighting;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardIcons;
import com.finboxsolutions.easyrec.dashboard.ui.component.DashboardTable;
import com.finboxsolutions.easyrec.dashboard.ui.component.KpiCard;
import com.finboxsolutions.easyrec.dashboard.ui.component.Palette;
import com.finboxsolutions.easyrec.dashboard.ui.component.Renderers;
import com.finboxsolutions.easyrec.dashboard.ui.component.Sections;
import com.finboxsolutions.easyrec.dashboard.ui.component.Tables;
import com.finboxsolutions.easyrec.dashboard.ui.component.TrendChart;
import com.finboxsolutions.easyrec.dashboard.ui.table.PatternHistoryTableModel;
import com.finboxsolutions.easyrec.dashboard.ui.table.PatternSightingTableModel;
import net.miginfocom.swing.MigLayout;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Component;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One pattern over time: its count on every run that looked for it, the other templates it
 * was found on, and how to find the RCA rule that explains it.
 *
 * <p>Nothing on this screen qualifies a pattern. Explaining a pattern means creating an RCA
 * rule from it in EasyRec, which writes its comment on every break of the pattern; the
 * dashboard lacks the values a rule needs, so it shows the name EasyRec gives that rule and,
 * given a rules file, whether a Groovy rule of that name is in it. The one thing written from
 * here is LINKED_PATTERN_ID, on the user's request: the earlier pattern this one continues.
 */
public class PatternView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final PatternService patterns;
    private final DashboardNavigator navigator;

    private final JLabel title = Sections.createTitle("", DashboardIcons.ICON_PATTERN);
    private final JLabel subtitle = new JLabel();

    private final JLabel signatureValue = new JLabel();
    private final JLabel ruleValue = new JLabel();
    private final JLabel linkValue = new JLabel();
    private final JButton copySignatureButton = Sections.createIconButton(DashboardIcons.ICON_COPY,
            "Copy the signature", event -> copy(shownSignature()));
    private final JButton copyRuleButton = Sections.createIconButton(DashboardIcons.ICON_COPY,
            "Copy the rule name", event -> copy(shownRuleName()));
    private final JButton checkRuleButton = Sections.createButton(DashboardIcons.ICON_RULE,
            "Check a rules file...", "Look for a Groovy rule of this name in a project's XML rules file",
            event -> checkRulesFile());
    private final JButton linkButton = Sections.createButton(DashboardIcons.ICON_LINK,
            "Link to earlier pattern...", "Say that this pattern continues an earlier one whose "
                    + "signature changed - a template change, a value mapping gaining a pair",
            event -> chooseLink());
    private final JButton unlinkButton = Sections.createButton(DashboardIcons.ICON_UNLINK,
            "Clear link", "Remove the link to the earlier pattern", event -> clearLink());

    private final KpiCard latestCard = new KpiCard("Latest occurrences", DashboardIcons.ICON_BREAKS);
    private final KpiCard peakCard = new KpiCard("Peak", DashboardIcons.ICON_CHART);
    private final KpiCard detectedCard = new KpiCard("Detected on", DashboardIcons.ICON_CLOCK);
    private final KpiCard firstSeenCard = new KpiCard("First seen", DashboardIcons.ICON_NEW);

    private final TrendChart occurrencesChart = new TrendChart();
    private final TrendChart rateChart = new TrendChart();
    private final JPanel charts = new JPanel(new MigLayout("insets 0, fillx",
            "[grow,fill,sg chart][grow,fill,sg chart]"));
    private final JPanel occurrencesSection =
            Sections.createFilledSection("Occurrences over time", occurrencesChart);
    private final JPanel rateSection =
            Sections.createFilledSection("% of rows compared - run sizes differ", rateChart);

    private final PatternHistoryTableModel tableModel = new PatternHistoryTableModel();
    private final DashboardTable table = Tables.create(tableModel);

    private final PatternSightingTableModel sightingModel = new PatternSightingTableModel();
    private final DashboardTable sightingTable = Tables.create(sightingModel);
    private final JLabel sightingSummary = new JLabel();
    private final JPanel sightingPanel = new JPanel(
            new MigLayout("insets 6 4 0 4, fill, wrap 1", "[grow,fill]", "[]4[grow,fill]"));

    private final JTabbedPane lowerTabs = new JTabbedPane();

    private int patternId;
    private transient PatternService.PatternHistory shown;
    /** The run the cross-template list is for: the selected row's, the latest by default. */
    private int sightingRunId = -1;
    /** The rules file last checked, offered again as the starting point. */
    private File lastRulesFile;

    public PatternView(PatternService patterns, DashboardNavigator navigator) {
        super(new MigLayout("insets 0, fill, wrap 1", "[grow,fill]", "[]0[]6[]6[]6[grow,fill]"));
        this.patterns = patterns;
        this.navigator = navigator;

        add(buildToolBar());
        add(buildPatternSection(), "gapx 12 12, gaptop 10");
        add(buildKpiBar(), "gapx 12 12");
        charts.setOpaque(false);
        add(charts, "gapx 12 12");
        add(buildLowerTabs(), "grow, push, gapx 12 12, gapbottom 8");

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
        table.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            int viewRow = table.getSelectedRow();
            if (viewRow >= 0) {
                loadSightings(tableModel.rowAt(table.convertRowIndexToModel(viewRow)).runId());
            }
        });
        Tables.onRowActivated(sightingTable, row -> {
            PatternRow other = sightingModel.rowAt(row).pattern();
            if (other.patternId() != patternId) {
                navigator.showPatternHistory(other.patternId(), other.label());
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

    private JPanel buildPatternSection() {
        // Label, value, copy button, then the actions pushed to the right edge.
        JPanel section = Sections.createSection("Pattern", "insets 8 12 8 12, wrap 5, gapy 2",
                "[]12[]4[]push[]8[]");
        section.add(Sections.createFieldLabel("Signature"));
        section.add(signatureValue);
        section.add(copySignatureButton, "wrap");

        section.add(Sections.createFieldLabel("RCA rule"));
        ruleValue.setToolTipText("The name EasyRec gives the RCA rule it drafts from this pattern. "
                + "Only Groovy rules keep it; an Excel rule row carries no name.");
        section.add(ruleValue);
        section.add(copyRuleButton);
        section.add(checkRuleButton, "wrap");

        section.add(Sections.createFieldLabel("Continues"));
        section.add(linkValue);
        section.add(linkButton, "skip 1");
        section.add(unlinkButton);

        section.add(Sections.createHint("Explaining a pattern means creating an RCA rule from it in "
                + "EasyRec: the rule then comments every break of the pattern, on this run and the next. "
                + "A count of zero means not detected - below the engine's support floor its breaks "
                + "move to the column's \"No rule found\" pattern."), "span 5, gaptop 4");
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

    private JTabbedPane buildLowerTabs() {
        sightingSummary.setForeground(Palette.muted());
        sightingPanel.add(sightingSummary);
        sightingPanel.add(Tables.viewer(sightingTable), "grow, push");

        // A modest preferred height: the tabs take whatever is left once the charts have theirs,
        // rather than squeezing them to make room for every row of the table.
        lowerTabs.setPreferredSize(new java.awt.Dimension(400, 220));
        lowerTabs.addTab("Runs", DashboardIcons.ICON_CLOCK, Tables.viewer(table),
                "Every run that looked for the pattern - double-click to open the reconciliation");
        lowerTabs.addTab("Other templates", DashboardIcons.ICON_PATTERN, sightingPanel,
                "The templates the same signature was found on");
        return lowerTabs;
    }

    private void applyRenderers() {
        Tables.renderer(table, "Batch", Renderers.identifier());
        Tables.renderer(table, "Run", Renderers.identifier());
        Tables.renderer(table, "Pattern", Renderers.identifier());
        Tables.renderer(table, "Trend", Renderers.trend());
        Tables.renderer(table, "Occurrences", Renderers.count());
        // Fewer occurrences is the improvement, and a count of breaks is a whole number.
        Tables.renderer(table, "Change", Renderers.delta("", false, 0));
        Tables.renderer(table, "% of Rows", Renderers.numeric(2, null));
        Tables.renderer(table, "Rows Compared", Renderers.count());

        Tables.renderer(sightingTable, "Template", Renderers.identifier());
        Tables.renderer(sightingTable, "Pattern", Renderers.identifier());
        Tables.renderer(sightingTable, "Occurrences", Renderers.count());
        Tables.renderer(sightingTable, "First Seen Run", Renderers.identifier());
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
            showPatternInfo(null, null);
            occurrencesChart.setPoints(List.of(), "", null);
            showCharts(false);
            tableModel.setRows(List.of());
            Tables.refresh(table);
            showSightings(-1, List.of());
            return;
        }

        PatternRow pattern = history.pattern();
        title.setText(pattern.label());
        title.setToolTipText(pattern.label());
        subtitle.setText((history.template() == null || history.template().fullPath() == null
                ? "Template " + pattern.templateId() : history.template().fullPath())
                + "  \u2022  template " + pattern.templateId()
                + "  \u2022  pattern " + pattern.patternId());

        showPatternInfo(pattern, history.linkedChain());
        applyKpis(history);
        applyCharts(history);

        tableModel.setRows(history.newestFirst());
        Tables.refresh(table);
        lowerTabs.setTitleAt(0, history.points().size() + " runs");

        PatternService.PatternPoint latest = history.latest();
        sightingRunId = -1;
        loadSightings(latest == null ? -1 : latest.runId());
    }

    private void showPatternInfo(PatternRow pattern, List<PatternRow> chain) {
        boolean present = pattern != null;
        signatureValue.setText(present ? pattern.signature() : "-");
        copySignatureButton.setEnabled(present);

        String rule = present ? PatternKind.of(pattern).expectedRuleName(pattern.signature()) : null;
        if (rule != null) {
            ruleValue.setText(rule);
            ruleValue.setForeground(Palette.text());
        } else {
            ruleValue.setText(present ? noRuleReason(pattern) : "-");
            ruleValue.setForeground(Palette.muted());
        }
        copyRuleButton.setEnabled(rule != null);
        checkRuleButton.setEnabled(rule != null);

        if (!present || chain.isEmpty()) {
            linkValue.setText(present ? "No earlier pattern linked" : "-");
            linkValue.setForeground(Palette.muted());
        } else {
            PatternRow linked = chain.get(0);
            linkValue.setText("Pattern " + linked.patternId() + " (template " + linked.templateId()
                    + "): " + linked.label()
                    + (chain.size() > 1 ? "  \u2022  and " + (chain.size() - 1) + " before it" : ""));
            linkValue.setForeground(Palette.text());
        }
        linkButton.setEnabled(present);
        unlinkButton.setEnabled(present && pattern.linkedPatternId() != null);
    }

    /** Why a pattern has no rule name, in the words of {@code PatternRcaConverter}. */
    private static String noRuleReason(PatternRow pattern) {
        PatternKind kind = PatternKind.of(pattern);
        if (!kind.isKnown()) {
            return "Type not recognised from the description";
        }
        if (kind.hasRcaRule()) {
            return "None - the signature is too short to name a rule from";
        }
        if (PatternKind.isUnexplained(pattern)) {
            return "None - no rule was found for these breaks, so there is no condition to write";
        }
        if (kind.column() == null) {
            return "None - row level patterns are not converted to rules";
        }
        return "None for a pattern of type \"" + kind.typeLabel() + "\"";
    }

    private void applyKpis(PatternService.PatternHistory history) {
        PatternService.PatternPoint latest = history.latest();
        if (latest == null) {
            latestCard.setValue("-", Palette.muted());
            latestCard.setDetail("never counted");
        } else {
            long occurrences = latest.line().occurrences();
            latestCard.setValue(occurrences == 0L ? "Not detected"
                            : String.format(Locale.ROOT, "%,d", occurrences),
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
        detectedCard.setDetail(history.linkedChain().isEmpty() ? "runs that looked for it"
                : "runs, linked patterns included");

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
        firstSeenCard.setDetail(firstRun == null ? " " : "FIRST_SEEN_RUN " + firstRun);
    }

    /**
     * The counts, and beside them the rate when the runs compared different numbers of rows:
     * a count that doubles on a run twice the size has not moved, and only the rate says so.
     */
    private void applyCharts(PatternService.PatternHistory history) {
        List<TrendChart.Point> counts = new ArrayList<>();
        List<TrendChart.Point> rates = new ArrayList<>();
        for (PatternService.PatternPoint point : history.points()) {
            PatternService.PatternLine line = point.line();
            String tooltip = point.longLabel() + " \u2022 run " + point.runId()
                    + (line.occurrences() == 0L ? " \u2022 not detected"
                            : String.format(Locale.ROOT, " \u2022 %,d occurrences", line.occurrences()))
                    + (line.shareOfRows() == null ? ""
                            : String.format(Locale.ROOT, " \u2022 %.2f%% of rows", line.shareOfRows()))
                    + " \u2022 " + line.trend().label()
                    + (point.countedAs().patternId() != history.pattern().patternId()
                            ? " \u2022 as pattern " + point.countedAs().patternId() : "")
                    + (line.configChanged() ? " \u2022 configuration changed" : "");
            counts.add(new TrendChart.Point(point.shortLabel(), (double) line.occurrences(),
                    tooltip, line.trend().needsAttention()));
            rates.add(new TrendChart.Point(point.shortLabel(), line.shareOfRows(),
                    tooltip, line.trend().needsAttention()));
        }
        occurrencesChart.setPoints(counts, "", null);
        rateChart.setPoints(rates, "%", null);
        showCharts(history.runSizesDiffer());
    }

    private void showCharts(boolean withRate) {
        charts.removeAll();
        charts.add(occurrencesSection, withRate ? "" : "span 2");
        if (withRate) {
            charts.add(rateSection);
        }
        charts.revalidate();
        charts.repaint();
    }

    // ----------------------------------------------------------------- cross-template view

    private void loadSightings(int runId) {
        PatternService.PatternHistory history = shown;
        if (history == null || runId < 0) {
            showSightings(-1, List.of());
            return;
        }
        if (runId == sightingRunId) {
            return;
        }
        sightingRunId = runId;
        DashboardTask.run(this, "The other templates of pattern " + history.pattern().patternId(),
                () -> patterns.sightings(history.pattern(), runId),
                found -> {
                    // A later selection may have overtaken this one.
                    if (runId == sightingRunId && history == shown) {
                        showSightings(runId, found);
                    }
                });
    }

    private void showSightings(int runId, List<PatternSighting> found) {
        sightingModel.setRows(found);
        Tables.refresh(sightingTable);
        long templates = found.stream().map(sighting -> sighting.pattern().templateId()).distinct().count();
        long counted = found.stream().filter(sighting -> sighting.occurrences() != null
                && sighting.occurrences() > 0L).count();
        sightingSummary.setText(runId < 0 ? " "
                : templates <= 1 ? "Found on this template only - selected run " + runId
                : String.format(Locale.ROOT, "Found on %d templates, detected on %d of them on run %d"
                        + " - select a run above to change it, double-click to open a template's pattern",
                        templates, counted, runId));
        lowerTabs.setTitleAt(1, templates <= 1 ? "Other templates" : "Other templates (" + (templates - 1) + ")");
    }

    // -------------------------------------------------------------------------------- links

    private void chooseLink() {
        PatternService.PatternHistory history = shown;
        if (history == null) {
            return;
        }
        int target = history.pattern().patternId();
        DashboardTask.run(this, "The patterns pattern " + target + " may be linked to",
                () -> patterns.linkCandidates(target),
                candidates -> {
                    if (candidates.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "No other pattern on this template, or on "
                                        + "a template with the same path, can be linked to pattern "
                                        + target + ".", "EasyRec Dashboard",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    JComboBox<PatternRow> choice = new JComboBox<>(candidates.toArray(new PatternRow[0]));
                    choice.setRenderer(new CandidateRenderer());
                    Integer current = history.pattern().linkedPatternId();
                    for (PatternRow candidate : candidates) {
                        if (current != null && candidate.patternId() == current) {
                            choice.setSelectedItem(candidate);
                        }
                    }
                    Object[] message = {
                        "Pattern " + target + " continues the earlier pattern:", choice,
                        Sections.createHint("Link two patterns only when the cause kept its meaning "
                                + "but its signature changed. The history then runs on through the link.")
                    };
                    int answer = JOptionPane.showOptionDialog(this, message, "Link pattern " + target,
                            JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                            new Object[] {"Link", "Cancel"}, "Cancel");
                    if (answer == 0 && choice.getSelectedItem() instanceof PatternRow chosen) {
                        saveLink(target, chosen.patternId());
                    }
                });
    }

    private void clearLink() {
        PatternService.PatternHistory history = shown;
        if (history == null || history.pattern().linkedPatternId() == null) {
            return;
        }
        int target = history.pattern().patternId();
        int answer = JOptionPane.showOptionDialog(this,
                "Remove the link from pattern " + target + " to pattern "
                        + history.pattern().linkedPatternId() + "?\nIts history will stop at its own first run.",
                "Clear link", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null,
                new Object[] {"Clear link", "Cancel"}, "Cancel");
        if (answer == 0) {
            saveLink(target, null);
        }
    }

    /** Writes the link off the EDT, then reloads; a refused link is said why. */
    private void saveLink(int target, Integer linkedId) {
        DashboardTask.run(this, "The link of pattern " + target, () -> {
            try {
                return patterns.link(target, linkedId) > 0 ? null
                        : "Pattern " + target + " was not found, so nothing was saved.";
            } catch (IllegalArgumentException refused) {
                return refused.getMessage();
            }
        }, problem -> {
            if (problem != null) {
                JOptionPane.showMessageDialog(this, problem, "EasyRec Dashboard",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                load(target);
            }
        });
    }

    /** One candidate in the link dialog: its id, where it was first seen, and its text. */
    private static final class CandidateRenderer extends DefaultListCellRenderer {

        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            Object shownValue = value;
            if (value instanceof PatternRow row) {
                String label = row.label();
                shownValue = "#" + row.patternId() + "  \u2022  template " + row.templateId()
                        + "  \u2022  first seen run " + row.firstSeenRun() + "  \u2022  "
                        + (label.length() > 90 ? label.substring(0, 87) + "..." : label);
            }
            return super.getListCellRendererComponent(list, shownValue, index, isSelected, cellHasFocus);
        }
    }

    // ---------------------------------------------------------------------------- RCA rule

    private String shownSignature() {
        return shown == null ? null : shown.pattern().signature();
    }

    private String shownRuleName() {
        return shown == null ? null
                : PatternKind.of(shown.pattern()).expectedRuleName(shown.pattern().signature());
    }

    private static void copy(String text) {
        if (text != null) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    /**
     * Asks for a project's XML rules file and says whether a Groovy rule with the expected
     * name is in it. An Excel rule carries no name, so "not found" never means "not explained".
     */
    private void checkRulesFile() {
        String rule = shownRuleName();
        if (rule == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(lastRulesFile);
        chooser.setDialogTitle("Rules file to look for " + rule + " in");
        chooser.setFileFilter(new FileNameExtensionFilter("XML rules files", "xml"));
        if (lastRulesFile != null) {
            chooser.setSelectedFile(lastRulesFile);
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        lastRulesFile = file;
        DashboardTask.run(this, "The rules file " + file.getName(),
                () -> containsRule(file, rule),
                found -> JOptionPane.showMessageDialog(this, found
                                ? "The Groovy rule " + rule + " is in\n" + file.getPath()
                                : "No Groovy rule named " + rule + " in\n" + file.getPath()
                                        + "\n\nAn Excel rule may still explain this pattern: Excel rule "
                                        + "rows carry no name, so they cannot be found this way.",
                        "RCA rule", found ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE));
    }

    /** True when the file has a {@code <rule name="...">} element with that name. */
    private static boolean containsRule(File file, String ruleName) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // A rules file is a user's file, not a trusted one: no external entities.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            NodeList rules = factory.newDocumentBuilder().parse(file).getElementsByTagName("rule");
            for (int index = 0; index < rules.getLength(); index++) {
                if (ruleName.equals(((Element) rules.item(index)).getAttribute("name"))) {
                    return true;
                }
            }
            return false;
        } catch (Exception failure) {
            throw new IllegalStateException("Could not read " + file.getPath() + " as an XML rules file",
                    failure);
        }
    }
}
