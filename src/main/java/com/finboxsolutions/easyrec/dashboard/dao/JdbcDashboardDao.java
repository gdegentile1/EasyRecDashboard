package com.finboxsolutions.easyrec.dashboard.dao;

import com.finboxsolutions.easyrec.dashboard.model.BatchRow;
import com.finboxsolutions.easyrec.dashboard.model.ColumnStats;
import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.model.RowStats;
import com.finboxsolutions.easyrec.dashboard.model.RunContextRow;
import com.finboxsolutions.easyrec.dashboard.model.RunRow;
import com.finboxsolutions.easyrec.dashboard.model.TemplateRow;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plain-JDBC {@link DashboardDao}, written against the SQL all of EasyRec's supported
 * back-ends accept.
 *
 * <p>Deliberately no vendor-specific syntax: the same statements have to run on Oracle,
 * H2 and DuckDB. That rules out doing anything numeric with SYS_DATE, which every
 * deployment stores as an epoch-millisecond string in a character column - casting it in
 * SQL would fail the moment one row holds something unparseable. Date filtering therefore
 * happens in the service layer, over ER_DASHBOARD_BATCH, which is the small table.
 */
public class JdbcDashboardDao implements DashboardDao {

    /** Thrown instead of a checked SQLException, so the UI layer catches one thing. */
    public static class DashboardDataException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DashboardDataException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final String BATCH_COLUMNS =
            "BATCH_ID, VERSION, RUN_MODE, USER_NAME, STATUS, SYS_DATE, SYS_TIME, "
            + "DURATION_MILLISEC, DESCRIPTION, PURGE_STATUS";

    private static final String RUN_COLUMNS =
            "RUN_ID, BATCH_ID, REC_TYPE, TEMPLATE_ID, PROJECT_PATH, SOURCE_ALIAS, "
            + "TARGET_ALIAS, SOURCE_LABEL, TARGET_LABEL, STATUS, SYS_DATE, SYS_TIME, "
            + "DURATION_MILLISEC, DESCRIPTION, PURGE_STATUS";

    private static final String CONTEXT_COLUMNS =
            "RUN_ID, POSITION, TEMPLATE_ID, SOURCE_ALIAS, TARGET_ALIAS, SOURCE_LABEL, "
            + "TARGET_LABEL, NAME, CATEGORY1, CATEGORY2, CATEGORY3, PRIORITY, USER_NAME, "
            + "USER_EMAIL, GROUP_NAME, GROUP_EMAIL, STATUS, DUE_DATE, DESCRIPTION";

    private static final String ROW_STAT_COLUMNS =
            "RUN_ID, TEMPLATE_ID, STATUS, ROWS_SOURCE, ROWS_TARGET, MISSING_SOURCE, "
            + "MISSING_TARGET, UNMATCHED, MATCHED, FORCE_MATCHED, NB_COMMENTS, FILTER, "
            + "MATCH_TYPE, PIVOT_BREAKDOWN";

    private static final String COL_STAT_COLUMNS =
            "RUN_ID, TEMPLATE_ID, COL_LABEL, COL_CLASS, COL_TOLERANCE, COL_MATCH_PCT, "
            + "COL_NB_UNMATCH, COL_NB_EXACT_MATCH, COL_NB_TOLERANCE_MATCH, COL_NB_FORCE_MATCH, "
            + "COL_IMPACT, COL_IMPACT_ABS, COL_AVERAGE, COL_STD_DEVIATION, COL_MIN_DIFF_ABS, "
            + "COL_MAX_DIFF_ABS, COL_MIN_DIFF_PCT, COL_MAX_DIFF_PCT";

    /**
     * Which ER_DASHBOARD_PIVOT column backs each measure. Kept as a map so the SELECT list,
     * the row reader and the tree all stay driven by {@link PivotMetric}; the two totals it
     * declares have no column of their own and are computed at load.
     */
    private static final Map<PivotMetric, String> PIVOT_COLUMNS = pivotColumns();

    /** The operator-maintained context columns, mapped from the field names the UI uses. */
    private static final Map<String, String> CONTEXT_WRITABLE_COLUMNS = Map.ofEntries(
            Map.entry("category1", "CATEGORY1"),
            Map.entry("category2", "CATEGORY2"),
            Map.entry("category3", "CATEGORY3"),
            Map.entry("priority", "PRIORITY"),
            Map.entry("userName", "USER_NAME"),
            Map.entry("userEmail", "USER_EMAIL"),
            Map.entry("groupName", "GROUP_NAME"),
            Map.entry("groupEmail", "GROUP_EMAIL"),
            Map.entry("statusCode", "STATUS"),
            Map.entry("dueDate", "DUE_DATE"),
            Map.entry("description", "DESCRIPTION"));

    /** Columns of ER_DASHBOARD_RUN_CONTEXT a filter dropdown may read. */
    private static final Set<String> CONTEXT_FILTER_COLUMNS =
            Set.of("NAME", "SOURCE_LABEL", "TARGET_LABEL", "CATEGORY1", "CATEGORY2", "CATEGORY3");

    private final DataSource dataSource;

    public JdbcDashboardDao(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    // ---------------------------------------------------------------- batches and runs

    @Override
    public List<BatchRow> findAllBatches() {
        return query("SELECT " + BATCH_COLUMNS + " FROM ER_DASHBOARD_BATCH ORDER BY BATCH_ID DESC",
                statement -> { }, JdbcDashboardDao::readBatch);
    }

    @Override
    public BatchRow findBatch(int batchId) {
        List<BatchRow> found = query(
                "SELECT " + BATCH_COLUMNS + " FROM ER_DASHBOARD_BATCH WHERE BATCH_ID = ?",
                statement -> statement.setInt(1, batchId), JdbcDashboardDao::readBatch);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public Map<Integer, List<RunRow>> findRunsByBatch(Collection<Integer> batchIds) {
        Map<Integer, List<RunRow>> byBatch = new LinkedHashMap<>();
        for (Integer batchId : batchIds) {
            byBatch.put(batchId, new ArrayList<>());
        }
        for (RunRow run : queryByIds(
                "SELECT " + RUN_COLUMNS + " FROM ER_DASHBOARD_RUN WHERE BATCH_ID IN ",
                " ORDER BY RUN_ID", batchIds, JdbcDashboardDao::readRun)) {
            byBatch.computeIfAbsent(run.batchId(), key -> new ArrayList<>()).add(run);
        }
        return byBatch;
    }

    @Override
    public RunRow findRun(int runId) {
        List<RunRow> found = query(
                "SELECT " + RUN_COLUMNS + " FROM ER_DASHBOARD_RUN WHERE RUN_ID = ?",
                statement -> statement.setInt(1, runId), JdbcDashboardDao::readRun);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public List<RunContextRow> findRunContexts(Collection<Integer> runIds) {
        return queryByIds(
                "SELECT " + CONTEXT_COLUMNS + " FROM ER_DASHBOARD_RUN_CONTEXT WHERE RUN_ID IN ",
                " ORDER BY RUN_ID, TEMPLATE_ID", runIds, JdbcDashboardDao::readContext);
    }

    // ------------------------------------------------------------------------ statistics

    @Override
    public List<RowStats> findRowStats(Collection<Integer> runIds) {
        return queryByIds(
                "SELECT " + ROW_STAT_COLUMNS + " FROM ER_DASHBOARD_STAT_ROWS WHERE RUN_ID IN ",
                " ORDER BY RUN_ID, TEMPLATE_ID", runIds, JdbcDashboardDao::readRowStats);
    }

    @Override
    public List<ColumnStats> findColumnStats(int runId, int templateId) {
        return query(
                "SELECT " + COL_STAT_COLUMNS + " FROM ER_DASHBOARD_STAT_COLS "
                        + "WHERE RUN_ID = ? AND TEMPLATE_ID = ? ORDER BY COL_NB_UNMATCH DESC",
                statement -> {
                    statement.setInt(1, runId);
                    statement.setInt(2, templateId);
                }, JdbcDashboardDao::readColumnStats);
    }

    @Override
    public Map<Integer, List<ColumnStats>> findColumnStats(Map<Integer, Integer> templateIdByRun) {
        Map<Integer, List<ColumnStats>> byRun = new LinkedHashMap<>();
        if (templateIdByRun.isEmpty()) {
            return byRun;
        }
        // One statement per pair would be an N+1 across a comparison of six batches, so the
        // pairs are ORed into a single predicate and the result grouped back in memory.
        StringBuilder sql = new StringBuilder("SELECT ").append(COL_STAT_COLUMNS)
                .append(" FROM ER_DASHBOARD_STAT_COLS WHERE ");
        for (int index = 0; index < templateIdByRun.size(); index++) {
            sql.append(index == 0 ? "" : " OR ").append("(RUN_ID = ? AND TEMPLATE_ID = ?)");
        }
        List<ColumnStats> found = query(sql.toString(), statement -> {
            int parameter = 1;
            for (Map.Entry<Integer, Integer> pair : templateIdByRun.entrySet()) {
                statement.setInt(parameter++, pair.getKey());
                statement.setInt(parameter++, pair.getValue());
            }
        }, JdbcDashboardDao::readColumnStats);
        for (ColumnStats stats : found) {
            byRun.computeIfAbsent(stats.runId(), key -> new ArrayList<>()).add(stats);
        }
        return byRun;
    }

    @Override
    public List<PivotRow> findPivots(int runId, int templateId) {
        StringBuilder sql = new StringBuilder("SELECT RUN_ID, TEMPLATE_ID, PIVOT_KEYS, PIVOT_LEVEL");
        for (int index = 0; index < PivotRow.MAX_KEYS; index++) {
            sql.append(", PIVOT_KEY").append(index);
        }
        for (String column : PIVOT_COLUMNS.values()) {
            sql.append(", ").append(column);
        }
        sql.append(" FROM ER_DASHBOARD_PIVOT WHERE RUN_ID = ? AND TEMPLATE_ID = ? ORDER BY PIVOT_KEYS");
        return query(sql.toString(), statement -> {
            statement.setInt(1, runId);
            statement.setInt(2, templateId);
        }, JdbcDashboardDao::readPivot);
    }

    // ------------------------------------------------------------------------- templates

    @Override
    public Map<Integer, TemplateRow> findTemplates(Collection<Integer> templateIds) {
        Map<Integer, TemplateRow> byId = new LinkedHashMap<>();
        for (TemplateRow template : queryByIds(
                "SELECT TEMPLATE_ID, FULL_PATH, KEY_COLUMNS, IGNORE_COLUMNS "
                        + "FROM ER_DASHBOARD_TEMPLATE WHERE TEMPLATE_ID IN ",
                "", templateIds, JdbcDashboardDao::readTemplate)) {
            byId.put(template.templateId(), template);
        }
        return byId;
    }

    @Override
    public TemplateRow findTemplate(int templateId) {
        return findTemplates(List.of(templateId)).get(templateId);
    }

    @Override
    public List<Integer> findTemplateIdsByPathFragment(String fragment) {
        String pattern = "%" + fragment.toLowerCase(java.util.Locale.ROOT) + "%";
        List<TemplateRow> found = query(
                "SELECT TEMPLATE_ID, FULL_PATH, KEY_COLUMNS, IGNORE_COLUMNS "
                        + "FROM ER_DASHBOARD_TEMPLATE WHERE LOWER(FULL_PATH) LIKE ?",
                statement -> statement.setString(1, pattern), JdbcDashboardDao::readTemplate);
        List<Integer> ids = new ArrayList<>(found.size());
        for (TemplateRow template : found) {
            ids.add(template.templateId());
        }
        return ids;
    }

    @Override
    public Map<Integer, String> findTemplateIdsByPaths(Collection<String> paths) {
        Map<Integer, String> byId = new LinkedHashMap<>();
        List<String> all = new ArrayList<>(new LinkedHashSet<>(paths));
        for (int start = 0; start < all.size(); start += 900) {
            List<String> chunk = all.subList(start, Math.min(start + 900, all.size()));
            List<TemplateRow> found = query(
                    "SELECT TEMPLATE_ID, FULL_PATH, KEY_COLUMNS, IGNORE_COLUMNS "
                            + "FROM ER_DASHBOARD_TEMPLATE WHERE FULL_PATH IN "
                            + Sql.placeholders(chunk.size()),
                    statement -> {
                        for (int index = 0; index < chunk.size(); index++) {
                            statement.setString(index + 1, chunk.get(index));
                        }
                    }, JdbcDashboardDao::readTemplate);
            for (TemplateRow template : found) {
                byId.put(template.templateId(), template.fullPath());
            }
        }
        return byId;
    }

    // --------------------------------------------------------------------- filter options

    @Override
    public List<String> findBatchUserNames() {
        return distinctStrings("SELECT DISTINCT USER_NAME FROM ER_DASHBOARD_BATCH");
    }

    @Override
    public List<String> findContextValues(String columnName) {
        String column = columnName.toUpperCase(java.util.Locale.ROOT);
        if (!CONTEXT_FILTER_COLUMNS.contains(column)) {
            // The column name goes into the statement text, so it is checked against a fixed
            // set rather than bound: an unchecked name here would be an injection point.
            throw new IllegalArgumentException("Not a filterable context column: " + columnName);
        }
        return distinctStrings("SELECT DISTINCT " + column + " FROM ER_DASHBOARD_RUN_CONTEXT");
    }

    private List<String> distinctStrings(String sql) {
        List<String> values = query(sql, statement -> { }, rows -> rows.getString(1));
        Set<String> distinct = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                distinct.add(value.trim());
            }
        }
        List<String> sorted = new ArrayList<>(distinct);
        sorted.sort(String.CASE_INSENSITIVE_ORDER);
        return sorted;
    }

    // ------------------------------------------------------------------------------ writes

    @Override
    public int updateBatchDescription(int batchId, String description) {
        return update("UPDATE ER_DASHBOARD_BATCH SET DESCRIPTION = ? WHERE BATCH_ID = ?",
                statement -> {
                    statement.setString(1, description);
                    statement.setInt(2, batchId);
                });
    }

    @Override
    public int updateRunProjectPath(Collection<Integer> runIds, String projectPath) {
        int total = 0;
        for (List<Integer> chunk : Sql.chunks(runIds)) {
            String sql = "UPDATE ER_DASHBOARD_RUN SET PROJECT_PATH = ? WHERE RUN_ID IN "
                    + Sql.placeholders(chunk.size());
            total += update(sql, statement -> {
                statement.setString(1, projectPath);
                for (int index = 0; index < chunk.size(); index++) {
                    statement.setInt(index + 2, chunk.get(index));
                }
            });
        }
        return total;
    }

    @Override
    public int updateRunContext(int runId, int templateId, Map<String, Object> values) {
        List<String> assignments = new ArrayList<>();
        List<Object> bound = new ArrayList<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String column = CONTEXT_WRITABLE_COLUMNS.get(entry.getKey());
            if (column == null) {
                throw new IllegalArgumentException("Not a writable context field: " + entry.getKey());
            }
            assignments.add(column + " = ?");
            bound.add(entry.getValue());
        }
        if (assignments.isEmpty()) {
            return 0;
        }
        String sql = "UPDATE ER_DASHBOARD_RUN_CONTEXT SET " + String.join(", ", assignments)
                + " WHERE RUN_ID = ? AND TEMPLATE_ID = ?";
        return update(sql, statement -> {
            int parameter = 1;
            for (Object value : bound) {
                if (value instanceof java.time.LocalDate date) {
                    statement.setDate(parameter++, Date.valueOf(date));
                } else {
                    statement.setObject(parameter++, value);
                }
            }
            statement.setInt(parameter++, runId);
            statement.setInt(parameter, templateId);
        });
    }

    @Override
    public Map<String, Integer> deleteBatches(Collection<Integer> batchIds) {
        Map<String, Integer> removed = new LinkedHashMap<>();
        if (batchIds.isEmpty()) {
            return removed;
        }
        Map<Integer, List<RunRow>> runsByBatch = findRunsByBatch(batchIds);
        List<Integer> runIds = new ArrayList<>();
        for (List<RunRow> runs : runsByBatch.values()) {
            for (RunRow run : runs) {
                runIds.add(run.runId());
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // Children first: the production schema enforces these constraints even
                // though a file-backed development database may not.
                for (String table : List.of("ER_DASHBOARD_PIVOT", "ER_DASHBOARD_STAT_COLS",
                        "ER_DASHBOARD_STAT_ROWS", "ER_DASHBOARD_RUN_CONTEXT")) {
                    removed.merge(table, deleteByIds(connection, table, "RUN_ID", runIds), Integer::sum);
                }
                removed.merge("ER_DASHBOARD_RUN",
                        deleteByIds(connection, "ER_DASHBOARD_RUN", "RUN_ID", runIds), Integer::sum);
                removed.merge("ER_DASHBOARD_BATCH",
                        deleteByIds(connection, "ER_DASHBOARD_BATCH", "BATCH_ID", batchIds), Integer::sum);
                connection.commit();
            } catch (SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException failure) {
            throw new DashboardDataException("Could not delete batches " + batchIds, failure);
        }
        return removed;
    }

    private int deleteByIds(Connection connection, String table, String column,
                            Collection<Integer> ids) throws SQLException {
        int total = 0;
        for (List<Integer> chunk : Sql.chunks(ids)) {
            String sql = "DELETE FROM " + table + " WHERE " + column + " IN "
                    + Sql.placeholders(chunk.size());
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < chunk.size(); index++) {
                    statement.setInt(index + 1, chunk.get(index));
                }
                total += statement.executeUpdate();
            }
        }
        return total;
    }

    // ------------------------------------------------------------------------- plumbing

    /** Binds the parameters of a prepared statement. */
    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    /** Maps the current row of a result set. */
    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rows) throws SQLException;
    }

    private <T> List<T> query(String sql, Binder binder, RowReader<T> reader) {
        List<T> results = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    results.add(reader.read(rows));
                }
            }
        } catch (SQLException failure) {
            throw new DashboardDataException("Query failed: " + sql, failure);
        }
        return results;
    }

    /** Runs {@code prefix + (ids) + suffix} once per IN-sized chunk and concatenates. */
    private <T> List<T> queryByIds(String prefix, String suffix, Collection<Integer> ids,
                                   RowReader<T> reader) {
        List<T> results = new ArrayList<>();
        for (List<Integer> chunk : Sql.chunks(new LinkedHashSet<>(ids))) {
            results.addAll(query(prefix + Sql.placeholders(chunk.size()) + suffix, statement -> {
                for (int index = 0; index < chunk.size(); index++) {
                    statement.setInt(index + 1, chunk.get(index));
                }
            }, reader));
        }
        return results;
    }

    private int update(String sql, Binder binder) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            return statement.executeUpdate();
        } catch (SQLException failure) {
            throw new DashboardDataException("Update failed: " + sql, failure);
        }
    }

    // ------------------------------------------------------------------------ row readers

    private static TemplateRow readTemplate(ResultSet rows) throws SQLException {
        return new TemplateRow(
                rows.getInt("TEMPLATE_ID"),
                rows.getString("FULL_PATH"),
                rows.getString("KEY_COLUMNS"),
                rows.getString("IGNORE_COLUMNS"));
    }

    private static BatchRow readBatch(ResultSet rows) throws SQLException {
        return new BatchRow(
                rows.getInt("BATCH_ID"),
                rows.getString("VERSION"),
                rows.getString("RUN_MODE"),
                rows.getString("USER_NAME"),
                Sql.status(rows, "STATUS"),
                Sql.epochMillis(rows, "SYS_TIME", "SYS_DATE"),
                Sql.bigint(rows, "DURATION_MILLISEC"),
                rows.getString("DESCRIPTION"),
                rows.getInt("PURGE_STATUS"));
    }

    private static RunRow readRun(ResultSet rows) throws SQLException {
        return new RunRow(
                rows.getInt("RUN_ID"),
                rows.getInt("BATCH_ID"),
                rows.getString("REC_TYPE"),
                Sql.integer(rows, "TEMPLATE_ID"),
                rows.getString("PROJECT_PATH"),
                rows.getString("SOURCE_ALIAS"),
                rows.getString("TARGET_ALIAS"),
                rows.getString("SOURCE_LABEL"),
                rows.getString("TARGET_LABEL"),
                Sql.status(rows, "STATUS"),
                Sql.epochMillis(rows, "SYS_TIME", "SYS_DATE"),
                Sql.bigint(rows, "DURATION_MILLISEC"),
                rows.getString("DESCRIPTION"),
                rows.getInt("PURGE_STATUS"));
    }

    private static RunContextRow readContext(ResultSet rows) throws SQLException {
        Date dueDate = rows.getDate("DUE_DATE");
        return new RunContextRow(
                rows.getInt("RUN_ID"),
                Sql.integer(rows, "POSITION"),
                Sql.integer(rows, "TEMPLATE_ID"),
                rows.getString("SOURCE_ALIAS"),
                rows.getString("TARGET_ALIAS"),
                rows.getString("SOURCE_LABEL"),
                rows.getString("TARGET_LABEL"),
                rows.getString("NAME"),
                rows.getString("CATEGORY1"),
                rows.getString("CATEGORY2"),
                rows.getString("CATEGORY3"),
                rows.getString("PRIORITY"),
                rows.getString("USER_NAME"),
                rows.getString("USER_EMAIL"),
                rows.getString("GROUP_NAME"),
                rows.getString("GROUP_EMAIL"),
                Sql.status(rows, "STATUS"),
                dueDate == null ? null : dueDate.toLocalDate(),
                rows.getString("DESCRIPTION"));
    }

    private static RowStats readRowStats(ResultSet rows) throws SQLException {
        return new RowStats(
                rows.getInt("RUN_ID"),
                Sql.integer(rows, "TEMPLATE_ID"),
                Sql.status(rows, "STATUS"),
                Sql.count(rows, "ROWS_SOURCE"),
                Sql.count(rows, "ROWS_TARGET"),
                Sql.count(rows, "MISSING_SOURCE"),
                Sql.count(rows, "MISSING_TARGET"),
                Sql.count(rows, "UNMATCHED"),
                Sql.count(rows, "MATCHED"),
                Sql.count(rows, "FORCE_MATCHED"),
                Sql.count(rows, "NB_COMMENTS"),
                rows.getString("FILTER"),
                rows.getString("MATCH_TYPE"),
                rows.getString("PIVOT_BREAKDOWN"));
    }

    private static ColumnStats readColumnStats(ResultSet rows) throws SQLException {
        return new ColumnStats(
                rows.getInt("RUN_ID"),
                Sql.integer(rows, "TEMPLATE_ID"),
                rows.getString("COL_LABEL"),
                rows.getString("COL_CLASS"),
                rows.getString("COL_TOLERANCE"),
                Sql.decimal(rows, "COL_MATCH_PCT"),
                Sql.count(rows, "COL_NB_UNMATCH"),
                Sql.count(rows, "COL_NB_EXACT_MATCH"),
                Sql.count(rows, "COL_NB_TOLERANCE_MATCH"),
                Sql.count(rows, "COL_NB_FORCE_MATCH"),
                Sql.decimal(rows, "COL_IMPACT"),
                Sql.decimal(rows, "COL_IMPACT_ABS"),
                Sql.decimal(rows, "COL_AVERAGE"),
                Sql.decimal(rows, "COL_STD_DEVIATION"),
                Sql.decimal(rows, "COL_MIN_DIFF_ABS"),
                Sql.decimal(rows, "COL_MAX_DIFF_ABS"),
                Sql.decimal(rows, "COL_MIN_DIFF_PCT"),
                Sql.decimal(rows, "COL_MAX_DIFF_PCT"));
    }

    private static PivotRow readPivot(ResultSet rows) throws SQLException {
        List<String> keys = new ArrayList<>(PivotRow.MAX_KEYS);
        for (int index = 0; index < PivotRow.MAX_KEYS; index++) {
            String key = rows.getString("PIVOT_KEY" + index);
            keys.add(key == null ? "" : key);
        }
        EnumMap<PivotMetric, Double> stored = new EnumMap<>(PivotMetric.class);
        for (Map.Entry<PivotMetric, String> entry : PIVOT_COLUMNS.entrySet()) {
            Double value = Sql.decimal(rows, entry.getValue());
            stored.put(entry.getKey(), value == null ? 0.0d : value);
        }
        return new PivotRow(
                rows.getInt("RUN_ID"),
                Sql.integer(rows, "TEMPLATE_ID"),
                rows.getString("PIVOT_KEYS"),
                Sql.integer(rows, "PIVOT_LEVEL"),
                keys,
                PivotRow.withTotals(stored));
    }

    private static Map<PivotMetric, String> pivotColumns() {
        EnumMap<PivotMetric, String> columns = new EnumMap<>(PivotMetric.class);
        columns.put(PivotMetric.MATCH_COUNT, "MATCH_COUNT");
        columns.put(PivotMetric.MATCH_SUM_S, "MATCH_SUM_S");
        columns.put(PivotMetric.MATCH_SUM_T, "MATCH_SUM_T");
        columns.put(PivotMetric.MISSING_SRC_COUNT, "MISSING_SRC_COUNT");
        columns.put(PivotMetric.MISSING_SRC_SUM_S, "MISSING_SRC_SUM_S");
        columns.put(PivotMetric.MISSING_SRC_SUM_T, "MISSING_SRC_SUM_T");
        columns.put(PivotMetric.MISSING_SRC_IMPACT, "MISSING_SRC_IMPACT");
        columns.put(PivotMetric.MISSING_SRC_IMPACT_ABS, "MISSING_SRC_IMPACT_ABS");
        columns.put(PivotMetric.MISSING_TRG_COUNT, "MISSING_TRG_COUNT");
        columns.put(PivotMetric.MISSING_TRG_SUM_S, "MISSING_TRG_SUM_S");
        columns.put(PivotMetric.MISSING_TRG_SUM_T, "MISSING_TRG_SUM_T");
        columns.put(PivotMetric.MISSING_TRG_IMPACT, "MISSING_TRG_IMPACT");
        columns.put(PivotMetric.MISSING_TRG_IMPACT_ABS, "MISSING_TRG_IMPACT_ABS");
        columns.put(PivotMetric.UNMATCH_COUNT, "UNMATCH_COUNT");
        columns.put(PivotMetric.UNMATCH_SUM_S, "UNMATCH_SUM_S");
        columns.put(PivotMetric.UNMATCH_SUM_T, "UNMATCH_SUM_T");
        columns.put(PivotMetric.UNMATCH_IMPACT, "UNMATCH_IMPACT");
        columns.put(PivotMetric.UNMATCH_IMPACT_ABS, "UNMATCH_IMPACT_ABS");
        columns.put(PivotMetric.UNMATCH_IMPACT_PCT, "UNMATCH_IMPACT_PCT");
        return columns;
    }
}
