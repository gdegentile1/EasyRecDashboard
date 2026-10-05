import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.PatternKind;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternSighting;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;
import com.finboxsolutions.easyrec.dashboard.service.TemplateIds;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Exercises the pattern read side against an in-memory H2 database laid out like the
 * Liquibase changelogs, foreign keys included: trends on counts and on rates, the
 * description stopgap and the expected RCA rule names, the template id rule, a skipped
 * export, the unexplained totals, the cross-template view, linking and its refusals, the
 * history continued across a link, the batch delete, and a datasource without the pattern
 * tables.
 *
 * <p>Run with H2 on the classpath; exits non-zero on the first failed check.
 */
public final class PatternTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        trendRules();
        patternKinds();
        templateIds();
        sampleRun();
        withPatternTables();
        withoutPatternTables();
        if (failures > 0) {
            System.out.println(failures + " check(s) FAILED");
            System.exit(1);
        }
        System.out.println("all checks passed");
    }

    // ------------------------------------------------------------------------- trend rules

    private static void trendRules() {
        PatternService service = new PatternService(null, null, 0.10d);
        check("first detection is NEW", service.trendOf(5, null, false), PatternTrend.NEW);
        check("within 10% is STABLE", service.trendOf(105, 100L, true), PatternTrend.STABLE);
        check("more than 10% up is INCREASING", service.trendOf(111, 100L, true), PatternTrend.INCREASING);
        check("more than 10% down is DECREASING", service.trendOf(89, 100L, true), PatternTrend.DECREASING);
        check("zero after a count is NOT_DETECTED", service.trendOf(0, 4L, true), PatternTrend.NOT_DETECTED);
        check("zero after zero is STILL_NOT_DETECTED", service.trendOf(0, 0L, true),
                PatternTrend.STILL_NOT_DETECTED);
        check("back after zero is REAPPEARED", service.trendOf(3, 0L, true), PatternTrend.REAPPEARED);
        check("first count after zeros only is NEW", service.trendOf(3, 0L, false), PatternTrend.NEW);
        check("double the count on a run twice the size is STABLE",
                service.trendOf(20, 10L, true, 5.0d, 5.0d), PatternTrend.STABLE);
        check("same count on a run twice the size is DECREASING",
                service.trendOf(10, 10L, true, 2.5d, 5.0d), PatternTrend.DECREASING);
        check("the tolerance is a setting",
                new PatternService(null, null, 0.5d).trendOf(140, 100L, true), PatternTrend.STABLE);
        check("no trend says resolved", List.of(PatternTrend.values()).stream()
                .anyMatch(trend -> trend.label().toLowerCase().contains("resolved")
                        || trend.label().toLowerCase().contains("fixed")), false);
    }

    // ----------------------------------------------------------------------- pattern kinds

    private static void patternKinds() {
        PatternKind shift = PatternKind.of("VALUE_DATE / Constant date shift / 1");
        check("column read", shift.column(), "VALUE_DATE");
        check("type read", shift.typeName(), "DATE_SHIFT");
        check("rule name of the date shift", shift.expectedRuleName("98f55b15deadbeef"),
                "Pattern_VALUE_DATE_DATE_SHIFT_98f55b15");
        check("rule name of a substitution",
                PatternKind.of("M_F_CTP / Single substitution / LEHMAN>DEFAULT_LEHMAN")
                        .expectedRuleName("e9d5f976aaaaaaaa"),
                "Pattern_M_F_CTP_SINGLE_SUBSTITUTION_e9d5f976");
        check("column sanitised in the rule name",
                PatternKind.of("M F-CTP / Single substitution / IT>INT").expectedRuleName("5a7b5b8e00"),
                "Pattern_M_F_CTP_SINGLE_SUBSTITUTION_5a7b5b8e");
        check("a key holding the separator and a type label",
                PatternKind.of("M_F_CTP / Single substitution / A / Constant factor>B").typeName(),
                "SINGLE_SUBSTITUTION");
        check("unexplained recognised", PatternKind.isUnexplained(pattern("AMOUNT / No rule found")), true);
        check("a rule pattern is not unexplained",
                PatternKind.isUnexplained(pattern("VALUE_DATE / Constant date shift / 1")), false);
        check("unexplained has no rule name",
                PatternKind.of("AMOUNT / No rule found").expectedRuleName("0123456789"), null);
        check("row level has no column", PatternKind.of("(row) / Rows missing on one side").column(), null);
        check("row level has no rule name",
                PatternKind.of("(row) / Rows missing on one side").expectedRuleName("0123456789"), null);
        check("an unknown type is not guessed", PatternKind.of("X / Something new / 1").isKnown(), false);
        check("a label must end at a separator",
                PatternKind.of("X / Constant factorial / 2").isKnown(), false);
    }

    // ------------------------------------------------------------------------ template ids

    private static void templateIds() {
        check("exact match preferred", TemplateIds.resolve(1, Set.of(1, 2), Set.of(1, 2)), 1);
        check("legacy +1 pairing kept", TemplateIds.resolve(1, Set.of(2), Set.of(1)), 2);
        check("no borrowing from another reconciliation of the run",
                TemplateIds.resolve(1, Set.of(2), Set.of(1, 2)), 1);
    }

    // -------------------------------------------------------------------------- sample run

    /**
     * The shape of the EasyRec sample: run 1 on the project template 2, its statistics,
     * context and patterns on template 1, six patterns counted 9, 9, 6, 1, 1, 1.
     */
    private static void sampleRun() throws SQLException {
        DataSource dataSource = h2("sample");
        try (Connection connection = dataSource.getConnection();
             Statement sql = connection.createStatement()) {
            createCoreTables(sql);
            createPatternTables(sql);
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (1, '/samples/csv/rec.xml', 'ID', NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (2, '/samples/project.xml', NULL, NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_BATCH (BATCH_ID, USER_NAME, STATUS, SYS_TIME, PURGE_STATUS) "
                    + "VALUES (1, 'ops', '1', TIMESTAMP '2026-10-01 08:00:00', 0)");
            sql.execute("INSERT INTO ER_DASHBOARD_RUN (RUN_ID, BATCH_ID, TEMPLATE_ID, STATUS, PURGE_STATUS) "
                    + "VALUES (1, 1, 2, '1', 0)");
            sql.execute("INSERT INTO ER_DASHBOARD_RUN_CONTEXT (RUN_ID, POSITION, TEMPLATE_ID, STATUS) "
                    + "VALUES (1, 0, 1, 0)");
            sql.execute("INSERT INTO ER_DASHBOARD_STAT_ROWS (RUN_ID, TEMPLATE_ID, STATUS, ROWS_SOURCE, "
                    + "ROWS_TARGET, MISSING_SOURCE, MISSING_TARGET, UNMATCHED, MATCHED, FORCE_MATCHED, "
                    + "NB_COMMENTS) VALUES (1, 1, '1', 100, 100, 0, 0, 27, 73, 0, 0)");
            String[] descriptions = {
                "M_F_CTP / Single substitution / LEHMAN>DEFAULT_LEHMAN",
                "VALUE_DATE / Constant date shift / 1",
                "AMOUNT / No rule found",
                "M_F_TYPO4 / Single substitution / IT>INT",
                "M_F_TRN / Single substitution / X>Y",
                "M_F_BOOK / Single substitution / A>B",
            };
            long[] counts = {9, 9, 6, 1, 1, 1};
            for (int index = 0; index < descriptions.length; index++) {
                int id = index + 1;
                sql.execute("INSERT INTO ER_DASHBOARD_PATTERN (PATTERN_ID, TEMPLATE_ID, SIGNATURE, "
                        + "DESCRIPTION, FIRST_SEEN_RUN) VALUES (" + id + ", 1, 'sig" + id + "', '"
                        + descriptions[index] + "', 1)");
                stat(sql, 1, id, counts[index]);
            }
        }
        DashboardDao dao = new JdbcDashboardDao(dataSource);
        DashboardService dashboard = new DashboardService(dao);
        PatternService patterns = new PatternService(dao, dashboard);

        DashboardService.Reconciliation rec = dashboard.findReconciliations(List.of(1), null).get(0);
        check("sample: statistics on template 1, not the run's 2", rec.statsTemplateId(), 1);
        PatternService.RunPatterns found = patterns.patternsOf(1, rec.statsTemplateId());
        List<Long> occurrences = new ArrayList<>();
        found.lines().forEach(line -> occurrences.add(line.occurrences()));
        check("sample: the six patterns with their counts", occurrences, List.of(9L, 9L, 6L, 1L, 1L, 1L));
        check("sample: six detected", found.presentCount(), 6L);
        check("sample: six breaks with no rule found", found.unexplained(), 6L);
        check("sample: the run's own template has no patterns", patterns.patternsOf(1, 2).tracked(), false);
    }

    // ----------------------------------------------------------------- with pattern tables

    private static void withPatternTables() throws SQLException {
        DataSource dataSource = h2("patterns");
        try (Connection connection = dataSource.getConnection();
             Statement sql = connection.createStatement()) {
            createCoreTables(sql);
            createPatternTables(sql);
            // 1: the reconciliation. 2: the project template ER_DASHBOARD_RUN points at.
            // 3: the same reconciliation after a key change, a new id. 4: another one.
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (1, '/rec/trades.xml', 'ID', NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (2, '/projects/trades.xml', NULL, NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (3, '/rec/trades.xml', 'ID,BOOK', NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (4, '/rec/other.xml', 'ID', NULL)");
            for (int run = 1; run <= 8; run++) {
                sql.execute("INSERT INTO ER_DASHBOARD_BATCH (BATCH_ID, USER_NAME, STATUS, SYS_TIME, PURGE_STATUS) "
                        + "VALUES (" + run + ", 'ops', '1', TIMESTAMP '2026-09-0" + run + " 08:00:00', 0)");
                sql.execute("INSERT INTO ER_DASHBOARD_RUN (RUN_ID, BATCH_ID, TEMPLATE_ID, STATUS, PURGE_STATUS) "
                        + "VALUES (" + run + ", " + run + ", 2, '1', 0)");
                int template = run == 8 ? 3 : 1;
                // Run 6 compared twice as many rows: its rates halve for the same counts.
                rowStats(sql, run, template, run == 6 ? 400 : 200);
            }
            // Run 3 also reconciled template 4.
            rowStats(sql, 3, 4, 200);

            // A: 5, 5, 8, 0, 0, 3 - first seen on run 1.
            // B: first seen on run 3, counted on 3..6, linked to C.
            // C: only ever counted on run 1, so deleting batch 1 leaves it with no history.
            // D: NPV's unexplained breaks, on every run.
            pattern(sql, 10, 1, "aaa", "NPV / Value mapping table / EUR>USD", 1, null);
            pattern(sql, 12, 1, "ccc", "DELTA / No rule found", 1, null);
            pattern(sql, 11, 1, "bbb", "GAMMA / Precision loss / 4", 3, 12);
            pattern(sql, 13, 1, "ddd", "NPV / No rule found", 1, null);
            // E: A's cause after the key change, on template 3. F: A's signature on template 4.
            pattern(sql, 20, 3, "eee", "NPV / Value mapping table / EUR>USD,GBP>USD", 8, null);
            pattern(sql, 21, 4, "aaa", "NPV / Value mapping table / EUR>USD", 3, null);
            long[] a = {5, 5, 8, 0, 0, 3};
            long[] d = {2, 2, 2, 6, 6, 2};
            for (int run = 1; run <= 6; run++) {
                stat(sql, run, 10, a[run - 1]);
                stat(sql, run, 13, d[run - 1]);
                if (run >= 3) {
                    stat(sql, run, 11, run == 3 ? 4 : 2);
                }
            }
            stat(sql, 1, 12, 7);
            stat(sql, 3, 21, 9);
            stat(sql, 8, 20, 4);
            // Run 7 has statistics but no pattern row: its export was skipped.
        }

        DashboardDao dao = new JdbcDashboardDao(dataSource);
        DashboardService dashboard = new DashboardService(dao);
        PatternService patterns = new PatternService(dao, dashboard);

        PatternService.RunPatterns run3 = patterns.patternsOf(3, 1);
        check("run 3 is tracked", run3.tracked(), true);
        check("run 3 lists the three patterns it counted", run3.lines().size(), 3);
        check("most occurrences first", run3.lines().get(0).pattern().patternId(), 10);
        check("A on run 3 is INCREASING", run3.lines().get(0).trend(), PatternTrend.INCREASING);
        check("A on run 3 moved by +3", run3.lines().get(0).change(), 3L);
        check("A on run 3 is 4% of 200 rows", run3.lines().get(0).shareOfRows(), 4.0d);
        check("B on run 3 is NEW", run3.lines().get(1).trend(), PatternTrend.NEW);
        check("four patterns known on the template", run3.knownCount(), 4);
        check("run 3 has 2 breaks with no rule found", run3.unexplained(), 2L);

        PatternService.RunPatterns run4 = patterns.patternsOf(4, 1);
        check("A on run 4 is NOT_DETECTED", lineOf(run4, 10).trend(), PatternTrend.NOT_DETECTED);
        check("A on run 4 shows NPV's unexplained count beside it", lineOf(run4, 10).unexplainedOnColumn(), 6L);
        check("a detected pattern shows no unexplained count", lineOf(run4, 11).unexplainedOnColumn(), null);
        check("A on run 5 is STILL_NOT_DETECTED", lineOf(patterns.patternsOf(5, 1), 10).trend(),
                PatternTrend.STILL_NOT_DETECTED);
        check("A on run 6 is REAPPEARED", lineOf(patterns.patternsOf(6, 1), 10).trend(), PatternTrend.REAPPEARED);
        check("B on run 4 is DECREASING", lineOf(run4, 11).trend(), PatternTrend.DECREASING);
        check("B on run 6, same count on twice the rows, is DECREASING",
                lineOf(patterns.patternsOf(6, 1), 11).trend(), PatternTrend.DECREASING);
        check("B on run 6 is 0.5% of 400 rows", lineOf(patterns.patternsOf(6, 1), 11).shareOfRows(), 0.5d);
        check("the run's own template finds nothing", patterns.patternsOf(3, 2).tracked(), false);

        PatternService.RunPatterns run7 = patterns.patternsOf(7, 1);
        check("run 7 is tracked", run7.tracked(), true);
        check("run 7 was not exported - no rows, not zeros", run7.exported(), false);

        Map<Integer, Long> unexplained = patterns.unexplainedByRun(1, List.of(1, 2, 3, 4, 5, 6, 7));
        check("unexplained on run 1 is C + D", unexplained.get(1), 9L);
        check("unexplained on run 4", unexplained.get(4), 6L);
        check("no unexplained figure for a skipped run", unexplained.containsKey(7), false);

        PatternService.PatternHistory history = patterns.patternHistory(10);
        check("A has six points", history.points().size(), 6);
        check("A was detected on four runs", history.detectedRuns(), 4);
        check("A peaked on run 3", history.peak().runId(), 3);
        check("A's points open the context template", history.points().get(0).contextTemplateId(), 1);
        check("A's points carry their batch", history.points().get(5).batch().batchId(), 6);
        check("A's runs differ in size", history.runSizesDiffer(), true);
        check("B continues C", patterns.patternHistory(11).linkedPattern().patternId(), 12);
        check("B's history runs on through C", patterns.patternHistory(11).points().size(), 5);

        List<PatternSighting> sightings = patterns.sightings(dao.findPattern(10), 3);
        check("A's signature is on two templates", sightings.size(), 2);
        check("ordered by path", sightings.get(0).templatePath(), "/rec/other.xml");
        check("with its count on the run there", sightings.get(0).occurrences(), 9L);
        check("and here", sightings.get(1).occurrences(), 8L);
        check("no stat row is no count, not zero", patterns.sightings(dao.findPattern(10), 8).get(1).occurrences(),
                null);

        check("E may link to the patterns of the same path",
                ids(patterns.linkCandidates(20)), List.of(10, 12, 13, 11));
        check("a link to itself is refused", refusal(() -> patterns.link(20, 20)) != null, true);
        check("E linked to A", patterns.link(20, 10), 1);
        check("the link is stored", dao.findPattern(20).linkedPatternId(), 10);
        check("A may not link back to E", ids(patterns.linkCandidates(10)).contains(20), false);
        check("a cycle is refused", refusal(() -> patterns.link(10, 20)) != null, true);
        check("A's link is untouched by the refusal", dao.findPattern(10).linkedPatternId(), null);
        PatternService.PatternHistory continued = patterns.patternHistory(20);
        check("E's history continues across the template change", continued.points().size(), 7);
        check("its first points were counted as A", continued.points().get(0).countedAs().patternId(), 10);
        check("its last point is its own", continued.latest().countedAs().patternId(), 20);
        check("the link cleared", patterns.link(20, null), 1);
        check("no link left", dao.findPattern(20).linkedPatternId(), null);

        Map<String, Integer> removed = dao.deleteBatches(List.of(1));
        check("run 1's pattern stats removed", removed.get("ER_DASHBOARD_PATTERN_STAT"), 3);
        check("C, left with no history, removed", removed.get("ER_DASHBOARD_PATTERN"), 1);
        check("C is gone", dao.findPattern(12), null);
        check("A moved to the earliest run still counting it", dao.findPattern(10).firstSeenRun(), 2);
        check("B's link to C cleared", dao.findPattern(11).linkedPatternId(), null);
        check("A now first counted on run 2 is NEW there",
                lineOf(patterns.patternsOf(2, 1), 10).trend(), PatternTrend.NEW);
    }

    // -------------------------------------------------------------- without pattern tables

    private static void withoutPatternTables() throws SQLException {
        DataSource dataSource = h2("legacy");
        try (Connection connection = dataSource.getConnection();
             Statement sql = connection.createStatement()) {
            createCoreTables(sql);
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (1, '/rec/a.xml', NULL, NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_BATCH (BATCH_ID, STATUS, PURGE_STATUS) VALUES (1, '1', 0)");
            sql.execute("INSERT INTO ER_DASHBOARD_RUN (RUN_ID, BATCH_ID, STATUS, PURGE_STATUS) VALUES (1, 1, '1', 0)");
        }
        DashboardDao dao = new JdbcDashboardDao(dataSource);
        PatternService patterns = new PatternService(dao, new DashboardService(dao));
        check("no pattern tables: not tracked", patterns.patternsOf(1, 1).tracked(), false);
        check("no pattern tables: no history", patterns.patternHistory(1), null);
        check("no pattern tables: no unexplained figures", patterns.unexplainedByRun(1, List.of(1)).isEmpty(), true);
        Map<String, Integer> removed = dao.deleteBatches(List.of(1));
        check("no pattern tables: the batch is still deleted", removed.get("ER_DASHBOARD_BATCH"), 1);
        check("no pattern tables: no pattern table touched",
                removed.containsKey("ER_DASHBOARD_PATTERN_STAT"), false);
    }

    // ---------------------------------------------------------------------------- helpers

    private static PatternService.PatternLine lineOf(PatternService.RunPatterns found, int patternId) {
        for (PatternService.PatternLine line : found.lines()) {
            if (line.pattern().patternId() == patternId) {
                return line;
            }
        }
        throw new AssertionError("pattern " + patternId + " not on the run");
    }

    private static PatternRow pattern(String description) {
        return new PatternRow(1, 1, "0123456789abcdef", description, 1, null);
    }

    private static List<Integer> ids(List<PatternRow> rows) {
        List<Integer> ids = new ArrayList<>();
        rows.forEach(row -> ids.add(row.patternId()));
        return ids;
    }

    private static String refusal(Runnable action) {
        try {
            action.run();
            return null;
        } catch (IllegalArgumentException refused) {
            return refused.getMessage();
        }
    }

    private static void pattern(Statement sql, int id, int template, String signature, String description,
                                int firstSeenRun, Integer linked) throws SQLException {
        sql.execute("INSERT INTO ER_DASHBOARD_PATTERN (PATTERN_ID, TEMPLATE_ID, SIGNATURE, DESCRIPTION, "
                + "FIRST_SEEN_RUN, LINKED_PATTERN_ID) VALUES (" + id + ", " + template + ", '" + signature
                + "', '" + description + "', " + firstSeenRun + ", " + linked + ")");
    }

    private static void rowStats(Statement sql, int run, int template, long rows) throws SQLException {
        sql.execute("INSERT INTO ER_DASHBOARD_RUN_CONTEXT (RUN_ID, POSITION, TEMPLATE_ID, STATUS) "
                + "VALUES (" + run + ", 0, " + template + ", 0)");
        sql.execute("INSERT INTO ER_DASHBOARD_STAT_ROWS (RUN_ID, TEMPLATE_ID, STATUS, ROWS_SOURCE, "
                + "ROWS_TARGET, MISSING_SOURCE, MISSING_TARGET, UNMATCHED, MATCHED, FORCE_MATCHED, "
                + "NB_COMMENTS) VALUES (" + run + ", " + template + ", '1', " + rows + ", " + rows
                + ", 0, 0, 20, " + (rows - 20) + ", 0, 0)");
    }

    private static void stat(Statement sql, int run, int pattern, long occurrences) throws SQLException {
        // ABOVE_THRESHOLD is reserved and always 1 today.
        sql.execute("INSERT INTO ER_DASHBOARD_PATTERN_STAT (RUN_ID, PATTERN_ID, OCCURRENCES, ABOVE_THRESHOLD) "
                + "VALUES (" + run + ", " + pattern + ", " + occurrences + ", 1)");
    }

    private static void createCoreTables(Statement sql) throws SQLException {
        sql.execute("CREATE TABLE ER_DASHBOARD_TEMPLATE (TEMPLATE_ID INT PRIMARY KEY, FULL_PATH VARCHAR(1000), "
                + "KEY_COLUMNS VARCHAR(1000), IGNORE_COLUMNS VARCHAR(1000))");
        sql.execute("CREATE TABLE ER_DASHBOARD_BATCH (BATCH_ID INT PRIMARY KEY, VERSION VARCHAR(50), "
                + "RUN_MODE VARCHAR(50), USER_NAME VARCHAR(250), STATUS VARCHAR(10), SYS_DATE DATE, "
                + "SYS_TIME TIMESTAMP, DURATION_MILLISEC BIGINT, DESCRIPTION VARCHAR(1000), PURGE_STATUS INT)");
        sql.execute("CREATE TABLE ER_DASHBOARD_RUN (RUN_ID INT PRIMARY KEY, "
                + "BATCH_ID INT REFERENCES ER_DASHBOARD_BATCH(BATCH_ID), REC_TYPE VARCHAR(50), "
                + "TEMPLATE_ID INT, PROJECT_PATH VARCHAR(1000), SOURCE_ALIAS VARCHAR(250), "
                + "TARGET_ALIAS VARCHAR(250), SOURCE_LABEL VARCHAR(250), TARGET_LABEL VARCHAR(250), "
                + "STATUS VARCHAR(10), SYS_DATE DATE, SYS_TIME TIMESTAMP, DURATION_MILLISEC BIGINT, "
                + "DESCRIPTION VARCHAR(1000), PURGE_STATUS INT)");
        sql.execute("CREATE TABLE ER_DASHBOARD_RUN_CONTEXT (RUN_ID INT REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "POSITION INT, TEMPLATE_ID INT, SOURCE_ALIAS VARCHAR(250), TARGET_ALIAS VARCHAR(250), "
                + "SOURCE_LABEL VARCHAR(250), TARGET_LABEL VARCHAR(250), NAME VARCHAR(250), "
                + "CATEGORY1 VARCHAR(250), CATEGORY2 VARCHAR(250), CATEGORY3 VARCHAR(250), "
                + "PRIORITY VARCHAR(50), USER_NAME VARCHAR(250), USER_EMAIL VARCHAR(250), "
                + "GROUP_NAME VARCHAR(250), GROUP_EMAIL VARCHAR(250), STATUS INT, DUE_DATE DATE, "
                + "DESCRIPTION VARCHAR(1000))");
        sql.execute("CREATE TABLE ER_DASHBOARD_STAT_ROWS (RUN_ID INT REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "TEMPLATE_ID INT, STATUS VARCHAR(10), ROWS_SOURCE BIGINT, ROWS_TARGET BIGINT, "
                + "MISSING_SOURCE BIGINT, MISSING_TARGET BIGINT, UNMATCHED BIGINT, MATCHED BIGINT, "
                + "FORCE_MATCHED BIGINT, NB_COMMENTS BIGINT, FILTER VARCHAR(1000), MATCH_TYPE VARCHAR(50), "
                + "PIVOT_BREAKDOWN VARCHAR(1000))");
        sql.execute("CREATE TABLE ER_DASHBOARD_STAT_COLS (RUN_ID INT REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "TEMPLATE_ID INT)");
        sql.execute("CREATE TABLE ER_DASHBOARD_PIVOT (RUN_ID INT REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "TEMPLATE_ID INT)");
    }

    /**
     * As changelog-CREATE_ER_DASHBOARD_PATTERN and _PATTERN_STAT declare them: no ROOT_CAUSE,
     * OWNER_NAME or TICKET_REF any more.
     */
    private static void createPatternTables(Statement sql) throws SQLException {
        sql.execute("CREATE TABLE ER_DASHBOARD_PATTERN (PATTERN_ID INT PRIMARY KEY, "
                + "TEMPLATE_ID INT NOT NULL REFERENCES ER_DASHBOARD_TEMPLATE(TEMPLATE_ID), "
                + "SIGNATURE VARCHAR(64) NOT NULL, DESCRIPTION VARCHAR(1000), "
                + "FIRST_SEEN_RUN INT NOT NULL REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "LINKED_PATTERN_ID INT REFERENCES ER_DASHBOARD_PATTERN(PATTERN_ID))");
        sql.execute("CREATE UNIQUE INDEX UK_ER_DASHBOARD_PATTERN__SIGNATURE "
                + "ON ER_DASHBOARD_PATTERN (TEMPLATE_ID, SIGNATURE)");
        sql.execute("CREATE INDEX ER_DASHBOARD_PATTERN_IND1 ON ER_DASHBOARD_PATTERN (SIGNATURE)");
        sql.execute("CREATE TABLE ER_DASHBOARD_PATTERN_STAT ("
                + "RUN_ID INT NOT NULL REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "PATTERN_ID INT NOT NULL REFERENCES ER_DASHBOARD_PATTERN(PATTERN_ID), "
                + "OCCURRENCES INT NOT NULL, ABOVE_THRESHOLD SMALLINT NOT NULL, "
                + "TEMPLATE_CFG_HASH VARCHAR(64), PRIMARY KEY (RUN_ID, PATTERN_ID))");
    }

    private static void check(String what, Object actual, Object expected) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        System.out.println((ok ? "ok    " : "FAIL  ") + what
                + (ok ? "" : "  (expected " + expected + ", got " + actual + ")"));
        if (!ok) {
            failures++;
        }
    }

    private static DataSource h2(String name) {
        String url = "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1";
        return new DataSource() {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
            @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) { }
            @Override public void setLoginTimeout(int seconds) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> iface) { return null; }
            @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        };
    }
}
