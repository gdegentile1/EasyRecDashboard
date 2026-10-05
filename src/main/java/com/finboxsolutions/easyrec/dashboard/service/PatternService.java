package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao.DashboardSchemaMissingException;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternKind;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternSighting;
import com.finboxsolutions.easyrec.dashboard.model.PatternStat;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The patterns the engine exported for a reconciliation, and how each moved over time.
 *
 * <p>ER_DASHBOARD_PATTERN_STAT holds raw counts only: one row per known pattern per run, a
 * zero for a pattern looked for and not found. The trend - new, increasing, not detected,
 * reappeared - is derived here, by walking each pattern's rows in RUN_ID order. "Previous"
 * therefore means the previous run that counted this pattern, so a run the export skipped
 * (patterns switched off on the template, or its diff truncated) is stepped over rather than
 * read as a drop to zero.
 *
 * <p>The pattern tables come with a later engine than the rest of the dashboard schema. On a
 * datasource that does not have them yet every read here answers "no patterns" instead of
 * failing, so the screens that show patterns keep working without them.
 */
public class PatternService {

    /**
     * Relative movement under which a count is reported STABLE: 10% of the previous count.
     *
     * <p>Without one, a pattern going from 1 000 breaks to 1 003 would be flagged as
     * increasing, and the column meant to draw the eye would flag almost everything.
     */
    public static final double DEFAULT_STABLE_TOLERANCE = 0.10d;

    /** A system property overriding {@link #DEFAULT_STABLE_TOLERANCE}, e.g. {@code 0.2}. */
    public static final String STABLE_TOLERANCE_PROPERTY = "easyrec.dashboard.patterns.stableTolerance";

    /** How many links {@link #patternHistory} follows at most, a guard against bad data. */
    private static final int MAX_LINKS = 50;

    private static final DateTimeFormatter LONG_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    /**
     * One pattern on one run, with its movement since the previous run that counted it.
     *
     * @param previous           the count on that previous run, or null on the first one
     * @param change             {@code occurrences - previous}, or null on the first run
     * @param shareOfRows        occurrences as a percentage of the rows compared on the run -
     *                           the denominator of the KPI tiles - or null when the run has no
     *                           row statistics on the template
     * @param configChanged      TEMPLATE_CFG_HASH differs from the previous run's, so the
     *                           movement may come from a configuration change rather than
     *                           from the data
     * @param unexplainedOnColumn for a pattern not detected on the run, the breaks the
     *                           unexplained pattern of its column counts on the same run - where
     *                           its own breaks went if it fell below the support floor. Null
     *                           otherwise, or when the column has no unexplained pattern
     */
    public record PatternLine(
            PatternStat stat,
            PatternTrend trend,
            Long previous,
            Long change,
            Double shareOfRows,
            boolean configChanged,
            Long unexplainedOnColumn) {

        public PatternRow pattern() {
            return stat.pattern();
        }

        public long occurrences() {
            return stat.occurrences();
        }

        public int runId() {
            return stat.runId();
        }

        PatternLine withUnexplainedOnColumn(Long unexplained) {
            return new PatternLine(stat, trend, previous, change, shareOfRows, configChanged,
                    unexplained);
        }
    }

    /**
     * The patterns of one reconciliation.
     *
     * @param tracked    false when the template has no pattern history at all - pattern export
     *                   off, or a datasource without the pattern tables
     * @param lines      the patterns counted on this run, most occurrences first; empty for
     *                   a tracked template whose export skipped this run
     * @param knownCount how many patterns the template has, over all its runs
     */
    public record RunPatterns(boolean tracked, List<PatternLine> lines, int knownCount) {

        public static final RunPatterns NONE = new RunPatterns(false, List.of(), 0);

        /**
         * True when the export ran on this run. Every known pattern gets a row on a run the
         * export covered, so a run with no row at all was skipped - which is not zero.
         */
        public boolean exported() {
            return !lines.isEmpty();
        }

        public long count(PatternTrend trend) {
            return lines.stream().filter(line -> line.trend() == trend).count();
        }

        /** Patterns detected on this run: those with at least one occurrence. */
        public long presentCount() {
            return lines.stream().filter(line -> line.trend().isPresent()).count();
        }

        /** Breaks left without a rule on this run: the unexplained patterns' total. */
        public long unexplained() {
            long total = 0L;
            for (PatternLine line : lines) {
                if (PatternKind.isUnexplained(line.pattern())) {
                    total += line.occurrences();
                }
            }
            return total;
        }
    }

    /** One point on a pattern's trend: a run, its count and how it moved. */
    public record PatternPoint(
            PatternLine line,
            BatchRow batch,
            Integer contextTemplateId,
            LocalDateTime when,
            String longLabel,
            String shortLabel) {

        public int runId() {
            return line.runId();
        }

        /** The pattern this point was counted under: the one shown, or one it continues. */
        public PatternRow countedAs() {
            return line.pattern();
        }
    }

    /**
     * One pattern's whole history, oldest run first.
     *
     * @param linkedChain    the patterns it continues, following LINKED_PATTERN_ID: the one
     *                       it links to first. Their runs before this pattern's first one are
     *                       part of {@code points}, so the curve goes on across a template
     *                       change or a signature that moved
     * @param runSizesDiffer the runs on the curve compared different numbers of rows, so the
     *                       rate is the figure to compare, not the raw count
     */
    public record PatternHistory(
            PatternRow pattern,
            TemplateRow template,
            List<PatternRow> linkedChain,
            List<PatternPoint> points,
            boolean runSizesDiffer) {

        public PatternRow linkedPattern() {
            return linkedChain.isEmpty() ? null : linkedChain.get(0);
        }

        public PatternPoint latest() {
            return points.isEmpty() ? null : points.get(points.size() - 1);
        }

        /** The run with the most occurrences, the latest of them on a tie. */
        public PatternPoint peak() {
            PatternPoint peak = null;
            for (PatternPoint point : points) {
                if (peak == null || point.line().occurrences() >= peak.line().occurrences()) {
                    peak = point;
                }
            }
            return peak;
        }

        /** How many of the runs on the trend detected the pattern. */
        public int detectedRuns() {
            int detected = 0;
            for (PatternPoint point : points) {
                if (point.line().occurrences() > 0) {
                    detected++;
                }
            }
            return detected;
        }

        /** Newest first, which is the order the table under the chart reads in. */
        public List<PatternPoint> newestFirst() {
            List<PatternPoint> reversed = new ArrayList<>(points);
            java.util.Collections.reverse(reversed);
            return reversed;
        }
    }

    private final DashboardDao dao;
    private final DashboardService dashboard;
    private final double stableTolerance;

    /** With the tolerance of {@link #STABLE_TOLERANCE_PROPERTY}, or the default. */
    public PatternService(DashboardDao dao, DashboardService dashboard) {
        this(dao, dashboard, configuredTolerance());
    }

    public PatternService(DashboardDao dao, DashboardService dashboard, double stableTolerance) {
        this.dao = dao;
        this.dashboard = dashboard;
        this.stableTolerance = stableTolerance;
    }

    /** {@link #STABLE_TOLERANCE_PROPERTY} when set to a non-negative number, else the default. */
    public static double configuredTolerance() {
        String raw = System.getProperty(STABLE_TOLERANCE_PROPERTY);
        if (raw != null && !raw.isBlank()) {
            try {
                double value = Double.parseDouble(raw.trim());
                if (value >= 0.0d) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to the default: a typo in a setting is not worth a failed screen.
            }
        }
        return DEFAULT_STABLE_TOLERANCE;
    }

    /**
     * The patterns of one reconciliation, each with its trend up to this run.
     *
     * @param statsTemplateId the reconciliation template's id, as ER_DASHBOARD_STAT_ROWS and
     *                        the context row carry it - never ER_DASHBOARD_RUN's
     */
    public RunPatterns patternsOf(int runId, Integer statsTemplateId) {
        if (statsTemplateId == null) {
            return RunPatterns.NONE;
        }
        List<PatternStat> history = historyOf(statsTemplateId);
        if (history.isEmpty()) {
            return RunPatterns.NONE;
        }

        Map<Integer, List<PatternStat>> byPattern = groupByPattern(history);
        List<PatternLine> lines = new ArrayList<>();
        for (List<PatternStat> rows : byPattern.values()) {
            for (PatternLine line : walk(rows)) {
                if (line.runId() == runId) {
                    lines.add(line);
                }
            }
        }
        lines = withUnexplainedOnColumn(lines);
        lines.sort(Comparator.comparingLong(PatternLine::occurrences).reversed()
                .thenComparing(line -> line.pattern().label(), String.CASE_INSENSITIVE_ORDER));
        return new RunPatterns(true, lines, byPattern.size());
    }

    /**
     * One pattern over every run that counted it, oldest first, followed back through
     * LINKED_PATTERN_ID; or null when the pattern does not exist (or the pattern tables do
     * not).
     */
    public PatternHistory patternHistory(int patternId) {
        PatternRow pattern;
        List<PatternRow> chain;
        try {
            pattern = dao.findPattern(patternId);
            if (pattern == null) {
                return null;
            }
            chain = linkedChainOf(pattern);
        } catch (DashboardSchemaMissingException absent) {
            return null;
        }
        TemplateRow template = dao.findTemplate(pattern.templateId());

        // Each pattern's own rows, then the earlier patterns' rows from before it started:
        // after a link, both may still be counted on the same runs - the old one at zero -
        // and the newer one is the one that speaks for those runs.
        Map<Integer, List<PatternStat>> historyByTemplate = new HashMap<>();
        List<PatternStat> rows = new ArrayList<>();
        int cutoff = Integer.MAX_VALUE;
        List<PatternRow> segments = new ArrayList<>();
        segments.add(pattern);
        segments.addAll(chain);
        for (PatternRow segment : segments) {
            List<PatternStat> templateRows = historyByTemplate.computeIfAbsent(
                    segment.templateId(), this::historyOf);
            int earliest = cutoff;
            for (PatternStat row : templateRows) {
                if (row.patternId() == segment.patternId() && row.runId() < cutoff) {
                    rows.add(row);
                    earliest = Math.min(earliest, row.runId());
                }
            }
            cutoff = earliest;
        }
        rows.sort(Comparator.comparingInt(PatternStat::runId));
        if (rows.isEmpty()) {
            return new PatternHistory(pattern, template, chain, List.of(), false);
        }

        Set<Integer> runIds = new LinkedHashSet<>();
        Set<Long> runSizes = new HashSet<>();
        for (PatternStat row : rows) {
            runIds.add(row.runId());
            if (row.rowsCompared() != null) {
                runSizes.add(row.rowsCompared());
            }
        }

        // The context TEMPLATE_ID of each run, so a point can open its reconciliation: that
        // screen is addressed by the context id. Normally the same id as the pattern's, but
        // findReconciliations is what pairs them on older databases too.
        Map<Integer, Integer> contextTemplateByRun = new LinkedHashMap<>();
        Map<Integer, Integer> templateByRun = new HashMap<>();
        for (PatternStat row : rows) {
            templateByRun.put(row.runId(), row.pattern().templateId());
        }
        for (DashboardService.Reconciliation rec : dashboard.findReconciliations(runIds, null)) {
            if (Objects.equals(rec.statsTemplateId(), templateByRun.get(rec.runId()))) {
                contextTemplateByRun.putIfAbsent(rec.runId(), rec.templateId());
            }
        }

        Map<Integer, RunRow> runs = dao.findRuns(runIds);
        Set<Integer> batchIds = new LinkedHashSet<>();
        for (RunRow run : runs.values()) {
            batchIds.add(run.batchId());
        }
        Map<Integer, BatchRow> batches = new LinkedHashMap<>();
        for (BatchRow batch : dao.findAllBatches()) {
            if (batchIds.contains(batch.batchId())) {
                batches.put(batch.batchId(), batch);
            }
        }

        List<PatternPoint> points = new ArrayList<>(rows.size());
        for (PatternLine line : walk(rows)) {
            RunRow run = runs.get(line.runId());
            BatchRow batch = run == null ? null : batches.get(run.batchId());
            LocalDateTime when = batch == null ? null : dashboard.timestampOf(batch);
            points.add(new PatternPoint(line, batch, contextTemplateByRun.get(line.runId()), when,
                    when == null ? "Run " + line.runId() : LONG_LABEL.format(when),
                    when == null ? "#" + line.runId() : SHORT_LABEL.format(when)));
        }
        return new PatternHistory(pattern, template, chain, points, runSizes.size() > 1);
    }

    /**
     * Every template the pattern's SIGNATURE was found on, with its count on {@code runId} -
     * the same cause on several reconciliations, to be fixed once.
     */
    public List<PatternSighting> sightings(PatternRow pattern, int runId) {
        if (pattern == null || pattern.signature() == null) {
            return List.of();
        }
        try {
            return dao.findPatternsBySignature(pattern.signature(), runId);
        } catch (DashboardSchemaMissingException absent) {
            return List.of();
        }
    }

    /**
     * The patterns {@code patternId} may be linked to: those of its own template and of every
     * template sharing its FULL_PATH - where a template change restarts the history - minus
     * itself and any pattern that already continues it, which a link would close into a cycle.
     * Earliest first seen first.
     */
    public List<PatternRow> linkCandidates(int patternId) {
        PatternRow pattern = dao.findPattern(patternId);
        if (pattern == null) {
            return List.of();
        }
        Set<Integer> templateIds = new LinkedHashSet<>();
        templateIds.add(pattern.templateId());
        TemplateRow template = dao.findTemplate(pattern.templateId());
        if (template != null && template.fullPath() != null) {
            templateIds.addAll(dao.findTemplateIdsByPaths(List.of(template.fullPath())).keySet());
        }
        List<PatternRow> all = dao.findPatternsByTemplates(templateIds);
        Map<Integer, PatternRow> byId = new HashMap<>();
        for (PatternRow row : all) {
            byId.put(row.patternId(), row);
        }
        List<PatternRow> candidates = new ArrayList<>();
        for (PatternRow candidate : all) {
            if (candidate.patternId() != patternId && !reaches(candidate, patternId, byId)) {
                candidates.add(candidate);
            }
        }
        candidates.sort(Comparator.comparing(PatternRow::firstSeenRun,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(PatternRow::patternId));
        return candidates;
    }

    /**
     * Links {@code patternId} to the earlier pattern it continues, or clears its link with
     * null. Only ever on a user's request: the dashboard never links on its own.
     *
     * @return the number of rows changed
     * @throws IllegalArgumentException for a link to itself, to a pattern that does not exist,
     *                                  or one that would close a cycle
     */
    public int link(int patternId, Integer linkedPatternId) {
        if (linkedPatternId != null) {
            if (linkedPatternId == patternId) {
                throw new IllegalArgumentException("A pattern cannot be linked to itself.");
            }
            PatternRow target = dao.findPattern(linkedPatternId);
            if (target == null) {
                throw new IllegalArgumentException("Pattern " + linkedPatternId + " does not exist.");
            }
            Set<Integer> seen = new HashSet<>();
            for (PatternRow step = target; step != null && step.linkedPatternId() != null; ) {
                if (step.linkedPatternId() == patternId) {
                    throw new IllegalArgumentException("Pattern " + linkedPatternId
                            + " already continues pattern " + patternId
                            + ", so this link would close a cycle.");
                }
                if (!seen.add(step.patternId())) {
                    break;  // a cycle already in the data, not through this pattern
                }
                step = dao.findPattern(step.linkedPatternId());
            }
        }
        return dao.updatePatternLink(patternId, linkedPatternId);
    }

    /**
     * Breaks left without a rule, per run, on one template: the total of its unexplained
     * patterns. A run absent from the map has no pattern data at all - its export was
     * skipped - which is not the same as zero.
     *
     * @param contextTemplateId the template as the history screens address it
     * @param runIds            the runs to report
     */
    public Map<Integer, Long> unexplainedByRun(int contextTemplateId, Collection<Integer> runIds) {
        Map<Integer, Long> totals = new LinkedHashMap<>();
        if (runIds.isEmpty()) {
            return totals;
        }
        Set<Integer> statsTemplateIds = new LinkedHashSet<>();
        for (DashboardService.Reconciliation rec : dashboard.findReconciliations(runIds, null)) {
            if (rec.templateId() != null && rec.templateId() == contextTemplateId
                    && rec.statsTemplateId() != null) {
                statsTemplateIds.add(rec.statsTemplateId());
            }
        }
        Set<Integer> wanted = new HashSet<>(runIds);
        for (Integer statsTemplateId : statsTemplateIds) {
            for (PatternStat row : historyOf(statsTemplateId)) {
                if (!wanted.contains(row.runId())) {
                    continue;
                }
                long unexplained = PatternKind.isUnexplained(row.pattern()) ? row.occurrences() : 0L;
                totals.merge(row.runId(), unexplained, Long::sum);
            }
        }
        return totals;
    }

    /**
     * How a count moved since the previous run that counted the pattern, on counts alone.
     *
     * @param previous   that run's count, or null when this is the pattern's first row
     * @param seenBefore whether any earlier row had an occurrence, which separates a pattern
     *                   coming back from one appearing for the first time
     */
    public PatternTrend trendOf(long occurrences, Long previous, boolean seenBefore) {
        return trendOf(occurrences, previous, seenBefore, null, null);
    }

    /**
     * How a count moved since the previous run that counted the pattern.
     *
     * <p>When both runs have a rate - their sizes are known - and the sizes differ, the move
     * is judged on the rate: a pattern holding at 5% of a run that doubled has not increased.
     *
     * @param rate         this run's occurrences as a percentage of its rows, or null
     * @param previousRate the previous run's, or null
     */
    public PatternTrend trendOf(long occurrences, Long previous, boolean seenBefore,
                                Double rate, Double previousRate) {
        if (occurrences == 0L) {
            return previous != null && previous > 0L
                    ? PatternTrend.NOT_DETECTED : PatternTrend.STILL_NOT_DETECTED;
        }
        if (!seenBefore) {
            return PatternTrend.NEW;
        }
        if (previous == null || previous == 0L) {
            return PatternTrend.REAPPEARED;
        }
        double current = occurrences;
        double before = previous;
        if (rate != null && previousRate != null && previousRate > 0.0d) {
            current = rate;
            before = previousRate;
        }
        double change = current - before;
        if (Math.abs(change) <= before * stableTolerance) {
            return PatternTrend.STABLE;
        }
        return change > 0 ? PatternTrend.INCREASING : PatternTrend.DECREASING;
    }

    // ------------------------------------------------------------------------ internals

    private List<PatternStat> historyOf(int templateId) {
        try {
            return dao.findPatternHistory(templateId);
        } catch (DashboardSchemaMissingException absent) {
            // An engine older than the pattern export: the other tables are there, these are
            // not. Not an error for any screen that merely offers patterns alongside the rest.
            return List.of();
        }
    }

    /** The patterns {@code pattern} continues, nearest first, stopping at a cycle. */
    private List<PatternRow> linkedChainOf(PatternRow pattern) {
        List<PatternRow> chain = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        seen.add(pattern.patternId());
        Integer next = pattern.linkedPatternId();
        while (next != null && seen.add(next) && chain.size() < MAX_LINKS) {
            PatternRow linked = dao.findPattern(next);
            if (linked == null) {
                break;
            }
            chain.add(linked);
            next = linked.linkedPatternId();
        }
        return chain;
    }

    /** True when following links from {@code start} arrives at {@code patternId}. */
    private static boolean reaches(PatternRow start, int patternId, Map<Integer, PatternRow> byId) {
        Set<Integer> seen = new HashSet<>();
        Integer next = start.linkedPatternId();
        while (next != null && seen.add(next)) {
            if (next == patternId) {
                return true;
            }
            PatternRow linked = byId.get(next);
            next = linked == null ? null : linked.linkedPatternId();
        }
        return false;
    }

    private static Map<Integer, List<PatternStat>> groupByPattern(List<PatternStat> history) {
        Map<Integer, List<PatternStat>> byPattern = new LinkedHashMap<>();
        for (PatternStat row : history) {
            byPattern.computeIfAbsent(row.patternId(), key -> new ArrayList<>()).add(row);
        }
        for (List<PatternStat> rows : byPattern.values()) {
            rows.sort(Comparator.comparingInt(PatternStat::runId));
        }
        return byPattern;
    }

    /**
     * For each line not detected on the run, the count of its column's unexplained pattern on
     * the same run: below the support floor a rule's breaks fall there, so a zero is shown
     * next to where they may have gone rather than as a fix.
     */
    private static List<PatternLine> withUnexplainedOnColumn(List<PatternLine> lines) {
        Map<String, Long> unexplainedByColumn = new HashMap<>();
        for (PatternLine line : lines) {
            PatternKind kind = PatternKind.of(line.pattern());
            if (PatternKind.isUnexplained(line.pattern()) && kind.column() != null) {
                unexplainedByColumn.merge(kind.column(), line.occurrences(), Long::sum);
            }
        }
        List<PatternLine> result = new ArrayList<>(lines.size());
        for (PatternLine line : lines) {
            String column = PatternKind.of(line.pattern()).column();
            boolean show = !line.trend().isPresent() && column != null
                    && !PatternKind.isUnexplained(line.pattern());
            result.add(show ? line.withUnexplainedOnColumn(unexplainedByColumn.get(column)) : line);
        }
        return result;
    }

    /**
     * Rows in RUN_ID order turned into lines with their movement.
     *
     * <p>The previous count is the previous row's, worked out here rather than taken from a
     * LAG in SQL: a pattern's history may run on through the pattern it is linked to, and the
     * step across that link is not one a window over PATTERN_ID can see.
     */
    private List<PatternLine> walk(List<PatternStat> rows) {
        List<PatternLine> lines = new ArrayList<>(rows.size());
        Long previous = null;
        Double previousRate = null;
        String previousHash = null;
        boolean seenBefore = false;
        for (PatternStat row : rows) {
            long occurrences = row.occurrences();
            Double rate = row.rate();
            boolean configChanged = previous != null && row.templateCfgHash() != null
                    && previousHash != null && !row.templateCfgHash().equals(previousHash);
            lines.add(new PatternLine(row,
                    trendOf(occurrences, previous, seenBefore, rate, previousRate),
                    previous, previous == null ? null : occurrences - previous,
                    rate, configChanged, null));
            previous = occurrences;
            previousRate = rate;
            previousHash = row.templateCfgHash();
            seenBefore |= occurrences > 0L;
        }
        return lines;
    }
}
