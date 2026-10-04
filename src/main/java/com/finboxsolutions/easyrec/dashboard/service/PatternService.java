package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao.DashboardSchemaMissingException;
import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternStat;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
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
 * zero for a pattern looked for and not found. The trend - new, increasing, resolved,
 * reappeared - is derived here, by walking each pattern's rows in RUN_ID order. "Previous"
 * therefore means the previous run that counted this pattern, so a run the export skipped
 * (the template was not opted in, or its diff was truncated) is stepped over rather than
 * read as a drop to zero.
 *
 * <p>The pattern tables come with a later engine than the rest of the dashboard schema. On a
 * datasource that does not have them yet every method here answers "no patterns" instead of
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

    private static final DateTimeFormatter LONG_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter SHORT_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    /**
     * One pattern on one run, with its movement since the previous run that counted it.
     *
     * @param previous      the count on that previous run, or null on the first one
     * @param change        {@code occurrences - previous}, or null on the first run
     * @param shareOfRows   occurrences as a percentage of the run's rows - the denominator of
     *                      the KPI tiles - or null when the run has no row statistics
     * @param configChanged TEMPLATE_CFG_HASH differs from the previous run's, so the movement
     *                      may come from a configuration change rather than from the data
     */
    public record PatternLine(
            PatternStat stat,
            PatternTrend trend,
            Long previous,
            Long change,
            Double shareOfRows,
            boolean configChanged) {

        public PatternRow pattern() {
            return stat.pattern();
        }

        public long occurrences() {
            return stat.occurrences();
        }

        public int runId() {
            return stat.runId();
        }
    }

    /**
     * The patterns of one reconciliation.
     *
     * @param tracked    false when the template has no pattern history at all - not opted in,
     *                   or a datasource without the pattern tables
     * @param lines      the patterns counted on this run, most occurrences first; empty for
     *                   a tracked template whose export skipped this run
     * @param knownCount how many patterns the template has, over all its runs
     */
    public record RunPatterns(boolean tracked, List<PatternLine> lines, int knownCount) {

        public static final RunPatterns NONE = new RunPatterns(false, List.of(), 0);

        /** True when the export ran on this run, even if it found nothing. */
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
    }

    /** One pattern's whole history, oldest run first. */
    public record PatternHistory(
            PatternRow pattern,
            TemplateRow template,
            PatternRow linkedPattern,
            List<PatternPoint> points) {

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

    public PatternService(DashboardDao dao, DashboardService dashboard) {
        this(dao, dashboard, DEFAULT_STABLE_TOLERANCE);
    }

    public PatternService(DashboardDao dao, DashboardService dashboard, double stableTolerance) {
        this.dao = dao;
        this.dashboard = dashboard;
        this.stableTolerance = stableTolerance;
    }

    /**
     * The patterns of one reconciliation, each with its trend up to this run.
     *
     * @param statsTemplateId the TEMPLATE_ID the run's statistics are filed under, which is
     *                        the one the engine files patterns under too
     * @param stats           the reconciliation's row statistics, for the share of rows; may
     *                        be null
     */
    public RunPatterns patternsOf(int runId, Integer statsTemplateId, RowStats stats) {
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
            for (PatternLine line : walk(rows, run -> stats)) {
                if (line.runId() == runId) {
                    lines.add(line);
                }
            }
        }
        lines.sort(Comparator.comparingLong(PatternLine::occurrences).reversed()
                .thenComparing(line -> line.pattern().label(), String.CASE_INSENSITIVE_ORDER));
        return new RunPatterns(true, lines, byPattern.size());
    }

    /**
     * One pattern over every run that counted it, oldest first, or null when the pattern
     * does not exist (or the pattern tables do not).
     */
    public PatternHistory patternHistory(int patternId) {
        PatternRow pattern;
        List<PatternStat> rows;
        try {
            pattern = dao.findPattern(patternId);
            if (pattern == null) {
                return null;
            }
            rows = dao.findPatternStats(patternId);
        } catch (DashboardSchemaMissingException absent) {
            return null;
        }
        PatternRow linked = null;
        if (pattern.linkedPatternId() != null) {
            linked = dao.findPattern(pattern.linkedPatternId());
        }
        TemplateRow template = dao.findTemplate(pattern.templateId());
        if (rows.isEmpty()) {
            return new PatternHistory(pattern, template, linked, List.of());
        }

        Set<Integer> runIds = new LinkedHashSet<>();
        for (PatternStat row : rows) {
            runIds.add(row.runId());
        }

        // The rows of the run on the pattern's own template only: a run reconciles many
        // templates, and the share has to be of this one's rows.
        Map<Integer, RowStats> statsByRun = new LinkedHashMap<>();
        for (RowStats stats : dao.findRowStats(runIds)) {
            if (stats.templateId() != null && stats.templateId() == pattern.templateId()) {
                statsByRun.put(stats.runId(), stats);
            }
        }

        // The context TEMPLATE_ID of each run, so a point can open its reconciliation: that
        // screen is addressed by the context id, and patterns are filed under the statistics
        // one. findReconciliations already pairs the two.
        Map<Integer, Integer> contextTemplateByRun = new LinkedHashMap<>();
        for (DashboardService.Reconciliation rec : dashboard.findReconciliations(runIds, null)) {
            if (Objects.equals(rec.statsTemplateId(), pattern.templateId())) {
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
        for (PatternLine line : walk(rows, statsByRun::get)) {
            RunRow run = runs.get(line.runId());
            BatchRow batch = run == null ? null : batches.get(run.batchId());
            LocalDateTime when = batch == null ? null : dashboard.timestampOf(batch);
            points.add(new PatternPoint(line, batch, contextTemplateByRun.get(line.runId()), when,
                    when == null ? "Run " + line.runId() : LONG_LABEL.format(when),
                    when == null ? "#" + line.runId() : SHORT_LABEL.format(when)));
        }
        return new PatternHistory(pattern, template, linked, points);
    }

    /** Writes the operator qualification of a pattern. Returns the number of rows changed. */
    public int qualify(int patternId, String rootCause, String ownerName, String ticketRef) {
        return dao.updatePatternQualification(patternId, rootCause, ownerName, ticketRef);
    }

    /**
     * How a count moved since the previous run that counted the pattern.
     *
     * @param previous   that run's count, or null when this is the pattern's first row
     * @param seenBefore whether any earlier row had an occurrence, which separates a pattern
     *                   coming back from one appearing for the first time
     */
    public PatternTrend trendOf(long occurrences, Long previous, boolean seenBefore) {
        if (occurrences == 0L) {
            return previous != null && previous > 0L ? PatternTrend.RESOLVED : PatternTrend.ABSENT;
        }
        if (!seenBefore) {
            return PatternTrend.NEW;
        }
        if (previous == null || previous == 0L) {
            return PatternTrend.REAPPEARED;
        }
        long change = occurrences - previous;
        if (Math.abs(change) <= previous * stableTolerance) {
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

    /** One pattern's rows, already in RUN_ID order, turned into lines with their movement. */
    private List<PatternLine> walk(List<PatternStat> rows,
                                   java.util.function.IntFunction<RowStats> statsOfRun) {
        List<PatternLine> lines = new ArrayList<>(rows.size());
        Long previous = null;
        String previousHash = null;
        boolean seenBefore = false;
        for (PatternStat row : rows) {
            long occurrences = row.occurrences();
            RowStats stats = statsOfRun.apply(row.runId());
            boolean configChanged = previous != null && row.templateCfgHash() != null
                    && previousHash != null && !row.templateCfgHash().equals(previousHash);
            lines.add(new PatternLine(row, trendOf(occurrences, previous, seenBefore), previous,
                    previous == null ? null : occurrences - previous,
                    stats == null ? null : stats.shareOfRows(occurrences),
                    configChanged));
            previous = occurrences;
            previousHash = row.templateCfgHash();
            seenBefore |= occurrences > 0L;
        }
        return lines;
    }
}
