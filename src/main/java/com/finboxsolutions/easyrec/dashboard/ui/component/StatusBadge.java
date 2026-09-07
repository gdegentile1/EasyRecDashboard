package com.finboxsolutions.easyrec.dashboard.ui.component;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * A status word drawn as a rounded pill.
 *
 * <p>Three things differ from the badge this was taken from, each because it is used inside
 * a table cell rather than on a form:
 *
 * <ul>
 *   <li><b>The pill hugs its text.</b> Filling the whole component with the rounded
 *       rectangle is right for a badge sitting in a layout; in a cell it would stretch the
 *       pill across the column and stop reading as a badge at all.</li>
 *   <li><b>It paints the cell behind itself.</b> A renderer is never added to a container,
 *       so nothing else paints the selection colour under it.</li>
 *   <li><b>The colours are mixed, not fixed.</b> The original's five pastel pairs are a
 *       light theme written down. Each is now the semantic colour of the status mixed
 *       towards the surface behind it, and its text the same colour mixed towards whatever
 *       the theme uses for text - which lands on the original pastels under FlatLaf Light
 *       and on legible dark tints under FlatLaf Dark, from one formula.</li>
 * </ul>
 */
public class StatusBadge extends JLabel {

    private static final long serialVersionUID = 1L;

    public enum Status {
        PASSED,
        FAILED,
        /** The execution did not complete. Not one side of the pass/fail pair. */
        ERROR,
        WARNING,
        RUNNING,
        SKIPPED
    }

    private static final int ARC = 12;

    /**
     * How much of the surface is mixed into the semantic colour for the fill.
     *
     * <p>A dark surface needs more of itself in the mix than a light one: the same 16% of
     * red over near-white is a pastel, and over near-black it is a muddy maroon that
     * competes with the word printed on it.
     */
    private static final float FILL_MIX_ON_LIGHT = 0.84f;
    private static final float FILL_MIX_ON_DARK = 0.90f;

    /** How much of the theme's text colour is mixed in for the label. */
    private static final float TEXT_MIX = 0.30f;

    /** What a cell with no recorded status shows, with no pill behind it. */
    private static final String NO_STATUS = "-";

    private transient Status status;
    private transient Color cellBackground;

    public StatusBadge(Status status) {
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);

        // Padding inside the badge.
        setBorder(new EmptyBorder(3, 9, 3, 9));

        // Follow the current look and feel's font, one step down and bold.
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
        }

        setStatus(status);
    }

    /** @param status the status to show, or null for a cell with none recorded */
    public final void setStatus(Status status) {
        this.status = status;
        setText(status == null ? NO_STATUS : status.name());
        setForeground(status == null ? Palette.muted() : statusForeground());
        revalidate();
        repaint();
    }

    public Status getStatus() {
        return status;
    }

    /**
     * What to paint behind the pill - the table's selection or background colour.
     *
     * <p>Null leaves the component transparent, which is what a badge on a form wants.
     */
    public void setCellBackground(Color cellBackground) {
        this.cellBackground = cellBackground;
    }

    private Color statusBackground() {
        Color surface = surface();
        return Palette.mix(hue(), surface, isDark(surface) ? FILL_MIX_ON_DARK : FILL_MIX_ON_LIGHT);
    }

    /** Rec. 601 luma, which is close enough to decide light from dark. */
    private static boolean isDark(Color colour) {
        return colour != null
                && (0.299 * colour.getRed() + 0.587 * colour.getGreen() + 0.114 * colour.getBlue()) < 128;
    }

    private Color statusForeground() {
        return Palette.mix(hue(), Palette.text(), TEXT_MIX);
    }

    /** The dashboard's own semantic colour for this status, so one palette drives both. */
    private Color hue() {
        return switch (status) {
            case PASSED -> Palette.success();
            case FAILED -> Palette.error();
            // A run that could not finish is not a run that finished and disagreed, so it
            // keeps the colour the dashboard has always given it rather than FAILED's red.
            case ERROR, WARNING -> Palette.warning();
            case RUNNING -> Palette.accent();
            case SKIPPED -> Palette.muted();
        };
    }

    private static Color surface() {
        Color found = UIManager.getColor("Table.background");
        return found != null ? found : Palette.cardBackground();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (cellBackground != null) {
                g2.setColor(cellBackground);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
            if (status != null) {
                paintPill(g2);
            }
        } finally {
            g2.dispose();
        }
        super.paintComponent(g);
    }

    /** The pill, sized to the text and centred, never wider than the cell. */
    private void paintPill(Graphics2D g2) {
        FontMetrics metrics = getFontMetrics(getFont());
        int textWidth = metrics.stringWidth(getText());
        int width = Math.min(getWidth(), textWidth + getInsets().left + getInsets().right);
        // Two pixels clear of the grid line above and below, so the pill reads as an object
        // in the row rather than as the row's own background.
        int height = Math.min(getHeight() - 4,
                metrics.getHeight() + getInsets().top + getInsets().bottom);
        int x = (getWidth() - width) / 2;
        int y = (getHeight() - height) / 2;

        g2.setColor(statusBackground());
        g2.fillRoundRect(x, y, width, height, ARC, ARC);
    }
}
