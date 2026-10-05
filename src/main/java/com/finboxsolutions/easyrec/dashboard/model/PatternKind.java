package com.finboxsolutions.easyrec.dashboard.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The column and the type of a pattern - read from its DESCRIPTION, as a stopgap.
 *
 * <p>ER_DASHBOARD_PATTERN does not store either as a value of its own yet: both only exist
 * inside the display text {@code <column> / <type label> / <parameter key>}. EasyRec is to add
 * COLUMN_NAME and PATTERN_TYPE columns; until then this class is the only place the dashboard
 * reads anything out of a description, so that the day those columns arrive, {@link #of} is
 * the one method to rewrite. Nothing else may parse a description.
 *
 * <p>The read is anchored on the type labels, which the engine fixes ({@code PatternType}),
 * rather than on the first {@code " / "}: the parameter key holds raw values, and a value may
 * contain the separator. A description whose type label is not recognised - an engine with a
 * type this list lacks - yields {@link #UNKNOWN}, never a guess.
 */
public record PatternKind(String column, String typeName, String typeLabel) {

    /** A description that could not be read. Not unexplained, no rule name. */
    public static final PatternKind UNKNOWN = new PatternKind(null, null, null);

    /** What {@code PatternFindingsAdapter} writes in place of a blank column name. */
    private static final String ROW_LEVEL_LABEL = "(row)";

    private static final String SEPARATOR = " / ";

    /** {@code PatternType} constant names by label, in the engine's declaration order. */
    private static final Map<String, String> TYPES_BY_LABEL = typesByLabel();

    /**
     * The types {@code PatternRcaConverter} can draft a rule for. Unexplained, structure and
     * schema findings explain no break by a condition, so they get no rule.
     */
    private static final Set<String> RULE_TYPES = Set.of(
            "SINGLE_SUBSTITUTION", "VALUE_MAPPING", "NUMERIC_FORMATTING", "CASE_CHANGE",
            "WHITESPACE", "PRECISION_LOSS", "COLLAPSE_TO_CONSTANT", "EXPANSION", "SIGN_FLIP",
            "CONSTANT_FACTOR", "CONSTANT_OFFSET", "DATE_SHIFT");

    /** The engine's {@code PatternType.UNSTRUCTURED}, labelled "No rule found". */
    private static final String UNEXPLAINED_TYPE = "UNSTRUCTURED";

    private static final String RULE_PREFIX = "Pattern_";

    /** How many characters of the signature the engine puts at the end of a rule name. */
    private static final int RULE_HASH_LENGTH = 8;

    /** The kind of {@code pattern}, or {@link #UNKNOWN} when its description cannot be read. */
    public static PatternKind of(PatternRow pattern) {
        return pattern == null ? UNKNOWN : of(pattern.description());
    }

    /** The kind described by {@code description}. See the class comment before using it. */
    public static PatternKind of(String description) {
        if (description == null || description.isEmpty()) {
            return UNKNOWN;
        }
        int bestAt = -1;
        String bestLabel = null;
        for (String label : TYPES_BY_LABEL.keySet()) {
            String marker = SEPARATOR + label;
            int from = 0;
            while (true) {
                int at = description.indexOf(marker, from);
                if (at < 0) {
                    break;
                }
                int end = at + marker.length();
                // The label has to end the description or be followed by the key's separator,
                // so that "Constant factor" is not read out of "Constant factorial".
                if (end == description.length() || description.startsWith(SEPARATOR, end)) {
                    if (bestAt < 0 || at < bestAt) {
                        bestAt = at;
                        bestLabel = label;
                    }
                    break;
                }
                from = at + 1;
            }
        }
        if (bestAt < 0) {
            return UNKNOWN;
        }
        String column = description.substring(0, bestAt);
        return new PatternKind(ROW_LEVEL_LABEL.equals(column) ? null : column,
                TYPES_BY_LABEL.get(bestLabel), bestLabel);
    }

    /**
     * True for an unexplained pattern - the engine's "No rule found" - whose count is the
     * breaks left without a rule on its column.
     *
     * <p>The one test the dashboard makes for it. When ER_DASHBOARD_PATTERN gains its
     * PATTERN_TYPE column, this becomes a comparison with that column.
     */
    public static boolean isUnexplained(PatternRow pattern) {
        return UNEXPLAINED_TYPE.equals(of(pattern).typeName());
    }

    public boolean isKnown() {
        return typeName != null;
    }

    /** True for a type EasyRec can draft an RCA rule for, on a pattern that has a column. */
    public boolean hasRcaRule() {
        return typeName != null && RULE_TYPES.contains(typeName) && column != null
                && !column.isBlank();
    }

    /**
     * The name EasyRec gives the RCA rule it drafts from this pattern,
     * {@code Pattern_<COLUMN>_<TYPE>_<first 8 of SIGNATURE>}, or null for a pattern no rule is
     * drafted for.
     *
     * <p>The engine hashes the column, the type and the raw parameter key, which is exactly
     * what SIGNATURE is the hash of, so the first eight characters of SIGNATURE are the ones
     * it uses. Only Groovy rules keep this name; an Excel row carries none.
     */
    public String expectedRuleName(String signature) {
        if (!hasRcaRule() || signature == null || signature.length() < RULE_HASH_LENGTH) {
            return null;
        }
        return RULE_PREFIX + column.replaceAll("[^A-Za-z0-9_]", "_") + "_" + typeName + "_"
                + signature.substring(0, RULE_HASH_LENGTH);
    }

    private static Map<String, String> typesByLabel() {
        Map<String, String> types = new LinkedHashMap<>();
        types.put("Single substitution", "SINGLE_SUBSTITUTION");
        types.put("Value mapping table", "VALUE_MAPPING");
        types.put("Same number, different text", "NUMERIC_FORMATTING");
        types.put("Case harmonised", "CASE_CHANGE");
        types.put("Whitespace only", "WHITESPACE");
        types.put("Precision loss", "PRECISION_LOSS");
        types.put("Collapse to a constant", "COLLAPSE_TO_CONSTANT");
        types.put("One source value expanded", "EXPANSION");
        types.put("Sign convention flipped", "SIGN_FLIP");
        types.put("Constant factor", "CONSTANT_FACTOR");
        types.put("Constant offset", "CONSTANT_OFFSET");
        types.put("Constant date shift", "DATE_SHIFT");
        types.put("No rule found", UNEXPLAINED_TYPE);
        types.put("Rows missing on one side", "MISSING_ROWS");
        types.put("Key difference, not missing data", "KEY_DIFFERENCE");
        types.put("Rows duplicated in target", "DUPLICATE_ROWS");
        types.put("Column on one side only", "MISSING_COLUMN");
        types.put("Declared type differs", "TYPE_MISMATCH");
        types.put("Decimal scale narrowed", "SCALE_NARROWING");
        types.put("Text length narrowed", "LENGTH_NARROWING");
        return types;
    }
}
