package com.finboxsolutions.easyrec.dashboard.ui.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.ActionListener;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.TitledBorder;

import com.finboxsolutions.common.gui.button.StyledIconButton;

import net.miginfocom.swing.MigLayout;

/**
 * The panel, toolbar and label idiom the rest of EasyRec is built from.
 *
 * <p>The section helpers are {@code DefaultOptionPanel}'s, kept identical - the same
 * {@code TextField.background} fill, the same rounded {@code Component.borderColor} line
 * inside a titled border, the same {@code insets 8 12 8 12} - so a dashboard screen and an
 * options screen are cut from one cloth. The toolbar helpers are
 * {@code PanelPivotActionBar}'s: font-icon buttons, {@code " | "} separators and a pushing
 * spacer that drives trailing actions to the right edge.
 *
 * <p>Static methods rather than a base class, because {@code DefaultOptionPanel} extends
 * {@code TitledPanel}, which lives in the EasyRec module the dashboard does not depend on.
 * When the dashboard moves inside that module, these become calls to the real thing.
 */
public final class Sections {

    private static final String DEFAULT_SECTION_LAYOUT = "[][grow]";
    private static final String DEFAULT_SECTION_INSETS = "insets 8 12 8 12";

    /** A section that holds a table: no inner padding, so the scroll pane meets the border. */
    private static final String FILL_SECTION_INSETS = "insets 6 8 8 8, fill";

    private Sections() {
    }

    // ============================================================================= sections

    /** A titled section with the default two-column layout. */
    public static JPanel createSection(String title) {
        return createSection(title, DEFAULT_SECTION_LAYOUT);
    }

    public static JPanel createSection(String title, String columnConstraints) {
        return createSection(title, DEFAULT_SECTION_INSETS, columnConstraints);
    }

    /** A titled section whose layout constraints need more than the default insets. */
    public static JPanel createSection(String title, String layoutConstraints,
                                       String columnConstraints) {
        JPanel panel = new JPanel(new MigLayout(layoutConstraints, columnConstraints));
        styleSectionPanel(panel, title);
        return panel;
    }

    /**
     * A titled section that gives all of its space to one growing child, for the screens
     * whose section is a table.
     */
    public static JPanel createFilledSection(String title, Component content) {
        JPanel panel = new JPanel(new MigLayout(FILL_SECTION_INSETS, "[grow,fill]", "[grow,fill]"));
        styleSectionPanel(panel, title);
        panel.add(content, "grow, push");
        return panel;
    }

    public static void styleSectionPanel(JPanel panel, String title) {
        Color sectionBg = UIManager.getColor("TextField.background");
        if (sectionBg == null) {
            sectionBg = Color.WHITE;
        }
        panel.setBackground(sectionBg);
        panel.setOpaque(true);

        Border line = BorderFactory.createLineBorder(borderColor(), 1, true);
        Border titled = BorderFactory.createTitledBorder(line, title);
        panel.setBorder(new CompoundBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0), titled));
    }

    /**
     * A card: the section's fill and rounded border, without a title.
     *
     * <p>KPI tiles and the comparison's batch headers are cards. They carry their caption
     * inside, where a titled border would have put it on the frame and cost a row of height
     * four tiles wide.
     */
    public static void styleCardPanel(JPanel panel) {
        Color sectionBg = UIManager.getColor("TextField.background");
        if (sectionBg == null) {
            sectionBg = Color.WHITE;
        }
        panel.setBackground(sectionBg);
        panel.setOpaque(true);
        panel.setBorder(BorderFactory.createLineBorder(borderColor(), 1, true));
    }

    // ============================================================================= toolbars

    /**
     * An action bar: the pivot bar's tight insets and its rule underneath.
     *
     * <p>The rule is what separates a toolbar from the screen it acts on; without it the
     * buttons read as the first row of content.
     */
    public static JPanel createToolBar() {
        JPanel bar = new JPanel(new MigLayout("insets 4 8 4 8", "[]", "[]"));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderColor()));
        return bar;
    }

    /** The pivot bar's {@code " | "} separator, so groups of buttons read as groups. */
    public static JComponent separator() {
        JLabel separator = new JLabel(" | ");
        separator.setForeground(Color.LIGHT_GRAY);
        return separator;
    }

    /** Add with {@code "push, growx"} to drive everything after it to the right edge. */
    public static JComponent spacer() {
        return new JLabel();
    }

    // ============================================================================== buttons

    /** A borderless icon-only button, as the options panels build them. */
    public static JButton createIconButton(Icon icon, String tooltip, ActionListener action) {
        JButton button = new StyledIconButton(icon);
        button.setToolTipText(tooltip);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        if (action != null) {
            button.addActionListener(action);
        }
        return button;
    }

    /** An icon beside a label, for the actions worth naming. */
    public static JButton createButton(Icon icon, String text, String tooltip, ActionListener action) {
        JButton button = new JButton(text, icon);
        button.setToolTipText(tooltip);
        button.setIconTextGap(6);
        if (action != null) {
            button.addActionListener(action);
        }
        return button;
    }

    public static void makeTransparent(AbstractButton... buttons) {
        if (buttons == null) {
            return;
        }
        for (AbstractButton button : buttons) {
            if (button != null) {
                button.setOpaque(false);
            }
        }
    }

    // =============================================================================== labels

    /** The italic grey note the options panels put under a control. */
    public static JLabel createHint(String text) {
        JLabel label = new JLabel("<html><i>" + text + "</i></html>");
        label.setFont(label.getFont().deriveFont(Font.ITALIC, 11f));
        label.setForeground(Palette.muted());
        return label;
    }

    /** A screen's heading: an icon, then the name, at the size a screen title is set in. */
    public static JLabel createTitle(String text, Icon icon) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 16f));
        label.setIconTextGap(8);
        return label;
    }

    /** A caption over a table or a chart. */
    public static JLabel createCaption(String text, Icon icon) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        label.setIconTextGap(6);
        return label;
    }

    /**
     * A field's label: small and grey, so the value beside it carries the weight.
     *
     * <p>The trailing pixels are not decoration. A label whose preferred width is exactly
     * its text width gets clipped by a pixel when the layout is tight, which turns
     * "Description" into "Descriptior" - readable enough to miss, wrong enough to notice.
     */
    public static JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(Palette.muted());
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 3));
        return label;
    }

    /**
     * Retitles a section built by {@link #createSection} or {@link #createFilledSection}.
     *
     * <p>The screens whose section title carries a count - ''Recent batches (12 of 40)'' -
     * rewrite it on every load, and rebuilding the border each time would drop the panel's
     * insets along with it.
     */
    public static void setSectionTitle(JPanel panel, String title) {
        Border border = panel.getBorder();
        if (border instanceof CompoundBorder compound) {
            border = compound.getInsideBorder();
        }
        if (border instanceof TitledBorder titled) {
            titled.setTitle(title);
            panel.repaint();
        }
    }

    /** One definition of the line every frame in the dashboard is drawn in. */
    static Color borderColor() {
        return Palette.border();
    }
}
