package com.finboxsolutions.easyrec.dashboard.ui.component;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * One control with several positions, drawn as a single track with the chosen segment
 * filled.
 *
 * <p>Three toggle buttons in a row are three buttons: each carries its own border, they sit
 * apart, and which one is down is a shade of fill the eye has to hunt for. The same three in
 * a track read as one question with three answers, which is what a period selector is.
 * {@code PanelPivotActionBar} makes the same move for its level buttons, in the vocabulary it
 * had - a tight group of borderless buttons, the selected one swapped to a filled icon font.
 *
 * <p>The track and the selected pill are painted here rather than left to the look and feel:
 * a toggle button's selected state is drawn differently by FlatLaf, Metal and Windows, and
 * only FlatLaf's is emphatic enough to answer "which period am I looking at" from across a
 * desk. Painting them keeps the answer the same everywhere and matches the rounded frames the
 * rest of the dashboard is built from.
 */
public class SegmentedControl extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int ARC = 14;

    /** How much of the background is mixed into the border colour for the track. */
    private static final float TRACK_MIX = 0.55f;

    /** How much of the surface is mixed into the accent for the chosen segment's fill. */
    private static final float PILL_MIX = 0.86f;

    /** And for its outline, which is what separates it from a track of a similar tone. */
    private static final float PILL_EDGE_MIX = 0.35f;

    private final transient ButtonGroup group = new ButtonGroup();
    private final transient List<JToggleButton> segments = new ArrayList<>();

    /** The track's padding around the segments, and the gap between them. */
    private static final int PADDING = 3;

    public SegmentedControl() {
        // GridBagLayout, as the pivot bar groups its level buttons. MigLayout gives a
        // single-row panel a platform minimum height - 46 pixels around a 24-pixel button -
        // which drew the track as a slab twice the height of the words inside it.
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        setOpaque(false);
    }

    /**
     * Adds a position.
     *
     * @param label   what the period is called
     * @param count   the figure behind it, set beside the label in a quieter tone
     * @param tooltip the fuller reading, or null
     * @param chosen  whether this is the position currently in force
     * @param action  run when this position is picked
     */
    public JToggleButton addSegment(String label, String count, String tooltip,
                                    boolean chosen, Runnable action) {
        JToggleButton segment = new JToggleButton();
        segment.setOpaque(false);
        segment.setContentAreaFilled(false);
        segment.setBorderPainted(false);
        segment.setFocusPainted(false);
        segment.setRolloverEnabled(false);
        segment.setHorizontalAlignment(SwingConstants.CENTER);
        segment.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        segment.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        segment.setToolTipText(tooltip);
        segment.putClientProperty("label", label);
        segment.putClientProperty("count", count);
        segment.setSelected(chosen);
        segment.addActionListener(event -> {
            restyle();
            action.run();
        });

        GridBagConstraints placement = new GridBagConstraints();
        placement.insets = new Insets(0, segments.isEmpty() ? 0 : PADDING, 0, 0);

        group.add(segment);
        segments.add(segment);
        add(segment, placement);
        restyle();
        return segment;
    }

    /** Empties the control, so a reload can rebuild it. */
    public void clearSegments() {
        for (JToggleButton segment : segments) {
            group.remove(segment);
        }
        segments.clear();
        removeAll();
    }

    /**
     * Re-labels every segment for the current selection.
     *
     * <p>The two halves of a segment are coloured independently - the period leads, the count
     * follows in a quieter tone - which a button's single foreground cannot express, so the
     * text is small HTML and is rewritten whenever the selection moves.
     */
    private void restyle() {
        for (JToggleButton segment : segments) {
            boolean chosen = segment.isSelected();
            segment.setFont(segment.getFont().deriveFont(chosen ? Font.BOLD : Font.PLAIN));
            segment.setText(html(
                    String.valueOf(segment.getClientProperty("label")),
                    String.valueOf(segment.getClientProperty("count")),
                    chosen));
        }
        repaint();
    }

    private static String html(String label, String count, boolean chosen) {
        String labelColour = hex(chosen ? Palette.accent() : Palette.text());
        return "<html><span style='color:" + labelColour + "'>" + escape(label) + "</span>"
                + "&#160;&#160;<span style='color:" + hex(Palette.muted()) + "'>"
                + escape(count) + "</span></html>";
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D canvas = (Graphics2D) g.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            canvas.setColor(trackFill());
            canvas.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);
            canvas.setColor(Palette.border());
            canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, ARC, ARC);

            paintChosen(canvas);
        } finally {
            canvas.dispose();
        }
        super.paintComponent(g);
    }

    /** The filled pill behind whichever segment is in force. */
    private void paintChosen(Graphics2D canvas) {
        for (JToggleButton segment : segments) {
            if (!segment.isSelected()) {
                continue;
            }
            int arc = ARC - PADDING;
            canvas.setColor(pillFill());
            canvas.fillRoundRect(segment.getX(), segment.getY(),
                    segment.getWidth() - 1, segment.getHeight() - 1, arc, arc);
            // An outline as well as a fill. Under a look and feel whose border tone is
            // already close to the accent - Metal's is blue - the two fills come out within
            // a few values of each other and the chosen segment stops looking chosen.
            canvas.setColor(pillEdge());
            canvas.drawRoundRect(segment.getX(), segment.getY(),
                    segment.getWidth() - 1, segment.getHeight() - 1, arc, arc);
            return;
        }
    }

    private static Color trackFill() {
        return Palette.mix(Palette.border(), surface(), TRACK_MIX);
    }

    private static Color pillFill() {
        // Against the table surface, not the panel's: the chosen segment reads as lifted out
        // of the track, the way a selected tab reads as lifted out of its strip.
        return Palette.mix(Palette.accent(), Palette.cardBackground(), PILL_MIX);
    }

    private static Color pillEdge() {
        return Palette.mix(Palette.accent(), surface(), PILL_EDGE_MIX);
    }

    private static Color surface() {
        Color found = UIManager.getColor("Panel.background");
        return found != null ? found : Palette.cardBackground();
    }

    private static String hex(Color colour) {
        return String.format("#%02x%02x%02x",
                colour.getRed(), colour.getGreen(), colour.getBlue());
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;");
    }
}
