package com.finboxsolutions.easyrec.dashboard.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * One row of ER_DASHBOARD_BATCH: a batch execution grouping reconciliation runs.
 *
 * <p>SYS_DATE and SYS_TIME are stored as epoch-millisecond strings rather than as date
 * columns; the DAO parses them and this record carries the resolved value, or null when
 * neither column held a usable number.
 */
public record BatchRow(
        int batchId,
        String version,
        String runMode,
        String userName,
        Integer statusCode,
        Long epochMillis,
        Long durationMillis,
        String description,
        int purgeStatus) {

    public StatusLabel status() {
        return StatusScope.EXECUTION.labelOf(statusCode);
    }

    /** The batch's timestamp in the local zone, or null when it has none. */
    public LocalDateTime when() {
        if (epochMillis == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }
}
