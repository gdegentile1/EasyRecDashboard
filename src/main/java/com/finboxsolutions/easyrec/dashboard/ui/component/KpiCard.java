package com.finboxsolutions.easyrec.dashboard.ui.component;

import net.miginfocom.swing.MigLayout;

import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Font;

/**
 * A single headline figure with a caption and an optional movement indicator.
 *
 * <p>Styled as a card - the section fill and rounded border of every other panel in the
 * application - rather than as a bare rectangle, so a row of tiles reads as content sitting
 * on the screen instead of as four boxes drawn on it. The caption carries a small icon
 * naming the measure, the way EasyRec's own captions do.
 */
public class KpiCard extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel captionLabel = new JLabel();
    private final JLabel valueLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();

    public KpiCard(String caption) {
        this(caption, null);
    }

    public KpiCard(String caption, Icon icon) {
        super(new MigLayout("insets 10 12 10 12, wrap 1, fillx", "[grow,fill]", "[]4[]2[]"));
        Sections.styleCardPanel(this);

        captionLabel.setText(caption);
        captionLabel.setIcon(icon);
        captionLabel.setIconTextGap(6);
        captionLabel.setForeground(Palette.muted());
        captionLabel.setFont(captionLabel.getFont().deriveFont(Font.PLAIN, 11f));

        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD, 24f));
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.PLAIN, 11f));
        detailLabel.setForeground(Palette.muted());
        detailLabel.setIconTextGap(4);
        detailLabel.setHorizontalTextPosition(SwingConstants.TRAILING);

        add(captionLabel);
        add(valueLabel);
        add(detailLabel);
    }

    public void setValue(String value, Color foreground) {
        valueLabel.setText(value);
        valueLabel.setForeground(foreground != null ? foreground : Palette.text());
    }

    public void setDetail(String detail) {
        detailLabel.setIcon(null);
        detailLabel.setText(detail == null ? " " : detail);
    }

    /** Shows a signed movement, coloured by direction and pointed by a caret. */
    public void setDelta(Double delta, String unit, boolean higherIsBetter) {
        if (delta == null) {
            setDetail(null);
            detailLabel.setForeground(Palette.muted());
            return;
        }
        boolean improved = higherIsBetter == (delta >= 0);
        detailLabel.setForeground(delta == 0.0d ? Palette.muted()
                : (improved ? Palette.success() : Palette.error()));
        detailLabel.setText(String.format(java.util.Locale.ROOT, "%s%.2f%s",
                delta >= 0 ? "+" : "", delta, unit));
        // Set after the text: setDetail clears the icon, and the arrow is the fastest read
        // on the tile - the sign has to be seen before the number is.
        detailLabel.setIcon(DashboardIcons.movement(delta, higherIsBetter));
    }
}
