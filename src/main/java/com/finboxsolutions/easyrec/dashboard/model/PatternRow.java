package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_PATTERN: a pattern identity, (TEMPLATE_ID, SIGNATURE).
 *
 * <p>TEMPLATE_ID is the reconciliation template's, the one ER_DASHBOARD_STAT_ROWS and
 * ER_DASHBOARD_RUN_CONTEXT carry - never the project template ER_DASHBOARD_RUN points at. See
 * {@code TemplateIds}.
 *
 * <p>Nothing here says why the pattern exists. The table used to carry a root cause, an owner
 * and a ticket; EasyRec dropped them, because explaining a pattern now means writing an RCA
 * rule from it in EasyRec, and the rule writes its comment on every break of the pattern.
 * LINKED_PATTERN_ID is the one column the dashboard writes, and only on a user's request.
 *
 * <p>DESCRIPTION is display text, {@code <column> / <type label> / <parameter key>}, written
 * once. Reading the column or the type out of it is confined to {@code PatternKind}.
 */
public record PatternRow(
        int patternId,
        int templateId,
        String signature,
        String description,
        Integer firstSeenRun,
        Integer linkedPatternId) {

    /** The description, or the start of the signature for a pattern exported without one. */
    public String label() {
        if (description != null && !description.isBlank()) {
            return description;
        }
        if (signature == null || signature.isEmpty()) {
            return "Pattern " + patternId;
        }
        return "Pattern " + signature.substring(0, Math.min(12, signature.length()));
    }
}
