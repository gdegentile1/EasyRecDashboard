package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One template a signature was found on, and its count on a given run: a row of the engine's
 * {@code QUERY_ER_PATTERN_BY_SIGNATURE}.
 *
 * <p>The same signature under several templates is the same cause found on several
 * reconciliations, which is fixed once.
 *
 * @param occurrences the count on the run, or null when the run has no stat row for this
 *                    pattern - the run did not reconcile that template, or its export was
 *                    skipped there
 */
public record PatternSighting(PatternRow pattern, String templatePath, Long occurrences) {
}
