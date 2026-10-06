package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao.DashboardSchemaMissingException;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternStat;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The archived history a live campaign is compared with: for every pattern signature, how
 * many breaks the templates of the campaign counted on their last archived run.
 *
 * <p>The Global Patterns view analyses the run loaded in EasyRec, which is not in the
 * database, or not yet. Its trends therefore come in two halves: the baseline, read here
 * once for every template of the campaign, and the live count of each cause, which only
 * EasyRec has. {@link #trendOf} puts the two together.
 *
 * <p>The reference run is taken per template: the latest archived run of each one, before
 * {@code currentRunId} when the live run has itself been archived, since comparing a run
 * with itself would report everything stable. Templates are not all reconciled on every
 * run, so a single run id for the whole campaign would read a template the run skipped as
 * a drop to zero.
 *
 * <p>A cause is aggregated over every template that counted it on its reference run, not
 * only the templates it is still found on: a cause fixed on three templates out of five
 * has decreased, and summing the two remaining ones would hide it.
 *
 * <p>The trend rules and the stable tolerance are {@link PatternService}'s, so a cause is
 * never "Stable" here and "Increasing" on the template screen for the same movement. Rates
 * are not used: the templates of a cause compare different numbers of rows, and a summed
 * rate means nothing.
 *
 * <p>Reads the database: call {@link #baselineOf} from a SwingWorker, never on the EDT.
 * {@link #trendOf} and {@link #vanished} are pure and cheap.
 */
public class CampaignTrendService {

    /**
     * One signature on the reference runs of the campaign.
     *
     * @param previous      breaks counted on the reference runs, summed over templates
     * @param seenBefore    detected at least once up to the reference runs
     * @param firstSeenRun  the first run that detected it, null when none did
     * @param templatePaths the templates that counted it on their reference run
     */
    public record SignatureBaseline(
            String signature,
            String description,
            long previous,
            boolean seenBefore,
            Integer firstSeenRun,
            Set<String> templatePaths) {
    }

    /**
     * The baseline of a whole campaign.
     *
     * @param trackedPaths   templates with an archived run to compare with
     * @param untrackedPaths templates with none: unknown to the database, pattern export off,
     *                       or only archived by the live run itself
     * @param referenceRuns  the reference run of each tracked template
     */
    public record CampaignBaseline(
            Integer currentRunId,
            Set<String> trackedPaths,
            Set<String> untrackedPaths,
            Map<String, Integer> referenceRuns,
            Map<String, SignatureBaseline> bySignature) {

        /** No history at all: the trend columns have nothing to show. */
        public static final CampaignBaseline NONE = new CampaignBaseline(null, Set.of(), Set.of(),
                Map.of(), Map.of());

        public boolean available() {
            return !trackedPaths.isEmpty();
        }

        public boolean isTracked(String templatePath) {
            return templatePath != null && trackedPaths.contains(templatePath);
        }

        /** The signature's baseline, or null when no tracked template ever counted it. */
        public SignatureBaseline baselineOf(String signature) {
            return signature == null ? null : bySignature.get(signature);
        }
    }

    /**
     * How one cause moved since the reference runs.
     *
     * @param trend               null when none of the cause's templates is tracked
     * @param comparedOccurrences the live count on the tracked templates only, the figure the
     *                            trend is judged on
     * @param previous            the count on the reference runs, null for a cause never
     *                            counted before
     * @param change              {@code comparedOccurrences - previous}, null with previous
     * @param untrackedTemplates  templates of the cause with no history; their breaks are
     *                            left out of the comparison
     */
    public record CauseTrend(
            PatternTrend trend,
            long comparedOccurrences,
            Long previous,
            Long change,
            Integer firstSeenRun,
            int untrackedTemplates) {

        /** Some of the cause's templates could not be compared. */
        public boolean partial() {
            return untrackedTemplates > 0 && trend != null;
        }
    }

    /** How many links the history follows at most, a guard against bad data. */
    private static final int MAX_LINKS = 50;

    private final DashboardDao dao;
    private final PatternService rules;

    /** With the tolerance of {@link PatternService#STABLE_TOLERANCE_PROPERTY}, or the default. */
    public CampaignTrendService(DashboardDao dao) {
        this(dao, PatternService.configuredTolerance());
    }

    public CampaignTrendService(DashboardDao dao, double stableTolerance) {
        this.dao = dao;
        // Only its trend rules are used: they read neither the dao nor the dashboard service.
        this.rules = new PatternService(dao, null, stableTolerance);
    }

    /**
     * Reads the baseline of the given templates.
     *
     * @param templatePaths the FULL_PATH of every template of the campaign, as the export
     *                      writes it into ER_DASHBOARD_TEMPLATE
     * @param currentRunId  the RUN_ID of the live run when it has been archived, so it is not
     *                      compared with itself; null when it has not
     */
    public CampaignBaseline baselineOf(Collection<String> templatePaths, Integer currentRunId) {
        Set<String> paths = new LinkedHashSet<>();
        if (templatePaths != null) {
            for (String path : templatePaths) {
                if (path != null && !path.isBlank()) {
                    paths.add(path);
                }
            }
        }
        if (paths.isEmpty()) {
            return CampaignBaseline.NONE;
        }

        Map<Integer, String> pathById;
        List<PatternStat> history;
        try {
            pathById = dao.findTemplateIdsByPaths(paths);
            history = pathById.isEmpty() ? List.of() : dao.findPatternHistory(pathById.keySet());
        } catch (DashboardSchemaMissingException absent) {
            // An engine older than the pattern export: no history, which is not an error.
            return new CampaignBaseline(currentRunId, Set.of(), Collections.unmodifiableSet(paths),
                    Map.of(), Map.of());
        }

        // A template may have several ids: a key change restarts it under a new one.
        Map<String, List<PatternStat>> rowsByPath = new LinkedHashMap<>();
        for (PatternStat row : history) {
            String path = pathById.get(row.pattern().templateId());
            if (path != null) {
                rowsByPath.computeIfAbsent(path, key -> new ArrayList<>()).add(row);
            }
        }

        Map<String, Integer> referenceRuns = new LinkedHashMap<>();
        Map<String, Accumulator> bySignature = new LinkedHashMap<>();
        for (Map.Entry<String, List<PatternStat>> entry : rowsByPath.entrySet()) {
            Integer reference = referenceRun(entry.getValue(), currentRunId);
            if (reference == null) {
                continue;
            }
            referenceRuns.put(entry.getKey(), reference);
            accumulate(entry.getKey(), entry.getValue(), reference.intValue(), bySignature);
        }

        Set<String> untracked = new LinkedHashSet<>(paths);
        untracked.removeAll(referenceRuns.keySet());
        Map<String, SignatureBaseline> baselines = new LinkedHashMap<>();
        for (Map.Entry<String, Accumulator> entry : bySignature.entrySet()) {
            baselines.put(entry.getKey(), entry.getValue().toBaseline(entry.getKey()));
        }
        return new CampaignBaseline(currentRunId,
                Collections.unmodifiableSet(new LinkedHashSet<>(referenceRuns.keySet())),
                Collections.unmodifiableSet(untracked),
                Collections.unmodifiableMap(referenceRuns),
                Collections.unmodifiableMap(baselines));
    }

    /**
     * The first RUN_ID of a batch, to pass as {@code currentRunId} when the live run has
     * been exported as that batch: its runs, and anything archived after them, are then
     * left out of the comparison.
     *
     * @return the smallest RUN_ID of the batch, null when the batch is null or has no run
     */
    public Integer firstRunOfBatch(Integer batchId) {
        if (batchId == null) {
            return null;
        }
        List<RunRow> runs = dao.findRunsByBatch(List.of(batchId)).get(batchId);
        Integer first = null;
        if (runs != null) {
            for (RunRow run : runs) {
                if (first == null || run.runId() < first.intValue()) {
                    first = Integer.valueOf(run.runId());
                }
            }
        }
        return first;
    }

    /**
     * Finds the FULL_PATH of templates known only by name, the way the campaign report
     * knows them.
     *
     * <p>The export writes the template's path in the project tree, which ends with its
     * name: {@code REPORTING/TRADE_ATTR/DT/FO/TRADE_ATTR_08}. A name is resolved when
     * exactly one distinct FULL_PATH ends with it, case aside and an extension allowed.
     * A name ending two different paths, the same template name in two folders, is left
     * out rather than guessed: its template is then untracked, never compared with
     * another template's history.
     *
     * <p>ER_DASHBOARD_TEMPLATE holds one row per template definition, so it is read whole:
     * two queries, whatever the number of names.
     *
     * @return the FULL_PATH of every name resolved, keyed by name as given
     */
    public Map<String, String> pathsByTemplateName(Collection<String> templateNames) {
        Map<String, String> resolved = new LinkedHashMap<>();
        if (templateNames == null || templateNames.isEmpty()) {
            return resolved;
        }
        Collection<TemplateRow> templates;
        try {
            // An empty fragment matches every FULL_PATH.
            templates = dao.findTemplates(dao.findTemplateIdsByPathFragment("")).values();
        } catch (DashboardSchemaMissingException absent) {
            return resolved;
        }

        Map<String, Set<String>> pathsByLastSegment = new HashMap<>();
        for (TemplateRow template : templates) {
            String path = template.fullPath();
            if (path == null || path.isBlank()) {
                continue;
            }
            for (String key : nameKeys(lastSegment(path))) {
                pathsByLastSegment.computeIfAbsent(key, k -> new LinkedHashSet<>()).add(path);
            }
        }
        for (String name : templateNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            Set<String> candidates = pathsByLastSegment.get(name.trim().toLowerCase(Locale.ROOT));
            if (candidates != null && candidates.size() == 1) {
                resolved.put(name, candidates.iterator().next());
            }
        }
        return resolved;
    }

    /**
     * Puts a live cause against the baseline.
     *
     * @param signature    the cause's SIGNATURE, computed exactly as the export computes it
     * @param liveByPath   the cause's live count on each template it was found on
     * @return the trend, or null when there is no baseline at all
     */
    public CauseTrend trendOf(CampaignBaseline baseline, String signature, Map<String, Long> liveByPath) {
        if (baseline == null || !baseline.available()) {
            return null;
        }
        long compared = 0L;
        int untracked = 0;
        int tracked = 0;
        if (liveByPath != null) {
            for (Map.Entry<String, Long> entry : liveByPath.entrySet()) {
                long count = entry.getValue() == null ? 0L : entry.getValue().longValue();
                if (baseline.isTracked(entry.getKey())) {
                    compared += count;
                    tracked++;
                } else {
                    untracked++;
                }
            }
        }
        if (tracked == 0 && untracked > 0) {
            // Found only on templates with no history: nothing to compare with, and "New"
            // would be a guess.
            return new CauseTrend(null, 0L, null, null, null, untracked);
        }

        SignatureBaseline before = baseline.baselineOf(signature);
        Long previous = before == null ? null : Long.valueOf(before.previous());
        boolean seenBefore = before != null && before.seenBefore();
        PatternTrend trend = rules.trendOf(compared, previous, seenBefore);
        return new CauseTrend(trend, compared, previous,
                previous == null ? null : Long.valueOf(compared - previous.longValue()),
                before == null ? null : before.firstSeenRun(), untracked);
    }

    /**
     * The causes counted on the reference runs and absent from the live run, most breaks
     * first. "Not detected" rather than fixed: below its support floor a cause's breaks go to
     * its column's unexplained pattern, and they may well still be there.
     *
     * @param liveSignatures the signatures of every live cause
     */
    public List<SignatureBaseline> vanished(CampaignBaseline baseline, Set<String> liveSignatures) {
        if (baseline == null || !baseline.available()) {
            return List.of();
        }
        List<SignatureBaseline> gone = new ArrayList<>();
        for (SignatureBaseline before : baseline.bySignature().values()) {
            if (before.previous() > 0L
                    && (liveSignatures == null || !liveSignatures.contains(before.signature()))) {
                gone.add(before);
            }
        }
        gone.sort(Comparator.comparingLong(SignatureBaseline::previous).reversed()
                .thenComparing(SignatureBaseline::signature));
        return gone;
    }

    // ------------------------------------------------------------------------ internals

    private static String lastSegment(String path) {
        int cut = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return (cut < 0 ? path : path.substring(cut + 1)).trim();
    }

    /** The segment, lower case, and the segment without its extension when it has one. */
    private static Set<String> nameKeys(String segment) {
        Set<String> keys = new LinkedHashSet<>();
        String lower = segment.toLowerCase(Locale.ROOT);
        keys.add(lower);
        int dot = lower.lastIndexOf('.');
        if (dot > 0) {
            keys.add(lower.substring(0, dot));
        }
        return keys;
    }

    /** The latest run of the template's history before {@code currentRunId}, null if none. */
    private static Integer referenceRun(List<PatternStat> rows, Integer currentRunId) {
        Integer reference = null;
        for (PatternStat row : rows) {
            int run = row.runId();
            if (currentRunId != null && run >= currentRunId.intValue()) {
                continue;
            }
            if (reference == null || run > reference.intValue()) {
                reference = Integer.valueOf(run);
            }
        }
        return reference;
    }

    /**
     * Adds one template's patterns, as they stood on its reference run, to the totals.
     *
     * <p>A pattern linked to an earlier one continues its history, as on the pattern screen:
     * the earlier pattern's runs before the newer one starts count as the newer one's, and
     * the earlier pattern, superseded, is not reported on its own.
     */
    private static void accumulate(String path, List<PatternStat> rows, int reference,
                                   Map<String, Accumulator> bySignature) {
        Map<Integer, PatternRow> patterns = new LinkedHashMap<>();
        Map<Integer, List<PatternStat>> rowsByPattern = new HashMap<>();
        for (PatternStat row : rows) {
            patterns.putIfAbsent(row.patternId(), row.pattern());
            rowsByPattern.computeIfAbsent(row.patternId(), key -> new ArrayList<>()).add(row);
        }
        Set<Integer> superseded = new HashSet<>();
        for (PatternRow pattern : patterns.values()) {
            if (pattern.linkedPatternId() != null && patterns.containsKey(pattern.linkedPatternId())) {
                superseded.add(pattern.linkedPatternId());
            }
        }

        for (PatternRow pattern : patterns.values()) {
            if (superseded.contains(pattern.patternId()) || pattern.signature() == null) {
                continue;
            }
            long previous = 0L;
            boolean known = false;
            boolean seenBefore = false;
            Integer firstSeen = null;
            for (PatternStat row : effectiveRows(pattern, patterns, rowsByPattern)) {
                if (row.runId() > reference) {
                    continue;
                }
                known = true;
                if (row.runId() == reference) {
                    previous += row.occurrences();
                }
                if (row.occurrences() > 0L) {
                    seenBefore = true;
                    if (firstSeen == null || row.runId() < firstSeen.intValue()) {
                        firstSeen = Integer.valueOf(row.runId());
                    }
                }
            }
            if (!known) {
                // First counted after the reference run: new as far as this comparison goes.
                continue;
            }
            bySignature.computeIfAbsent(pattern.signature(), key -> new Accumulator())
                    .add(path, pattern, previous, seenBefore, firstSeen);
        }
    }

    /** The pattern's own rows, then those of the patterns it continues from before it started. */
    private static List<PatternStat> effectiveRows(PatternRow pattern, Map<Integer, PatternRow> patterns,
                                                   Map<Integer, List<PatternStat>> rowsByPattern) {
        List<PatternStat> own = rowsByPattern.getOrDefault(pattern.patternId(), List.of());
        List<PatternStat> rows = new ArrayList<>(own);
        int cutoff = Integer.MAX_VALUE;
        for (PatternStat row : own) {
            cutoff = Math.min(cutoff, row.runId());
        }
        Set<Integer> seen = new HashSet<>();
        seen.add(pattern.patternId());
        Integer next = pattern.linkedPatternId();
        int steps = 0;
        while (next != null && seen.add(next) && steps++ < MAX_LINKS) {
            PatternRow linked = patterns.get(next);
            if (linked == null) {
                break;  // linked to another template's pattern: outside this campaign's paths
            }
            int earliest = cutoff;
            for (PatternStat row : rowsByPattern.getOrDefault(next, List.of())) {
                if (row.runId() < cutoff) {
                    rows.add(row);
                    earliest = Math.min(earliest, row.runId());
                }
            }
            cutoff = earliest;
            next = linked.linkedPatternId();
        }
        return rows;
    }

    /** Running totals of one signature across templates. */
    private static final class Accumulator {

        private long previous;
        private boolean seenBefore;
        private Integer firstSeenRun;
        private String description;
        private final Set<String> paths = new LinkedHashSet<>();

        void add(String path, PatternRow pattern, long count, boolean seen, Integer firstSeen) {
            previous += count;
            seenBefore |= seen;
            if (firstSeen != null && (firstSeenRun == null || firstSeen.intValue() < firstSeenRun.intValue())) {
                firstSeenRun = firstSeen;
            }
            if (description == null || description.isBlank()) {
                description = pattern.label();
            }
            if (count > 0L) {
                paths.add(path);
            }
        }

        SignatureBaseline toBaseline(String signature) {
            return new SignatureBaseline(signature, description, previous, seenBefore, firstSeenRun,
                    Collections.unmodifiableSet(paths));
        }
    }
}
