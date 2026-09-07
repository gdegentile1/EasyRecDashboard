package com.finboxsolutions.easyrec.dashboard.model;

/** One row of ER_DASHBOARD_TEMPLATE: a reconciliation template definition. */
public record TemplateRow(int templateId, String fullPath, String keyColumns, String ignoreColumns) {

    /** The file name at the end of FULL_PATH, with either separator. */
    public String shortName() {
        if (fullPath == null || fullPath.isEmpty()) {
            return "Template " + templateId;
        }
        int cut = Math.max(fullPath.lastIndexOf('/'), fullPath.lastIndexOf('\\'));
        return cut < 0 ? fullPath : fullPath.substring(cut + 1);
    }
}
