package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.swing.jtable.renderers.StandardCellRenderer;
import com.finboxsolutions.swing.jtable.renderers.header.VerticalTableHeaderCellRenderer;
import com.finboxsolutions.swing.jtable.renderers.zoom.ZoomRenderer;

import org.jdesktop.swingx.JXTreeTable;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Font;

/**
 * The one place the dashboard's table type size is decided.
 *
 * <p>The grid does not agree with itself about this. {@code ColumnAttribute} hard-codes
 * {@code SANS_SERIF} at 11 for cells, {@code VerticalTableHeaderCellRenderer} hard-codes the
 * theme's family bold at 11 for headers, and {@code ZoomRenderer} overrides both from the row
 * height. A dashboard screen is a lot of table on one page - seventeen columns of batches
 * above four KPI tiles - and at those sizes the table shouts over everything around it.
 *
 * <p>Both faces are derived from {@code Table.font}, so they follow the look and feel's
 * family and the two sizes below are the only numbers to change.
 */
public final class Fonts {

    /**
     * How far from {@code Table.font} the dashboard sets its tables. Zero: they match it.
     *
     * <p>An offset rather than a size, so {@code Table.font} stays the one lever a host has
     * over it. Setting that font is the obvious way to size every table in an application at
     * once, and it works under FlatLaf and nowhere else - see {@link #rowHeight()} for why -
     * so the dashboard reads it directly and applies it itself, under every look and feel.
     *
     * <p>It was -2 for a while, on the reasoning that a dashboard is a lot of table on one
     * page and could stand to be denser than the rest of the application. That reasoning
     * holds only while nobody has said otherwise: a host that sets {@code Table.font} has
     * said otherwise, and subtracting from it puts the dashboard two points under a size
     * somebody chose on purpose. Negative values still work if these tables should be denser
     * than their neighbours; this is the number to change.
     */
    private static final float SIZE_OFFSET = 0f;

    /** However far {@code Table.font} is turned down, the tables stop shrinking here. */
    private static final float MIN_FONT_SIZE = 9f;

    /** Tall enough for a status badge to keep its padding. */
    private static final int MIN_ROW_HEIGHT = 20;

    private Fonts() {
    }

    public static Font cell() {
        return base().deriveFont(Font.PLAIN, cellSize());
    }

    public static Font header() {
        return base().deriveFont(Font.BOLD, cellSize());
    }

    /** The classes a grid registers a default renderer for, plus the ones the dashboard adds. */
    private static final Class<?>[] RENDERED_TYPES = {
        Object.class, String.class, Number.class, Integer.class, Long.class,
        Double.class, Float.class, Boolean.class, java.util.Date.class, java.sql.Time.class
    };

    /** A badge carries its own weight, at the cell size. */
    public static Font badge() {
        return base().deriveFont(Font.BOLD, badgeSize());
    }

    public static float cellSize() {
        return Math.max(MIN_FONT_SIZE, base().getSize2D() + SIZE_OFFSET);
    }

    public static float badgeSize() {
        return cellSize();
    }

    /**
     * The size a cell is actually drawn at, given the row height it is drawn in.
     *
     * <p>The grid sizes type from row height: {@code ZoomRenderer} rewrites every cell's font
     * to {@code rowHeight - 4 + (size - 12)} unless the row height is exactly the value
     * {@code UIManager.getInt("Table.rowHeight")} held when that class was first loaded. In a
     * host that loads it before its theme is in place, that test never passes, the row height
     * decides the type size outright, and changing a base size here moves the result by a
     * point out of sixteen - which is why the dashboard's sizes held in isolation and not in
     * EasyRec.
     *
     * <p>So the size is settled here instead, from a base and the distance between the row
     * height in use and the theme's own. At the theme's height it is exactly the base; a
     * ctrl-wheel zoom, which works by growing the row height, still grows the text; and a
     * theme that scales its metrics for a high-DPI screen scales both terms with it.
     */
    public static Font sized(Font font, float baseSize, int rowHeight) {
        float size = Math.max(MIN_FONT_SIZE, baseSize + (rowHeight - rowHeight()));
        return font == null || font.getSize2D() == size ? font : font.deriveFont(size);
    }

    /**
     * The row height the look and feel asks for.
     *
     * <p>{@code Table.rowHeight} is a FlatLaf key. Metal, Windows and Nimbus do not define
     * it, so this reads 0 under all of them and the fallback below is what the dashboard
     * uses - and, more to the point, {@code ZoomRenderer} captures the same 0 as the height
     * at which it stands aside. No table is ever nought pixels tall, so under any look and
     * feel but FlatLaf that wrapper is permanently on and the row height decides the type
     * size outright, whatever {@code Table.font} says. {@link #sized} is what settles it.
     */
    public static int rowHeight() {
        int theme = UIManager.getInt("Table.rowHeight");
        return theme > 0 ? Math.max(theme, MIN_ROW_HEIGHT) : MIN_ROW_HEIGHT;
    }

    private static Font base() {
        Font found = UIManager.getFont("Table.font");
        return found != null ? found : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    /**
     * Sets these faces on every renderer a grid draws with.
     *
     * <p>Three places decide the size and none of them agree, so all three are set: the
     * {@code ColumnAttribute} each {@code StandardCellRenderer} formats from, the header
     * renderer, whose own default is baked into its no-argument constructor, and any
     * renderer already wrapped for zoom, which has to be unwrapped to be reached.
     *
     * <p>Takes a plain {@code JTable} because it is wanted for two of them: the dashboard's
     * own grid, and the {@code ExcelTreeTable} the pivot breakdown is drawn on, which sits
     * directly under one of them on the reconciliation screen and would otherwise be the
     * only table on the page at a different size.
     */
    public static void applyTo(JTable table) {
        // Row height first, and before anything reads a font. ZoomRenderer leaves a table
        // alone at the theme's own row height and otherwise rewrites every cell's font to
        // fit the row - so a grid at any other height silently overrules the size set below.
        table.setRowHeight(rowHeight());

        Font cell = cell();
        table.setFont(cell);
        for (Class<?> type : RENDERED_TYPES) {
            applyFont(table.getDefaultRenderer(type), cell);
        }
        VerticalTableHeaderCellRenderer header = new VerticalTableHeaderCellRenderer(header());
        for (int index = 0; index < table.getColumnCount(); index++) {
            applyFont(table.getColumnModel().getColumn(index).getCellRenderer(), cell);
            table.getColumnModel().getColumn(index).setHeaderRenderer(header);
        }
        applyTreeFont(table, cell);
    }

    /**
     * The hierarchical column of a tree table, which no renderer of the grid's draws.
     *
     * <p>SwingX paints it with a {@code JXTree} of its own that answers to its own font, so
     * the pivot breakdown's level names would otherwise stay large while the value columns
     * beside them shrank.
     */
    private static void applyTreeFont(JTable table, Font font) {
        if (!(table instanceof JXTreeTable treeTable)) {
            return;
        }
        int hierarchical = treeTable.getHierarchicalColumn();
        if (hierarchical < 0) {
            return;
        }
        if (treeTable.getCellRenderer(0, hierarchical) instanceof Component tree) {
            tree.setFont(font);
        }
    }

    private static void applyFont(TableCellRenderer renderer, Font font) {
        TableCellRenderer target = renderer instanceof ZoomRenderer zoom
                ? zoom.getRendererDelegate()
                : renderer;
        if (target instanceof StandardCellRenderer standard) {
            standard.getColumnAttribute().setFont(font);
        }
    }
}
