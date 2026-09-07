package com.finboxsolutions.easyrec.dashboard.service;

import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds ER_DASHBOARD_PIVOT rows into the ordered tree the pivot table renders.
 *
 * <p>EasyRec may write only the leaf combinations - the sample data has nothing above
 * PIVOT_LEVEL 1 - so ancestors are created on demand and their metrics summed from their
 * descendants. A parent that EasyRec did write wins over the sum, since that is what the
 * engine itself reported; a synthesised one is flagged {@link Node#aggregated()} so the
 * table can mark it.
 *
 * <p>Ratios cannot be summed up a tree, so a synthesised parent recomputes them from its
 * own summed numerator and denominator - see {@link PivotMetric#derivedFrom()}.
 */
public final class PivotTreeBuilder {

    /**
     * One node of the flattened tree, in pre-order.
     *
     * <p>Each node carries its own id and its parent's, so a branch can be folded without
     * parsing key strings back apart - which also keeps a key containing the separator
     * harmless. Root nodes have a parent id of 0.
     */
    public record Node(
            int id,
            int parentId,
            String key,
            String path,
            int depth,
            boolean hasChildren,
            int leafCount,
            boolean aggregated,
            String levelName,
            Map<PivotMetric, Double> values) {

        public double value(PivotMetric metric) {
            Double found = values.get(metric);
            return found == null ? 0.0d : found;
        }
    }

    /** The flattened tree plus what the surrounding controls need to describe it. */
    public record Tree(List<Node> rows, int maxDepth, List<Level> levels) {

        public boolean isEmpty() {
            return rows.isEmpty();
        }

        /** The first-level keys, which double as the options of the column filter. */
        public List<Node> roots() {
            List<Node> roots = new ArrayList<>();
            for (Node node : rows) {
                if (node.depth() == 0) {
                    roots.add(node);
                }
            }
            return roots;
        }
    }

    /**
     * One level of the tree, for the depth selector.
     *
     * <p>{@code field} is the source column behind the level and is empty when there is
     * none; {@code name} is what to show. Keeping them apart lets a header list only the
     * real fields while the picker still labels every level.
     */
    public record Level(int depth, String field, String name) {
    }

    private PivotTreeBuilder() {
    }

    /**
     * @param pivots      the complete pivot set for one (run, template); a truncated read
     *                    would drop children and make their parents' totals wrong
     * @param rawBreakdown ER_DASHBOARD_STAT_ROWS.PIVOT_BREAKDOWN, which names the levels
     */
    public static Tree build(List<PivotRow> pivots, String rawBreakdown) {
        Map<List<String>, MutableNode> nodes = new LinkedHashMap<>();
        List<MutableNode> roots = new ArrayList<>();

        for (PivotRow pivot : pivots) {
            List<String> path = pathOf(pivot);
            if (path.isEmpty()) {
                continue;
            }
            for (int depth = 0; depth < path.size(); depth++) {
                List<String> prefix = List.copyOf(path.subList(0, depth + 1));
                if (nodes.containsKey(prefix)) {
                    continue;
                }
                MutableNode node = new MutableNode(prefix.get(depth), prefix, depth);
                nodes.put(prefix, node);
                MutableNode parent = depth == 0
                        ? null
                        : nodes.get(List.copyOf(prefix.subList(0, depth)));
                (parent == null ? roots : parent.children).add(node);
            }
            // Two rows on one path would silently overwrite, so the first seen wins.
            MutableNode leaf = nodes.get(path);
            if (leaf.row == null) {
                leaf.row = pivot;
            }
        }

        for (MutableNode root : roots) {
            resolve(root);
        }
        roots.sort((left, right) -> left.key.compareTo(right.key));

        List<Node> flattened = new ArrayList<>();
        int maxDepth = 0;
        for (MutableNode root : roots) {
            maxDepth = Math.max(maxDepth, flatten(root, 0, flattened));
        }

        List<Level> levels = flattened.isEmpty()
                ? List.of()
                : levelOptions(breakdownNames(rawBreakdown), maxDepth);

        // The level name is part of each row so the table can label a node without
        // reaching back into the level list on every cell paint.
        List<Node> named = new ArrayList<>(flattened.size());
        for (Node node : flattened) {
            String name = node.depth() < levels.size()
                    ? levels.get(node.depth()).name()
                    : "Level " + (node.depth() + 1);
            named.add(new Node(node.id(), node.parentId(), node.key(), node.path(), node.depth(),
                    node.hasChildren(), node.leafCount(), node.aggregated(), name, node.values()));
        }
        return new Tree(Collections.unmodifiableList(named), maxDepth, levels);
    }

    /**
     * The key components of a pivot row, outermost first.
     *
     * <p>PIVOT_LEVEL is trusted only when it agrees with the populated key columns, so a
     * row whose level is missing or stale still lands somewhere sensible rather than being
     * dropped. Reading stops at the first empty column: a hole would otherwise graft a deep
     * key onto the wrong ancestor.
     */
    public static List<String> pathOf(PivotRow pivot) {
        List<String> path = new ArrayList<>(PivotRow.MAX_KEYS);
        for (String raw : pivot.keys()) {
            String value = ContextValues.clean(raw);
            if (value == null) {
                break;
            }
            path.add(value);
        }
        Integer level = pivot.pivotLevel();
        if (level != null && level >= 0 && level < path.size()) {
            path = new ArrayList<>(path.subList(0, level + 1));
        }
        if (path.isEmpty() && pivot.pivotKeys() != null && !pivot.pivotKeys().isBlank()) {
            // No split columns at all - fall back to the joined form.
            for (String part : pivot.pivotKeys().split(java.util.regex.Pattern.quote(PivotRow.KEY_SEPARATOR))) {
                if (!part.isBlank()) {
                    path.add(part);
                }
            }
        }
        return List.copyOf(path);
    }

    /**
     * The names of the pivot breakdown levels, outermost first.
     *
     * <p>PIVOT_BREAKDOWN holds them as a bracketed, comma-separated list -
     * {@code [==BREAK_TYPE==, M_TP_PFOLIO]} - one name per PIVOT_KEY column, in the same
     * order as the keys. A name wrapped in {@code ==} is EasyRec's marker for a level with
     * no source column behind it, not a field anybody chose to break down by; those slots
     * come back empty so the level keeps its position while contributing no name.
     */
    public static List<String> breakdownNames(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String body = raw.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        List<String> names = new ArrayList<>();
        for (String part : body.split(",")) {
            String name = part.trim();
            if (name.isEmpty()) {
                continue;
            }
            names.add(name.startsWith("==") && name.endsWith("==") ? "" : name);
        }
        return names;
    }

    /**
     * One entry per level the tree actually has.
     *
     * <p>Driven by the tree's own depth rather than by the name list: a breakdown naming
     * more levels than the pivot rows reach would otherwise offer levels that expand to
     * nothing, and one naming fewer would hide levels that exist.
     */
    public static List<Level> levelOptions(List<String> names, int maxDepth) {
        List<Level> levels = new ArrayList<>(maxDepth + 1);
        for (int depth = 0; depth <= maxDepth; depth++) {
            String field = depth < names.size() ? names.get(depth) : "";
            levels.add(new Level(depth, field, field.isEmpty() ? "Level " + (depth + 1) : field));
        }
        return levels;
    }

    // ------------------------------------------------------------------------ internals

    private static final class MutableNode {
        private final String key;
        private final List<String> path;
        private final int depth;
        private final List<MutableNode> children = new ArrayList<>();
        private PivotRow row;
        private Map<PivotMetric, Double> values;
        private boolean aggregated;
        private int leafCount;

        private MutableNode(String key, List<String> path, int depth) {
            this.key = key;
            this.path = path;
            this.depth = depth;
        }
    }

    /** Depth-first, so a parent sums children that may be sums themselves. */
    private static void resolve(MutableNode node) {
        for (MutableNode child : node.children) {
            resolve(child);
        }
        EnumMap<PivotMetric, Double> values = new EnumMap<>(PivotMetric.class);
        if (node.row != null) {
            for (PivotMetric metric : PivotMetric.values()) {
                values.put(metric, node.row.metric(metric));
            }
            node.aggregated = false;
        } else {
            for (PivotMetric metric : PivotMetric.values()) {
                double sum = 0.0d;
                for (MutableNode child : node.children) {
                    Double childValue = child.values.get(metric);
                    sum += childValue == null ? 0.0d : childValue;
                }
                values.put(metric, sum);
            }
            for (PivotMetric metric : PivotMetric.values()) {
                PivotMetric[] operands = metric.derivedFrom();
                if (operands != null) {
                    values.put(metric, Rates.impactRatio(values.get(operands[0]), values.get(operands[1])));
                }
            }
            node.aggregated = true;
        }
        node.values = values;
        if (node.children.isEmpty()) {
            node.leafCount = 1;
        } else {
            int leaves = 0;
            for (MutableNode child : node.children) {
                leaves += child.leafCount;
            }
            node.leafCount = leaves;
        }
    }

    /** Appends {@code node} and its subtree in pre-order; returns the deepest depth reached. */
    private static int flatten(MutableNode node, int parentId, List<Node> out) {
        int id = out.size() + 1;
        node.children.sort((left, right) -> left.key.compareTo(right.key));
        out.add(new Node(id, parentId, node.key, String.join(PivotRow.KEY_SEPARATOR, node.path),
                node.depth, !node.children.isEmpty(), node.leafCount, node.aggregated, "",
                Collections.unmodifiableMap(node.values)));
        int maxDepth = node.depth;
        for (MutableNode child : node.children) {
            maxDepth = Math.max(maxDepth, flatten(child, id, out));
        }
        return maxDepth;
    }
}
