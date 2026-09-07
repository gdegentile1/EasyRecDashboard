package com.finboxsolutions.easyrec.dashboard.service;

/**
 * Normalisation of the free-text columns EasyRec writes into ER_DASHBOARD_RUN_CONTEXT and
 * ER_DASHBOARD_RUN.
 *
 * <p>EasyRec fills unused columns with the literal {@code <Undefined>} rather than leaving
 * them null, so a filter dropdown built straight off DISTINCT offers it as if it were a
 * real project name. Both forms mean "no value" and both are cleaned away here.
 */
public final class ContextValues {

    private static final String UNDEFINED = "<Undefined>";

    private ContextValues() {
    }

    /** The value, or null when it is blank or EasyRec's placeholder. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || UNDEFINED.equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    /** {@link #clean} with a dash for the empty case, ready for a table cell. */
    public static String display(String value) {
        String cleaned = clean(value);
        return cleaned == null ? "-" : cleaned;
    }
}
