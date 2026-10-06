package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Font;

/**
 * The Trend column, drawn as a {@link TrendBadge}: the counterpart of
 * {@link StatusBadgeCellRenderer}, so a growing cause stands out of a long list by its
 * shape rather than by the hue of its text.
 *
 * <p>Works on any JTable whose column holds {@link PatternTrend} values, the dashboard grid
 * as well as the Global Patterns table of EasyRec. A cell without a trend stays empty.
 */
public class TrendBadgeCellRenderer implements TableCellRenderer {

    private final TrendBadge badge = new TrendBadge(null);

    /** The badge's own padding, kept so the focus border can be taken off again. */
    private final Border padding = badge.getBorder();

    /**
     * The badge's own font, re-applied on every cell, as in {@link StatusBadgeCellRenderer}:
     * a zoom wrapper that scales whatever font it finds would otherwise grow it each repaint.
     */
    private final Font baseFont = Fonts.badge();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        PatternTrend trend = value instanceof PatternTrend found ? found : null;
        badge.setFont(baseFont);
        badge.setTrend(trend);
        badge.setCellBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        if (isSelected) {
            // The pill keeps its own fill, but the word on it has to clear the selection.
            badge.setForeground(table.getSelectionForeground());
        }
        badge.setToolTipText(describe(trend));
        badge.setBorder(hasFocus
                ? UIManager.getBorder("Table.focusCellHighlightBorder")
                : padding);
        return badge;
    }

    private static String describe(PatternTrend trend) {
        if (trend == null) {
            return null;
        }
        return switch (trend) {
            case NEW -> "First run this pattern was detected on";
            case REAPPEARED -> "Detected again after one or more runs without it";
            case INCREASING -> "More breaks than on the previous run";
            case STABLE -> "About as many breaks as on the previous run";
            case DECREASING -> "Fewer breaks than on the previous run";
            case NOT_DETECTED -> "Not detected on this run: its breaks may have moved to the "
                    + "column's unexplained pattern, so this is not a fix";
            case STILL_NOT_DETECTED -> "Not detected on this run nor on the previous one";
        };
    }
}
