package com.finboxsolutions.easyrec.dashboard.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Small JDBC helpers shared by the dashboard queries.
 *
 * <p>Two things here are not incidental. The getters return boxed nulls instead of
 * {@code getInt}'s silent zero, because a null STATUS and a STATUS of 0 mean different
 * things in these tables. And {@link #chunks(Collection)} splits an id list into batches
 * small enough for Oracle's 1000-element IN limit, which the dashboard reaches easily: a
 * single run in the sample data carries 29 context rows and 4 196 column-statistic rows.
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
     * SYS_DATE / SYS_TIME as epoch milliseconds.
     *
     * <p>Both columns hold an epoch-millisecond string and either may be blank, so the
     * first usable one wins, as EasyRec's own readers do.
     */
    public static Long epochMillis(ResultSet rows, String first, String second) throws SQLException {
        Long parsed = parseEpoch(rows.getString(first));
        return parsed != null ? parsed : parseEpoch(rows.getString(second));
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
