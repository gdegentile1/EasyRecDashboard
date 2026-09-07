package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.swing.jtable.renderers.PercentCellRenderer;
import com.finboxsolutions.swing.jtable.renderers.StandardCellRenderer;

import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Function;

/**
 * The cell renderers every dashboard table shares.
 *
 * <p>All of them are {@code StandardCellRenderer}s, the base every other renderer in the
 * grid is built on - {@code IntegerCellRenderer}, {@code PercentCellRenderer} and
 * {@code NumberCellRenderer} are each a few lines configuring a {@code ColumnAttribute} on
 * top of it, and these are the same. That matters for more than tidiness: a column with a
 * renderer of ours now carries the same font, padding, alignment and focus behaviour as a
 * column left to the grid's own, and the two stop looking like different tables.
 *
 * <p>Colour is the one thing taken away from {@code ColumnAttribute}. It caches
 * {@code UIManager} colours when it is constructed, which would freeze a renderer's
 * background at whatever theme was installed when the screen was built; every renderer here
 * reads the table's colours at paint time instead, so a theme switch follows.
 */
public final class Renderers {

    private Renderers() {
    }

    /** A status word as a coloured pill. */
    public static TableCellRenderer status() {
        return new StatusBadgeCellRenderer();
    }

    /** A match rate as {@code 91.93%}, coloured by band. */
    public static TableCellRenderer matchRate() {
        return new PercentRenderer();
    }

    /** A count with grouped thousands, right-aligned. */
    public static TableCellRenderer count() {
        return numeric(0, null);
    }

    /** A signed movement: green when it improved, red when it did not. */
    public static TableCellRenderer delta(String unit, boolean higherIsBetter) {
        return delta(unit, higherIsBetter, 2);
    }

    /**
     * A signed movement with a stated number of decimals.
     *
     * <p>A change in break count is a whole number of rows; printing it as {@code -59.00}
     * offers two digits of precision the figure does not have and costs the eye a moment
     * deciding they are zeroes.
     */
    public static TableCellRenderer delta(String unit, boolean higherIsBetter, int decimals) {
        return new DeltaRenderer(unit, higherIsBetter, decimals);
    }

    /**
     * A number with a fixed number of decimals.
     *
     * @param colour applied when the row is not selected, or null to leave it alone
     */
    public static TableCellRenderer numeric(int decimals, Color colour) {
        DashboardRenderer renderer = new DashboardRenderer(value -> colour);
        renderer.getColumnAttribute().setFormat(grouped(decimals));
        renderer.getColumnAttribute().setHorizontalAlignment(SwingConstants.RIGHT);
        return renderer;
    }

    /** Text with a tooltip carrying the full value, for columns that fold several. */
    public static TableCellRenderer foldedText() {
        return new FoldedTextRenderer();
    }

    /** Right-aligned plain text, for id columns. */
    public static TableCellRenderer identifier() {
        DashboardRenderer renderer = new DashboardRenderer(value -> null);
        renderer.getColumnAttribute().setHorizontalAlignment(SwingConstants.RIGHT);
        return renderer;
    }

    private static NumberFormat grouped(int decimals) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ROOT);
        format.setMinimumFractionDigits(decimals);
        format.setMaximumFractionDigits(decimals);
        format.setGroupingUsed(true);
        return format;
    }

    // =========================================================================== renderers

    /**
     * The base: the grid's own renderer, with the cell's colour decided per value.
     *
     * <p>{@code StandardCellRenderer.setColor} is the hook the grid provides for exactly
     * this, so per-value colouring is an override rather than something bolted on top of
     * {@code DefaultTableCellRenderer} - whose {@code setForeground} doubles as "and use
     * this for every unselected row from now on", which is how a conditional colour leaks
     * down a column.
     */
    private static class DashboardRenderer extends StandardCellRenderer {

        private static final long serialVersionUID = 1L;

        private final transient Function<Object, Color> foreground;

        DashboardRenderer(Function<Object, Color> foreground) {
            this.foreground = foreground;
            getColumnAttribute().setFont(Fonts.cell());
        }

        @Override
        protected void setColor(JTable table, Object value, boolean isSelected) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
                return;
            }
            Color colour = foreground.apply(value);
            setForeground(colour != null ? colour : table.getForeground());
            setBackground(table.getBackground());
        }
    }

    /**
     * A match rate, on the grid's own percentage renderer.
     *
     * <p>{@code PercentCellRenderer} is what every other percentage column in the
     * application is drawn with: it carries the locale, the two fraction digits, the
     * grouping and the right alignment, and a percentage typed the same way everywhere is
     * one less thing for a reader to check.
     *
     * <p>It formats a ratio, because {@code NumberFormat.getPercentInstance} multiplies by a
     * hundred on the way out. These rates are stored and carried as percentages - the KPI
     * tiles, the charts and the comparison all read them that way - so the value is divided
     * here, at the last moment, rather than changing what a match rate means everywhere else
     * to suit one renderer.
     */
    private static final class PercentRenderer extends PercentCellRenderer {

        private static final long serialVersionUID = 1L;

        private static final double PERCENT = 100.0d;

        private PercentRenderer() {
            getColumnAttribute().setFont(Fonts.cell());
        }

        @Override
        protected void setColor(JTable table, Object value, boolean isSelected) {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
                return;
            }
            setForeground(Palette.forRate(value instanceof Number number
                    ? number.doubleValue() : null));
            setBackground(table.getBackground());
        }

        @Override
        public String toString(Object value) {
            return value instanceof Number number
                    ? super.toString(number.doubleValue() / PERCENT)
                    : "-";
        }
    }

    /** A signed movement, with its sign spelled out and its direction coloured. */
    private static final class DeltaRenderer extends DashboardRenderer {

        private static final long serialVersionUID = 1L;

        private final transient NumberFormat format;
        private final String unit;

        private DeltaRenderer(String unit, boolean higherIsBetter, int decimals) {
            super(value -> {
                if (!(value instanceof Number number)) {
                    return null;
                }
                double delta = number.doubleValue();
                return delta == 0.0d ? Palette.muted()
                        : (higherIsBetter == (delta > 0) ? Palette.success() : Palette.error());
            });
            this.format = grouped(decimals);
            this.unit = unit;
            getColumnAttribute().setHorizontalAlignment(SwingConstants.RIGHT);
        }

        @Override
        public String toString(Object value) {
            if (!(value instanceof Number number)) {
                return "-";
            }
            double delta = number.doubleValue();
            return (delta >= 0 ? "+" : "") + format.format(delta) + unit;
        }
    }

    /**
     * Several values folded into one cell: the first, a count of the rest, and all of them
     * in the tooltip.
     */
    private static final class FoldedTextRenderer extends DashboardRenderer {

        private static final long serialVersionUID = 1L;

        private static final String SEPARATOR = " | ";

        private FoldedTextRenderer() {
            super(value -> null);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            // After super, which sets the tooltip from the folded text when the column asks
            // for one. The point of the tooltip here is the part that was folded away.
            String full = value == null ? null : value.toString();
            setToolTipText(full == null || full.isEmpty() || "-".equals(full) ? null : full);
            return this;
        }

        @Override
        public String toString(Object value) {
            String text = value == null ? "-" : value.toString();
            int separator = text.indexOf(SEPARATOR);
            if (separator < 0) {
                return text;
            }
            long extra = text.chars().filter(character -> character == '|').count();
            return text.substring(0, separator) + "  +" + extra;
        }
    }
}
