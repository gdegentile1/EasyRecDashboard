package com.finboxsolutions.easyrec.dashboard.ui.component;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small line chart for a handful of points, painted with Java2D.
 *
 * <p>Deliberately not a charting library. The web dashboard it replaces drew these as
 * inline SVG for the same reason: a trend over a dozen runs does not justify a dependency,
 * and this way the chart follows the look and feel's colours for free.
 */
public class TrendChart extends JComponent {

    private static final long serialVersionUID = 1L;

    /** One plotted point. {@code value} may be null, which breaks the line rather than zeroing it. */
    public record Point(String label, Double value, String tooltip, boolean highlighted) {
    }

    private static final int PAD_LEFT = 46;
    private static final int PAD_RIGHT = 12;
    private static final int PAD_TOP = 12;
    private static final int PAD_BOTTOM = 26;

    /** Roughly how many labels fit along the x axis before they collide. */
    private static final int MAX_X_LABELS = 7;

    private final List<Point> points = new ArrayList<>();
    private String unit = "%";
    private Double axisMaximum;
    private int hoveredIndex = -1;

    public TrendChart() {
        setPreferredSize(new Dimension(760, 190));
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter tracker = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                int found = nearestIndex(event.getX());
                if (found != hoveredIndex) {
                    hoveredIndex = found;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoveredIndex = -1;
                repaint();
            }
        };
        addMouseMotionListener(tracker);
        addMouseListener(tracker);
    }

    /**
     * @param unit         appended to every axis label, e.g. {@code "%"}
     * @param axisMaximum  the top of the axis, or null to fit the data. Fixing it at 100 for
     *                     a match rate keeps two charts comparable at a glance.
     */
    public void setPoints(List<Point> newPoints, String unit, Double axisMaximum) {
        points.clear();
        points.addAll(newPoints);
        this.unit = unit;
        this.axisMaximum = axisMaximum;
        hoveredIndex = -1;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int index = nearestIndex(event.getX());
        return index < 0 ? null : points.get(index).tooltip();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D canvas = (Graphics2D) graphics.create();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int plotWidth = width - PAD_LEFT - PAD_RIGHT;
            int plotHeight = height - PAD_TOP - PAD_BOTTOM;
            if (plotWidth <= 0 || plotHeight <= 0) {
                return;
            }
            double top = axisTop();
            paintGrid(canvas, plotWidth, plotHeight, top);
            if (points.size() > 1) {
                paintLine(canvas, plotWidth, plotHeight, top);
            }
            paintMarkers(canvas, plotWidth, plotHeight, top);
            paintXLabels(canvas, plotWidth, height);
        } finally {
            canvas.dispose();
        }
    }

    private double axisTop() {
        if (axisMaximum != null) {
            return axisMaximum;
        }
        double maximum = 0.0d;
        for (Point point : points) {
            if (point.value() != null) {
                maximum = Math.max(maximum, point.value());
            }
        }
        if (maximum <= 0.0d) {
            return 1.0d;
        }
        // Round up to a readable step so the top gridline lands on a round number.
        double magnitude = Math.pow(10, Math.floor(Math.log10(maximum)));
        return Math.ceil(maximum / magnitude) * magnitude;
    }

    private void paintGrid(Graphics2D canvas, int plotWidth, int plotHeight, double top) {
        canvas.setStroke(new BasicStroke(1f));
        for (int tick = 0; tick <= 4; tick++) {
            double value = top * tick / 4.0d;
            int y = PAD_TOP + plotHeight - (int) Math.round(plotHeight * tick / 4.0d);
            canvas.setColor(Palette.border());
            canvas.drawLine(PAD_LEFT, y, PAD_LEFT + plotWidth, y);
            canvas.setColor(Palette.muted());
            String label = String.format(Locale.ROOT, "%.0f%s", value, unit);
            canvas.drawString(label, 4, y + 4);
        }
    }

    private void paintLine(Graphics2D canvas, int plotWidth, int plotHeight, double top) {
        GeneralPath line = new GeneralPath(Path2D.WIND_NON_ZERO);
        boolean started = false;
        for (int index = 0; index < points.size(); index++) {
            Double value = points.get(index).value();
            if (value == null) {
                started = false;   // a gap breaks the line instead of being drawn as zero
                continue;
            }
            float x = xOf(index, plotWidth);
            float y = yOf(value, plotHeight, top);
            if (started) {
                line.lineTo(x, y);
            } else {
                line.moveTo(x, y);
                started = true;
            }
        }
        canvas.setColor(Palette.accent());
        canvas.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvas.draw(line);
    }

    private void paintMarkers(Graphics2D canvas, int plotWidth, int plotHeight, double top) {
        for (int index = 0; index < points.size(); index++) {
            Point point = points.get(index);
            if (point.value() == null) {
                continue;
            }
            float x = xOf(index, plotWidth);
            float y = yOf(point.value(), plotHeight, top);
            int radius = index == hoveredIndex || point.highlighted() ? 5 : 3;
            canvas.setColor(Palette.forRate(point.value()));
            canvas.fillOval((int) x - radius, (int) y - radius, radius * 2, radius * 2);
            if (point.highlighted()) {
                canvas.setColor(Palette.accent());
                canvas.setStroke(new BasicStroke(2f));
                canvas.drawOval((int) x - radius - 3, (int) y - radius - 3,
                        (radius + 3) * 2, (radius + 3) * 2);
            }
        }
    }

    private void paintXLabels(Graphics2D canvas, int plotWidth, int height) {
        if (points.isEmpty()) {
            return;
        }
        canvas.setColor(Palette.muted());
        int step = Math.max(1, (int) Math.ceil(points.size() / (double) MAX_X_LABELS));
        for (int index = 0; index < points.size(); index += step) {
            String label = points.get(index).label();
            int labelWidth = canvas.getFontMetrics().stringWidth(label);
            int x = (int) xOf(index, plotWidth) - labelWidth / 2;
            canvas.drawString(label, Math.max(2, x), height - 8);
        }
    }

    private float xOf(int index, int plotWidth) {
        if (points.size() == 1) {
            return PAD_LEFT + plotWidth / 2f;
        }
        return PAD_LEFT + plotWidth * index / (float) (points.size() - 1);
    }

    private float yOf(double value, int plotHeight, double top) {
        double clamped = Math.max(0.0d, Math.min(value, top));
        return (float) (PAD_TOP + plotHeight - plotHeight * clamped / top);
    }

    private int nearestIndex(int x) {
        if (points.isEmpty()) {
            return -1;
        }
        int plotWidth = getWidth() - PAD_LEFT - PAD_RIGHT;
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int index = 0; index < points.size(); index++) {
            double distance = Math.abs(xOf(index, plotWidth) - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return bestDistance <= 18.0d ? best : -1;
    }
}
