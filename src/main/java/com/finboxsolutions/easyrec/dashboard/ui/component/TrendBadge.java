package com.finboxsolutions.easyrec.dashboard.ui.component;

import com.finboxsolutions.easyrec.dashboard.model.PatternTrend;

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
 * A pattern trend drawn as a rounded pill, the counterpart of {@link StatusBadge}.
 *
 * <p>Same shape, same mixing rules and same metrics as the status pill, so a trend and a
 * status read as the same kind of object on any screen. The colour is
 * {@link Palette#forTrend}: red for growth, amber for something that appeared, green for a
 * decrease, grey for no movement and for a pattern no longer detected, which is not a fix.
 *
 * <p>Kept apart from {@link StatusBadge} rather than folded into it: that class maps a fixed
 * set of outcomes to upper case words, and changing it would touch every status cell of the
 * dashboard for a column that only needs the same drawing.
 */
public class TrendBadge extends JLabel {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 12;

    /** As in {@link StatusBadge}: a dark surface needs more of itself in the fill. */
    private static final float FILL_MIX_ON_LIGHT = 0.84f;
    private static final float FILL_MIX_ON_DARK = 0.90f;

    /** How much of the theme's text colour is mixed in for the label. */
    private static final float TEXT_MIX = 0.30f;

    private transient PatternTrend trend;
    private transient Color cellBackground;

    public TrendBadge(PatternTrend trend) {
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(new EmptyBorder(3, 9, 3, 9));

        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            setFont(font.deriveFont(Font.BOLD, font.getSize2D() - 1f));
        }
        setTrend(trend);
    }

    /**
     * @param trend the trend to show, or null for a cause with no trend, which shows an
     *              empty cell rather than a pill: no history is not a movement
     */
    public final void setTrend(PatternTrend trend) {
        this.trend = trend;
        setText(trend == null ? "" : trend.label());
        setForeground(trend == null ? Palette.muted() : Palette.mix(hue(), Palette.text(), TEXT_MIX));
        revalidate();
        repaint();
    }

    public PatternTrend getTrend() {
        return trend;
    }

    /** What to paint behind the pill: the table's selection or background colour. */
    public void setCellBackground(Color cellBackground) {
        this.cellBackground = cellBackground;
    }

    private Color hue() {
        return Palette.forTrend(trend);
    }

    private Color fill() {
        Color surface = surface();
        return Palette.mix(hue(), surface, isDark(surface) ? FILL_MIX_ON_DARK : FILL_MIX_ON_LIGHT);
    }

    /** Rec. 601 luma, which is close enough to decide light from dark. */
    private static boolean isDark(Color colour) {
        return colour != null
                && (0.299 * colour.getRed() + 0.587 * colour.getGreen() + 0.114 * colour.getBlue()) < 128;
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
            if (trend != null) {
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
        int height = Math.min(getHeight() - 4,
                metrics.getHeight() + getInsets().top + getInsets().bottom);
        int x = (getWidth() - width) / 2;
        int y = (getHeight() - height) / 2;

        g2.setColor(fill());
        g2.fillRoundRect(x, y, width, height, ARC, ARC);
    }
}
