import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.PatternRow;
import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.service.DashboardService;
import com.finboxsolutions.easyrec.dashboard.service.PatternService;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Exercises the pattern read side against an in-memory H2 database laid out like the
 * Liquibase changelogs, foreign keys included: trends, the share of rows, the qualification
 * write, the batch delete, and a datasource without the pattern tables.
 *
 * <p>Run with H2 on the classpath; exits non-zero on the first failed check.
 */
public final class PatternTest {

    private static int failures;

    public static void main(String[] args) throws Exception {
        trendRules();
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
        PatternService service = new PatternService(null, null);
        check("first detection is NEW", service.trendOf(5, null, false), PatternTrend.NEW);
        check("within 10% is STABLE", service.trendOf(105, 100L, true), PatternTrend.STABLE);
        check("more than 10% up is INCREASING", service.trendOf(111, 100L, true), PatternTrend.INCREASING);
        check("more than 10% down is DECREASING", service.trendOf(89, 100L, true), PatternTrend.DECREASING);
        check("zero after a count is RESOLVED", service.trendOf(0, 4L, true), PatternTrend.RESOLVED);
        check("zero after zero is ABSENT", service.trendOf(0, 0L, true), PatternTrend.ABSENT);
        check("back after zero is REAPPEARED", service.trendOf(3, 0L, true), PatternTrend.REAPPEARED);
        check("first count after zeros only is NEW", service.trendOf(3, 0L, false), PatternTrend.NEW);
    }

    // ----------------------------------------------------------------- with pattern tables

    private static void withPatternTables() throws SQLException {
        DataSource dataSource = h2("patterns");
        try (Connection connection = dataSource.getConnection();
             Statement sql = connection.createStatement()) {
            createCoreTables(sql);
            createPatternTables(sql);
            // Six batches of one run each. Context TEMPLATE_ID 1, statistics filed under 2:
            // the offset TemplateIds resolves, and the one patterns are filed under too.
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (1, '/rec/trades.xml', 'ID', NULL)");
            sql.execute("INSERT INTO ER_DASHBOARD_TEMPLATE VALUES (2, '/rec/trades.xml', 'ID', NULL)");
            for (int run = 1; run <= 6; run++) {
                sql.execute("INSERT INTO ER_DASHBOARD_BATCH (BATCH_ID, USER_NAME, STATUS, SYS_TIME, PURGE_STATUS) "
                        + "VALUES (" + run + ", 'ops', '1', TIMESTAMP '2026-09-0" + run + " 08:00:00', 0)");
                sql.execute("INSERT INTO ER_DASHBOARD_RUN (RUN_ID, BATCH_ID, TEMPLATE_ID, STATUS, PURGE_STATUS) "
                        + "VALUES (" + run + ", " + run + ", 1, '1', 0)");
                sql.execute("INSERT INTO ER_DASHBOARD_RUN_CONTEXT (RUN_ID, POSITION, TEMPLATE_ID, STATUS) "
                        + "VALUES (" + run + ", 0, 1, 0)");
                sql.execute("INSERT INTO ER_DASHBOARD_STAT_ROWS (RUN_ID, TEMPLATE_ID, STATUS, ROWS_SOURCE, "
                        + "ROWS_TARGET, MISSING_SOURCE, MISSING_TARGET, UNMATCHED, MATCHED, FORCE_MATCHED, "
                        + "NB_COMMENTS) VALUES (" + run + ", 2, '1', 200, 200, 0, 0, 20, 180, 0, 0)");
            }
            // A: 5, 5, 8, 0, 0, 3 - first seen on run 1.
            // B: first seen on run 3, counted on 3..6, linked to C.
            // C: only ever counted on run 1, so deleting batch 1 leaves it with no history.
            sql.execute("INSERT INTO ER_DASHBOARD_PATTERN (PATTERN_ID, TEMPLATE_ID, SIGNATURE, DESCRIPTION, "
                    + "FIRST_SEEN_RUN) VALUES (10, 2, 'aaa', 'NPV / Value mapping / EUR>USD', 1)");
            sql.execute("INSERT INTO ER_DASHBOARD_PATTERN (PATTERN_ID, TEMPLATE_ID, SIGNATURE, DESCRIPTION, "
                    + "FIRST_SEEN_RUN) VALUES (12, 2, 'ccc', 'DELTA / Unstructured', 1)");
            sql.execute("INSERT INTO ER_DASHBOARD_PATTERN (PATTERN_ID, TEMPLATE_ID, SIGNATURE, DESCRIPTION, "
                    + "FIRST_SEEN_RUN, LINKED_PATTERN_ID) VALUES (11, 2, 'bbb', 'GAMMA / Precision loss', 3, 12)");
            long[] a = {5, 5, 8, 0, 0, 3};
            for (int run = 1; run <= 6; run++) {
                stat(sql, run, 10, a[run - 1]);
                if (run >= 3) {
                    stat(sql, run, 11, run == 3 ? 4 : 2);
                }
            }
            stat(sql, 1, 12, 7);
        }

        DashboardDao dao = new JdbcDashboardDao(dataSource);
        DashboardService dashboard = new DashboardService(dao);
        PatternService patterns = new PatternService(dao, dashboard);

        RowStats stats3 = dao.findRowStats(List.of(3)).get(0);
        PatternService.RunPatterns run3 = patterns.patternsOf(3, 2, stats3);
        check("run 3 is tracked", run3.tracked(), true);
        check("run 3 lists the two patterns it counted", run3.lines().size(), 2);
        check("most occurrences first", run3.lines().get(0).pattern().patternId(), 10);
        check("A on run 3 is INCREASING", run3.lines().get(0).trend(), PatternTrend.INCREASING);
        check("A on run 3 moved by +3", run3.lines().get(0).change(), 3L);
        check("A on run 3 is 4% of 200 rows", run3.lines().get(0).shareOfRows(), 4.0d);
        check("B on run 3 is NEW", run3.lines().get(1).trend(), PatternTrend.NEW);
        check("three patterns known on the template", run3.knownCount(), 3);

        check("A on run 4 is RESOLVED", trendOf(patterns.patternsOf(4, 2, null), 10), PatternTrend.RESOLVED);
        check("A on run 5 is ABSENT", trendOf(patterns.patternsOf(5, 2, null), 10), PatternTrend.ABSENT);
        check("A on run 6 is REAPPEARED", trendOf(patterns.patternsOf(6, 2, null), 10), PatternTrend.REAPPEARED);
        check("B on run 4 is DECREASING", trendOf(patterns.patternsOf(4, 2, null), 11), PatternTrend.DECREASING);
        check("the context template id finds nothing", patterns.patternsOf(3, 1, null).tracked(), false);

        PatternService.PatternHistory history = patterns.patternHistory(10);
        check("A has six points", history.points().size(), 6);
        check("A was detected on four runs", history.detectedRuns(), 4);
        check("A peaked on run 3", history.peak().runId(), 3);
        check("A's points open the context template", history.points().get(0).contextTemplateId(), 1);
        check("A's points carry their batch", history.points().get(5).batch().batchId(), 6);
        check("B continues C", patterns.patternHistory(11).linkedPattern().patternId(), 12);

        check("qualification writes one row", patterns.qualify(10, "FX rate source", "Ops", "  "), 1);
        PatternRow qualified = dao.findPattern(10);
        check("root cause stored", qualified.rootCause(), "FX rate source");
        check("owner stored", qualified.ownerName(), "Ops");
        check("blank ticket stored as null", qualified.ticketRef(), null);

        Map<String, Integer> removed = dao.deleteBatches(List.of(1));
        check("run 1's pattern stats removed", removed.get("ER_DASHBOARD_PATTERN_STAT"), 2);
        check("C, left with no history, removed", removed.get("ER_DASHBOARD_PATTERN"), 1);
        check("C is gone", dao.findPattern(12), null);
        check("A moved to the earliest run still counting it", dao.findPattern(10).firstSeenRun(), 2);
        check("A kept its qualification", dao.findPattern(10).rootCause(), "FX rate source");
        check("B's link to C cleared", dao.findPattern(11).linkedPatternId(), null);
        check("A now first counted on run 2 is NEW there",
                trendOf(patterns.patternsOf(2, 2, null), 10), PatternTrend.NEW);
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
        check("no pattern tables: not tracked", patterns.patternsOf(1, 1, null).tracked(), false);
        check("no pattern tables: no history", patterns.patternHistory(1), null);
        Map<String, Integer> removed = dao.deleteBatches(List.of(1));
        check("no pattern tables: the batch is still deleted", removed.get("ER_DASHBOARD_BATCH"), 1);
        check("no pattern tables: no pattern table touched",
                removed.containsKey("ER_DASHBOARD_PATTERN_STAT"), false);
    }

    // ---------------------------------------------------------------------------- helpers

    private static PatternTrend trendOf(PatternService.RunPatterns found, int patternId) {
        for (PatternService.PatternLine line : found.lines()) {
            if (line.pattern().patternId() == patternId) {
                return line.trend();
            }
        }
        return null;
    }

    private static void stat(Statement sql, int run, int pattern, long occurrences) throws SQLException {
        sql.execute("INSERT INTO ER_DASHBOARD_PATTERN_STAT (RUN_ID, PATTERN_ID, OCCURRENCES, ABOVE_THRESHOLD) "
                + "VALUES (" + run + ", " + pattern + ", " + occurrences + ", " + (occurrences > 0 ? 1 : 0) + ")");
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

    /** As changelog-CREATE_ER_DASHBOARD_PATTERN and _PATTERN_STAT declare them. */
    private static void createPatternTables(Statement sql) throws SQLException {
        sql.execute("CREATE TABLE ER_DASHBOARD_PATTERN (PATTERN_ID INT PRIMARY KEY, "
                + "TEMPLATE_ID INT NOT NULL REFERENCES ER_DASHBOARD_TEMPLATE(TEMPLATE_ID), "
                + "SIGNATURE VARCHAR(64) NOT NULL, DESCRIPTION VARCHAR(1000), "
                + "FIRST_SEEN_RUN INT NOT NULL REFERENCES ER_DASHBOARD_RUN(RUN_ID), "
                + "LINKED_PATTERN_ID INT REFERENCES ER_DASHBOARD_PATTERN(PATTERN_ID), "
                + "ROOT_CAUSE VARCHAR(2000), OWNER_NAME VARCHAR(500), TICKET_REF VARCHAR(250))");
        sql.execute("CREATE UNIQUE INDEX UK_ER_DASHBOARD_PATTERN__SIGNATURE "
                + "ON ER_DASHBOARD_PATTERN (TEMPLATE_ID, SIGNATURE)");
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
