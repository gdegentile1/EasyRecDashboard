package com.finboxsolutions.easyrec.dashboard.model;

/**
 * One row of ER_DASHBOARD_PATTERN: a pattern identity, (TEMPLATE_ID, SIGNATURE), and what an
 * operator has said about it.
 *
 * <p>TEMPLATE_ID is the one the statistics are filed under - the engine resolves it with the
 * same call as ER_DASHBOARD_STAT_ROWS - so it is a reconciliation's {@code statsTemplateId},
 * not the id its context row carries. See {@code TemplateIds}.
 *
 * <p>ROOT_CAUSE, OWNER_NAME and TICKET_REF are never written by EasyRec; the dashboard is
 * where they are set.
 */
public record PatternRow(
        int patternId,
        int templateId,
        String signature,
        String description,
        Integer firstSeenRun,
        Integer linkedPatternId,
        String rootCause,
        String ownerName,
        String ticketRef) {

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

    /** True once an operator has recorded anything about the pattern. */
    public boolean isQualified() {
        return isSet(rootCause) || isSet(ownerName) || isSet(ticketRef);
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
