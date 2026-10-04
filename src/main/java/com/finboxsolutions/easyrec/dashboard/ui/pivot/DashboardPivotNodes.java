package com.finboxsolutions.easyrec.dashboard.ui.pivot;

import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;
import com.finboxsolutions.swing.treetable.model.FilterableExcelTreeTableModel;
import com.finboxsolutions.swing.treetable.node.ExcelNode;
import com.finboxsolutions.swing.treetable.sorter.ExcelSortableTreeTableNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/**
 * Turns stored pivot rows into the tree EasyRec's pivot component already knows how to
 * display.
 *
 * <p>This is the whole reason the dashboard can reuse {@code ExcelTreeTable} rather than
 * carry a table of its own. The live pivot screen gets its tree from
 * {@code PivotBuilder.getTreeTableModel(appender, ...)}, which recomputes everything from
 * the reconciliation in memory. The dashboard has no appender: its numbers were computed
 * once, when the run happened, and written to ER_DASHBOARD_PIVOT. So the tree is rebuilt
 * from those rows and handed to the same component.
 *
 * <p>Two consequences of that difference are worth knowing.
 *
 * <p>First, ER_DASHBOARD_PIVOT stores only the combinations EasyRec wrote, which in
 * practice means the leaves. The parents are synthesised by {@link PivotTreeBuilder}, which
 * sums the counts and recomputes the ratios rather than adding them up. That happens before
 * this class is called, so the nodes handed over already carry correct subtotals and the
 * component's own subtotal option has nothing left to do here.
 *
 * <p>Second, tolerances are not adjustable. The live screen recomputes the pivot when the
 * tolerance checkbox changes; a stored pivot was computed with whatever tolerance applied
 * at run time and cannot be recomputed without the underlying rows.
 *
 * <p><b>Both hierarchies are linked, not just one.</b> {@code FilterableExcelTreeTableModel}
 * takes an {@link ExcelSortableTreeTableNode} root, but its search filter reads through to
 * the wrapped {@link ExcelNode}: {@code isChildrenMatchPattern(n.getExcelNode(), filter)}
 * walks the ExcelNode's own children. Linking only the wrappers would render the tree
 * correctly and then leave the search box matching nothing below the first level. So the
 * ExcelNode tree is built first and in full, and the wrappers are added over it only if
 * they are not derived from it already - see the runtime check below.
 */
public final class DashboardPivotNodes {

    private DashboardPivotNodes() {
    }

    /**
     * Builds the tree table model for one reconciliation's stored breakdown.
     *
     * @param tree    the folded tree, parents already summed
     * @param metrics the measures to show, in column order
     */
    public static FilterableExcelTreeTableModel toTreeTableModel(
            PivotTreeBuilder.Tree tree, List<PivotMetric> metrics) {

        Vector<String> columnNames = new Vector<>();
        columnNames.add(hierarchicalColumnName(tree));
        for (PivotMetric metric : metrics) {
            columnNames.add(metric.label());
        }

        // Pass one: the ExcelNode tree, complete and linked. The flattened rows arrive in
        // pre-order with each node carrying its parent's id, so one sweep rebuilds the
        // hierarchy with no lookup by key string - which is what keeps a pivot key
        // containing the separator harmless - and a parent always exists before its
        // children need it.
        // The two-argument constructor is the one that initialises the children map;
        // ExcelNode(key) alone leaves it null and the first addChild throws. EasyRec's own
        // ExcelNodeBuilder builds every node as new ExcelNode(key, values) for that reason,
        // and gives its root null values, which renders the root row blank as it does there.
        ExcelNode root = new ExcelNode("", null);

        Map<Integer, ExcelNode> nodesById = new LinkedHashMap<>();
        nodesById.put(0, root);
        for (PivotTreeBuilder.Node node : tree.rows()) {
            ExcelNode child = new ExcelNode(node.key(), valuesOf(node, metrics));
            nodesById.getOrDefault(node.parentId(), root).addChild(child);
            nodesById.put(node.id(), child);
        }

        // Pass two: the wrapper tree the model is built from - but only if the wrapper did
        // not already build it. Whether ExcelSortableTreeTableNode mirrors the subtree of
        // the node it wraps is the one thing about this seam that could not be read off
        // PanelPivot, so it is asked at runtime rather than assumed: wrapping the finished
        // root and finding children means the wrapper mirrors, and adding them here would
        // double every branch.
        ExcelSortableTreeTableNode rootWrapper = new ExcelSortableTreeTableNode(root);
        if (rootWrapper.getChildCount() == 0 && !tree.rows().isEmpty()) {
            Map<Integer, ExcelSortableTreeTableNode> wrappersById = new LinkedHashMap<>();
            wrappersById.put(0, rootWrapper);
            for (PivotTreeBuilder.Node node : tree.rows()) {
                ExcelSortableTreeTableNode wrapper =
                        new ExcelSortableTreeTableNode(nodesById.get(node.id()));
                wrappersById.getOrDefault(node.parentId(), rootWrapper).add(wrapper);
                wrappersById.put(node.id(), wrapper);
            }
        }

        return new FilterableExcelTreeTableModel(rootWrapper, columnNames);
    }

    /**
     * The name of the tree column, whatever the breakdown: "Break", as on the live pivot.
     *
     * <p>It used to be the breakdown fields joined - {@code M_TP_PFOLIO} - which made the
     * header change from one reconciliation to the next and read as a data column rather
     * than as the tree. The fields are in the header's tooltip instead; see
     * {@link #breakdownTooltip}.
     */
    public static final String TREE_COLUMN = "Break";

    private static String hierarchicalColumnName(PivotTreeBuilder.Tree tree) {
        return TREE_COLUMN;
    }

    /**
     * What the tree column's header says when hovered: the breakdown, one level per line,
     * outermost first.
     *
     * <p>Read from the raw PIVOT_BREAKDOWN - {@code [==BREAK_TYPE==, M_TP_PFOLIO]} - rather
     * than from the tree's levels, which leave EasyRec's {@code ==...==} levels unnamed. Those
     * have no source column behind them, so they are spelled out ("Break type") and marked as
     * computed by EasyRec; the others are the template's field names as written.
     *
     * @return the tooltip, or null when no breakdown was recorded
     */
    public static String breakdownTooltip(String rawBreakdown) {
        if (rawBreakdown == null || rawBreakdown.isBlank()) {
            return null;
        }
        String body = rawBreakdown.trim();
        if (body.startsWith("[") && body.endsWith("]")) {
            body = body.substring(1, body.length() - 1);
        }
        StringBuilder text = new StringBuilder("<html><b>Pivot breakdown</b>");
        int level = 0;
        for (String part : body.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            text.append("<br>").append(++level).append(". ");
            if (name.length() > 4 && name.startsWith("==") && name.endsWith("==")) {
                text.append(escape(humanise(name.substring(2, name.length() - 2))))
                        .append(" <i>(computed by EasyRec)</i>");
            } else {
                text.append(escape(name));
            }
        }
        return level == 0 ? null : text.append("</html>").toString();
    }

    /** {@code BREAK_TYPE} as "Break type". */
    private static String humanise(String marker) {
        String words = marker.replace('_', ' ').trim().toLowerCase(java.util.Locale.ROOT);
        return words.isEmpty() ? marker : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static Vector<Object> valuesOf(PivotTreeBuilder.Node node, List<PivotMetric> metrics) {
        Vector<Object> values = new Vector<>(metrics.size());
        for (PivotMetric metric : metrics) {
            // A ratio stays a ratio, as the live pivot hands it over: its percentage column is
            // drawn by SignedProgressCellRenderer, which multiplies by 100 itself. Scaling it
            // here as well printed 10,000% once the live view's renderer applied.
            values.add(node.value(metric));
        }
        return values;
    }
}
