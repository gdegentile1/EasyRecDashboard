package com.finboxsolutions.easyrec.dashboard.model;

/**
 * Which STATUS mapping a table uses.
 *
 * <p>EasyRec's STATUS codes do not mean the same thing in every table, and the two
 * mappings are exactly opposite. ER_DASHBOARD_BATCH and ER_DASHBOARD_RUN record how the
 * execution finished, so 1 is a clean run and 0 a failed one. ER_DASHBOARD_STAT_ROWS and
 * ER_DASHBOARD_RUN_CONTEXT record the outcome of a single reconciliation, and there 0
 * means "no breaks" and 1 means "breaks found".
 *
 * <p>Reading a code without saying which table it came from is therefore always a bug.
 * Every call site has to name its scope, which is what this enum forces.
 */
public enum StatusScope {

    /** ER_DASHBOARD_BATCH, ER_DASHBOARD_RUN. */
    EXECUTION(1, 0),

    /** ER_DASHBOARD_STAT_ROWS, ER_DASHBOARD_RUN_CONTEXT. */
    RECONCILIATION(0, 1);

    /** EasyRec writes -1 when the execution errored; that sentinel means the same everywhere. */
    public static final int ERROR_CODE = -1;

    private final int passedCode;
    private final int failedCode;

    StatusScope(int passedCode, int failedCode) {
        this.passedCode = passedCode;
        this.failedCode = failedCode;
    }

    public int passedCode() {
        return passedCode;
    }

    public int failedCode() {
        return failedCode;
    }

    /** Maps a raw STATUS code, tolerating null and any code outside the mapping. */
    public StatusLabel labelOf(Integer code) {
        if (code == null) {
            return StatusLabel.UNKNOWN;
        }
        if (code == passedCode) {
            return StatusLabel.PASSED;
        }
        if (code == failedCode) {
            return StatusLabel.FAILED;
        }
        if (code == ERROR_CODE) {
            return StatusLabel.ERROR;
        }
        return StatusLabel.UNKNOWN;
    }
}
