package com.finboxsolutions.easyrec.dashboard.ui.component;

import java.awt.Color;

import javax.swing.UIManager;

import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.model.StatusLabel;
import com.finboxsolutions.easyrec.dashboard.service.Rates;
import com.finboxsolutions.easyrec.dashboard.ui.GuiPreferences;

/**
 * The dashboard's semantic colours, resolved against the current look and feel.
 *
 * <p>Two sources, in this order. Where EasyRec already names a colour, {@link GuiPreferences}
 * wins, so the dashboard reads as part of the same application rather than as a panel with
 * a palette of its own. Where it does not - the pass, warn and fail bands, which are a
 * dashboard idea - the colour is looked up from UIManager and only falls back to a literal,
 * so a FlatLaf theme switch repaints correctly.
 *
 * <p>Call these at paint time rather than caching the result in a field.
 */
public final class Palette {

    private Palette() {
    }

    // The three bands below are the dashboard's own idea, so they have no GuiPreferences
    // constant to defer to. If EasyRec names a pass/warn/fail triple somewhere, these three
    // methods are the only places to change.
    public static Color success() {
        return uiColor("Component.custom.successColor", new Color(0x10B981));
    }

    public static Color warning() {
        return uiColor("Component.custom.warningColor", new Color(0xF59E0B));
    }

    public static Color error() {
        return uiColor("Component.custom.errorColor", new Color(0xEF4444));
    }

    public static Color accent() {
        return GuiPreferences.COLOR_BLUE;
    }

    /**
     * Secondary text: captions, field names, the note under a control.
     *
     * <p>Resolved from UIManager on every call rather than from
     * {@code GuiPreferences.DARK_GRAY}, which is a {@code static final} bound to whichever
     * theme happened to be installed when that class first loaded. Reading it here would
     * freeze the dashboard's caption colour at start-up and leave it unreadable after a
     * theme switch - the one thing this class exists to avoid.
     */
    public static Color muted() {
        Color found = UIManager.getColor("Label.disabledForeground");
        if (found == null) {
            found = UIManager.getColor("TextField.foreground");
        }
        return found != null ? found : Color.GRAY;
    }

    /** The green EasyRec already uses for Excel export, for anything spreadsheet-related. */
    public static Color excel() {
        return GuiPreferences.COLOR_GREEN_EXCEL;
    }

    /**
     * The line a frame, a rule or a grid is drawn in.
     *
     * <p>{@code Component.borderColor} is a FlatLaf key: Metal, Windows and Nimbus do not
     * define it, and falling back to a literal there gave the dashboard a dark slate frame
     * on a light grey desktop. {@code controlShadow} is the border tone every one of them
     * does define - #b8cfe5 under Metal, #a0a0a0 under Windows, #ccd3e0 under Nimbus - so it
     * is asked second, and the literal is only reached by a look and feel that names neither.
     */
    public static Color border() {
        Color found = UIManager.getColor("Component.borderColor");
        if (found == null) {
            found = UIManager.getColor("controlShadow");
        }
        if (found == null) {
            found = UIManager.getColor("Table.gridColor");
        }
        return found != null ? found : new Color(0xC2C2C2);
    }

    public static Color cardBackground() {
        return uiColor("Table.background", UIManager.getColor("Panel.background"));
    }

    public static Color text() {
        return uiColor("Label.foreground", Color.DARK_GRAY);
    }

    /** The colour band of a match rate: the same thresholds every screen uses. */
    public static Color forRate(Double rate) {
        return switch (Rates.toneOf(rate)) {
            case GOOD -> success();
            case WARN -> warning();
            case BAD -> error();
            case NONE -> muted();
        };
    }

    public static Color forStatus(StatusLabel status) {
        return switch (status) {
            case PASSED -> success();
            case FAILED -> error();
            // An execution error is not the same as a run that completed and found breaks,
            // so it gets its own colour rather than reusing FAILED's.
            case ERROR -> warning();
            case UNKNOWN -> muted();
        };
    }

    /**
     * The colour of one pivot cell, from its column's tone and its value.
     *
     * <p>GOOD is always coloured; WARN and BAD fire only on a non-zero count, so a quiet row
     * stays quiet; SIGNED reads the sign; PLAIN is never coloured.
     */
    public static Color forPivot(PivotMetric.Tone tone, double value) {
        return switch (tone) {
            case SIGNED -> value > 0 ? success() : (value < 0 ? error() : null);
            case GOOD -> success();
            case WARN -> value == 0.0d ? null : warning();
            case BAD -> value == 0.0d ? null : error();
            case PLAIN -> null;
        };
    }

    /**
     * {@code colour} with {@code weight} of {@code towards} mixed into it.
     *
     * <p>How the dashboard derives a tint from a semantic colour: the same green over white
     * and over near-black, at one weight, is a pastel and a dark wash respectively, so a
     * badge or a track written this way follows a theme switch without a second palette.
     */
    public static Color mix(Color colour, Color towards, float weight) {
        if (towards == null) {
            return colour;
        }
        float keep = 1f - weight;
        return new Color(
                Math.round(colour.getRed() * keep + towards.getRed() * weight),
                Math.round(colour.getGreen() * keep + towards.getGreen() * weight),
                Math.round(colour.getBlue() * keep + towards.getBlue() * weight));
    }

    private static Color uiColor(String key, Color fallback) {
        Color found = UIManager.getColor(key);
        return found != null ? found : fallback;
    }
}