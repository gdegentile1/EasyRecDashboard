package com.finboxsolutions.easyrec.dashboard.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Small JDBC helpers shared by the dashboard queries.
 *
 * <p>Three things here are not incidental. The getters return boxed nulls instead of
 * {@code getInt}'s silent zero, because a null STATUS and a STATUS of 0 mean different
 * things in these tables. {@link #chunks(Collection)} splits an id list into batches
 * small enough for Oracle's 1000-element IN limit, which the dashboard reaches easily: a
 * single run in the sample data carries 29 context rows and 4 196 column-statistic rows.
 * And {@link #epochMillis} reads a temporal column by type rather than by text, which is
 * what makes a date reach the screen at all.
 */
public final class Sql {

    /** Oracle rejects an IN list longer than 1000; the margin leaves room for other binds. */
    private static final int MAX_IN_PARAMETERS = 900;

    private Sql() {
    }

    /** Splits ids into IN-sized chunks, preserving order. Empty input yields no chunks. */
    public static List<List<Integer>> chunks(Collection<Integer> ids) {
        List<Integer> all = new ArrayList<>(ids);
        List<List<Integer>> chunks = new ArrayList<>();
        for (int start = 0; start < all.size(); start += MAX_IN_PARAMETERS) {
            chunks.add(all.subList(start, Math.min(start + MAX_IN_PARAMETERS, all.size())));
        }
        return chunks;
    }

    /** A parenthesised list of {@code count} bind placeholders, e.g. {@code (?, ?, ?)}. */
    public static String placeholders(int count) {
        StringBuilder builder = new StringBuilder(count * 3 + 2);
        builder.append('(');
        for (int index = 0; index < count; index++) {
            builder.append(index == 0 ? "?" : ", ?");
        }
        return builder.append(')').toString();
    }

    public static Integer integer(ResultSet rows, String column) throws SQLException {
        int value = rows.getInt(column);
        return rows.wasNull() ? null : value;
    }

    public static Long bigint(ResultSet rows, String column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    /** A count column, with null read as zero: absent breaks are zero breaks. */
    public static long count(ResultSet rows, String column) throws SQLException {
        return rows.getLong(column);
    }

    public static Double decimal(ResultSet rows, String column) throws SQLException {
        double value = rows.getDouble(column);
        return rows.wasNull() ? null : value;
    }

    /**
     * A STATUS column, whichever type the deployment stores it as.
     *
     * <p>The shipped DDL declares STATUS differently across the dashboard tables - integer
     * in ER_DASHBOARD_RUN_CONTEXT, character elsewhere - so it is read as text and parsed,
     * and a non-numeric value is reported as absent rather than raising.
     */
    public static Integer status(ResultSet rows, String column) throws SQLException {
        String raw = rows.getString(column);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * The first of {@code columns} that yields an instant, as epoch milliseconds.
     *
     * <p>SYS_TIME and SYS_DATE are temporal columns, not text. {@code BatchDaoImpl} writes
     * them as {@code setTimestamp} and {@code setDate} of the same instant, and the shipped
     * DDL declares them {@code TIMESTAMP} and {@code date} in every dialect - so SYS_TIME is
     * the whole instant and SYS_DATE is that instant with the time thrown away. Callers ask
     * for SYS_TIME first for that reason.
     *
     * <p>Read by type rather than as a string. Reading these as text and parsing them as
     * epoch milliseconds - which is what this did - returns null for every row on every
     * database, which leaves every batch without a date and every date filter excluding
     * everything: an empty home screen and a range filter that finds nothing.
     *
     * <p>The epoch-string branch is kept for a deployment whose columns are still character,
     * and the {@code getTimestamp} fallback for a driver that hands its own class back from
     * {@code getObject} - Oracle's {@code oracle.sql.TIMESTAMP} being the one to expect.
     */
    public static Long epochMillis(ResultSet rows, String... columns) throws SQLException {
        for (String column : columns) {
            Long millis = instantOf(rows, column);
            if (millis != null) {
                return millis;
            }
        }
        return null;
    }

    private static Long instantOf(ResultSet rows, String column) throws SQLException {
        Object value = rows.getObject(column);
        if (value == null) {
            return null;
        }
        // java.sql.Date, java.sql.Time and java.sql.Timestamp are all java.util.Date.
        if (value instanceof java.util.Date date) {
            return date.getTime();
        }
        if (value instanceof java.time.OffsetDateTime offset) {
            return offset.toInstant().toEpochMilli();
        }
        if (value instanceof java.time.LocalDateTime local) {
            return local.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof java.time.LocalDate local) {
            return local.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        Long parsed = parseEpoch(value.toString());
        return parsed != null ? parsed : fromTimestamp(rows, column);
    }

    /** A last resort for a driver whose {@code getObject} returns a class of its own. */
    private static Long fromTimestamp(ResultSet rows, String column) {
        try {
            java.sql.Timestamp stamp = rows.getTimestamp(column);
            return stamp == null ? null : stamp.getTime();
        } catch (SQLException ignored) {
            // Not a temporal column, and not a number written as text either.
            return null;
        }
    }

    private static Long parseEpoch(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
