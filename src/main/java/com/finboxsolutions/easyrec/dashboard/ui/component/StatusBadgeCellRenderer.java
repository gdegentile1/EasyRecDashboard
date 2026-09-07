package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;

import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellRenderer;
import java.awt.Component;
import java.awt.Font;

/**
 * The Status column, drawn as a {@link StatusBadge}.
 *
 * <p>Status is the column the eye goes to first on three of these screens, and a coloured
 * word is a weak signal for it: at a glance it is the same shape as every other cell, and
 * red-on-white against green-on-white asks the reader to distinguish two hues of text.
 * A filled pill is a different shape, so the failures in a list of forty rows can be counted
 * without reading any of them.
 *
 * <p>The mapping itself is {@link StatusBadge#of}, so a status reads the same whether it is
 * drawn in a cell or, as on the batch header, on its own.
 */
public class StatusBadgeCellRenderer implements TableCellRenderer {

    private final StatusBadge badge = new StatusBadge(null);

    /** The badge's own padding, kept so the focus border can be taken off again. */
    private final Border padding = badge.getBorder();

    /**
     * The badge's own font, re-applied on every cell.
     *
     * <p>The grid's zoom wrapper reads a renderer's font, scales it and sets it back. A
     * component that keeps whatever it was last given would therefore grow on every repaint;
     * the grid's own renderers avoid that by re-applying their font each time, and so does
     * this one.
     */
    private final Font baseFont = Fonts.badge();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        badge.setFont(baseFont);
        badge.setStatus(toBadgeStatus(value));
        badge.setCellBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        if (isSelected) {
            // The pill keeps its own fill, but the word on it has to clear the selection.
            badge.setForeground(table.getSelectionForeground());
        }
        badge.setToolTipText(describe(value));
        badge.setBorder(hasFocus
                ? UIManager.getBorder("Table.focusCellHighlightBorder")
                : padding);
        return badge;
    }

    private static StatusBadge.Status toBadgeStatus(Object value) {
        return value instanceof StatusLabel label ? StatusBadge.of(label) : null;
    }

    private static String describe(Object value) {
        if (!(value instanceof StatusLabel label)) {
            return null;
        }
        return switch (label) {
            case PASSED -> "Completed with no breaks";
            case FAILED -> "Completed with breaks";
            case ERROR -> "Did not complete: execution error";
            case UNKNOWN -> "No status recorded";
        };
    }
}
