package com.finboxsolutions.easyrec.dashboard.ui.component;

import java.awt.Color;

import javax.swing.Icon;

import com.finboxsolutions.common.gui.fonticons.builder.AwesomeFactory;
import com.finboxsolutions.common.gui.fonticons.symbols.AwesomeUnicodeConstants;
import com.finboxsolutions.easyrec.dashboard.ui.GuiPreferences;

/**
 * The dashboard's icons, in one place, the way {@code EasyRecIcons} holds EasyRec's.
 *
 * <p>Font icons rather than images, for the same reason the rest of the application uses
 * them: they take the look and feel's foreground colour, scale with the font, and need no
 * resource files shipped alongside the module.
 *
 * <p>Every icon is built through {@link #icon}, which swallows a failure and returns null.
 * A button with a null icon is a button with no icon; a static initialiser that threw would
 * be an {@code ExceptionInInitializerError} the first time the tab is opened, and would take
 * the whole dashboard with it.
 */
public final class DashboardIcons {

    /** Toolbar icons, sized to sit beside a default-height button's text. */
    private static final int TOOLBAR_SIZE = 15;

    /** Icons that carry a KPI caption or a section title, one step smaller. */
    private static final int CAPTION_SIZE = 13;

    // -------------------------------------------------------------------------- navigation

    public static final Icon ICON_BACK = icon(AwesomeUnicodeConstants.FA_ARROW_LEFT, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_HOME = icon(AwesomeUnicodeConstants.FA_HOME, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_BATCHES = icon(AwesomeUnicodeConstants.FA_CUBES, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_REFRESH = icon(AwesomeUnicodeConstants.FA_REFRESH, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_CRUMB = icon(AwesomeUnicodeConstants.FA_ANGLE_RIGHT, GuiPreferences.DARK_GRAY, CAPTION_SIZE);

    // ------------------------------------------------------------------------------ screens

    public static final Icon ICON_BATCH = icon(AwesomeUnicodeConstants.FA_CUBE, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_RECONCILIATION = icon(AwesomeUnicodeConstants.FA_BALANCE_SCALE, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_COMPARE = icon(AwesomeUnicodeConstants.FA_EXCHANGE, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_HISTORY = icon(AwesomeUnicodeConstants.FA_HISTORY, GuiPreferences.COLOR_BLUE);

    // ------------------------------------------------------------------------------ actions

    public static final Icon ICON_FILTER = icon(AwesomeUnicodeConstants.FA_FILTER, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_FILTER_CLEAR = icon(AwesomeUnicodeConstants.FA_ERASER, GuiPreferences.COLOR_RED);
    public static final Icon ICON_SEARCH = icon(AwesomeUnicodeConstants.FA_SEARCH, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_CALENDAR = icon(AwesomeUnicodeConstants.FA_CALENDAR_O, GuiPreferences.DARK_GRAY);
    public static final Icon ICON_OPEN = icon(AwesomeUnicodeConstants.FA_LIST, GuiPreferences.COLOR_BLUE);

    /** Editing one field in place: the pencil, then commit or discard. */
    public static final Icon ICON_EDIT = icon(AwesomeUnicodeConstants.FA_PENCIL, GuiPreferences.COLOR_BLUE);
    public static final Icon ICON_SAVE = icon(AwesomeUnicodeConstants.FA_CHECK, GuiPreferences.COLOR_GREEN);
    public static final Icon ICON_CANCEL = icon(AwesomeUnicodeConstants.FA_TIMES, GuiPreferences.COLOR_RED);

    // ------------------------------------------------------------------------------ figures

    public static final Icon ICON_RATE = icon(AwesomeUnicodeConstants.FA_PERCENT, GuiPreferences.COLOR_BLUE, CAPTION_SIZE);
    public static final Icon ICON_PASSED = icon(AwesomeUnicodeConstants.FA_CHECK_CIRCLE, GuiPreferences.COLOR_GREEN, CAPTION_SIZE);
    public static final Icon ICON_FAILED = icon(AwesomeUnicodeConstants.FA_TIMES_CIRCLE, GuiPreferences.COLOR_RED, CAPTION_SIZE);
    public static final Icon ICON_BREAKS = icon(AwesomeUnicodeConstants.FA_EXCLAMATION_TRIANGLE, GuiPreferences.COLOR_DARK_ORANGE, CAPTION_SIZE);
    public static final Icon ICON_ROWS = icon(AwesomeUnicodeConstants.FA_TABLE, GuiPreferences.DARK_GRAY, CAPTION_SIZE);
    public static final Icon ICON_COLUMNS = icon(AwesomeUnicodeConstants.FA_COLUMNS, GuiPreferences.DARK_GRAY, CAPTION_SIZE);
    public static final Icon ICON_PIVOT = icon(AwesomeUnicodeConstants.FA_SITEMAP, GuiPreferences.DARK_GRAY, CAPTION_SIZE);
    public static final Icon ICON_CHART = icon(AwesomeUnicodeConstants.FA_LINE_CHART, GuiPreferences.DARK_GRAY, CAPTION_SIZE);
    public static final Icon ICON_CLOCK = icon(AwesomeUnicodeConstants.FA_CLOCK_O, GuiPreferences.DARK_GRAY, CAPTION_SIZE);
    public static final Icon ICON_CONTEXT = icon(AwesomeUnicodeConstants.FA_INFO_CIRCLE, GuiPreferences.COLOR_BLUE, CAPTION_SIZE);
    public static final Icon ICON_FORCED = icon(AwesomeUnicodeConstants.FA_CHECK_SQUARE_O, GuiPreferences.COLOR_BLUE, CAPTION_SIZE);

    // ----------------------------------------------------------------------------- movement

    /** Movement indicators for a KPI's detail line; coloured at build time, not at paint time. */
    public static final Icon ICON_UP_GOOD = icon(AwesomeUnicodeConstants.FA_CARET_UP, GuiPreferences.COLOR_GREEN, CAPTION_SIZE);
    public static final Icon ICON_UP_BAD = icon(AwesomeUnicodeConstants.FA_CARET_UP, GuiPreferences.COLOR_RED, CAPTION_SIZE);
    public static final Icon ICON_DOWN_GOOD = icon(AwesomeUnicodeConstants.FA_CARET_DOWN, GuiPreferences.COLOR_GREEN, CAPTION_SIZE);
    public static final Icon ICON_DOWN_BAD = icon(AwesomeUnicodeConstants.FA_CARET_DOWN, GuiPreferences.COLOR_RED, CAPTION_SIZE);
    public static final Icon ICON_FLAT = icon(AwesomeUnicodeConstants.FA_MINUS, GuiPreferences.DARK_GRAY, CAPTION_SIZE);

    private DashboardIcons() {
    }

    /**
     * The movement icon for a signed delta.
     *
     * @param higherIsBetter false for a figure like a break count, where a fall is the good news
     */
    public static Icon movement(double delta, boolean higherIsBetter) {
        if (delta == 0.0d) {
            return ICON_FLAT;
        }
        boolean improved = higherIsBetter == (delta > 0.0d);
        if (delta > 0.0d) {
            return improved ? ICON_UP_GOOD : ICON_UP_BAD;
        }
        return improved ? ICON_DOWN_GOOD : ICON_DOWN_BAD;
    }

    private static Icon icon(char unicode, Color colour) {
        return icon(unicode, colour, TOOLBAR_SIZE);
    }

    private static Icon icon(char unicode, Color colour, int size) {
        try {
            return AwesomeFactory.getInstance().buildFontIcon(unicode, colour, size);
        } catch (RuntimeException failure) {
            // The icon font is loaded from JFontIcons' own resources. If that ever fails,
            // an iconless dashboard is a far better outcome than no dashboard.
            return null;
        }
    }
}
