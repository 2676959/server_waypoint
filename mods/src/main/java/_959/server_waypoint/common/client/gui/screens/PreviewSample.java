package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.*;

/**
 * The theme editor's preview samples and the elements that carry markers, each with the theme keys it
 * draws. The screen builds one widget for each sample, keeps the samples of a family together, and marks
 * every sample and element that uses the selected key. A test checks that some constant uses every theme
 * key, so a new key needs a sample or an element that draws it.
 */
enum PreviewSample {
    PRIMARY_TEXT(Family.TEXT, TEXT_PRIMARY),
    MUTED_TEXT(Family.TEXT, TEXT_MUTED),
    DISABLED_TEXT(Family.TEXT, TEXT_DISABLED),
    TEXT_FIELD(Family.FIELD, CONTROL_BACKGROUND, TEXT_PLACEHOLDER, TEXT_PRIMARY, BORDER, FOCUS_RING),
    COMBOBOX(Family.CHOICES, CONTROL_BACKGROUND, TEXT_PRIMARY, BORDER, FOCUS_RING, POPUP_BACKGROUND,
            ROW_HOVER_BACKGROUND),
    DROPDOWN(Family.CHOICES, POPUP_BACKGROUND, TEXT_PRIMARY, BORDER, ROW_HOVER_BACKGROUND),
    BUTTON(Family.BUTTONS, CONTROL_BACKGROUND, CONTROL_HOVER_BACKGROUND, TEXT_PRIMARY, BORDER, FOCUS_RING),
    DISABLED_BUTTON(Family.BUTTONS, CONTROL_DISABLED_BACKGROUND, TEXT_DISABLED),
    SELECTED_TOGGLE(Family.TOGGLES, CONTROL_SELECTED_BACKGROUND, TEXT_ON_ACCENT),
    ON_TOGGLE(Family.TOGGLES, SUCCESS_BACKGROUND, TEXT_ON_ACCENT),
    OFF_TOGGLE(Family.TOGGLES, DANGER_BACKGROUND, TEXT_ON_ACCENT),
    SLIDER(Family.SLIDERS, ACCENT, CONTROL_BACKGROUND),
    DISABLED_SLIDER(Family.SLIDERS, SLIDER_THUMB_DISABLED, CONTROL_DISABLED_BACKGROUND, TEXT_DISABLED),
    ACCENT_CHIP(Family.ACCENT, ACCENT, TEXT_ON_ACCENT),
    HOVERED_ACCENT_CHIP(Family.ACCENT, ACCENT_HOVER, TEXT_ON_ACCENT),
    TOOLTIP(Family.POPUPS, POPUP_BACKGROUND, BORDER, TEXT_PRIMARY),
    POPUP_CHIP(Family.POPUPS, POPUP_BACKGROUND),
    DIALOG_CHIP(Family.POPUPS, DIALOG_BACKGROUND),
    SUCCESS_CHIP(Family.STATUS, SUCCESS, SUCCESS_BACKGROUND),
    WARNING_CHIP(Family.STATUS, WARNING, WARNING_BACKGROUND),
    DANGER_CHIP(Family.STATUS, DANGER, DANGER_BACKGROUND),
    SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB),
    ACTIVE_SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB_ACTIVE),
    DISABLED_SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB_DISABLED),

    /** The key list: its panel, its rows and its scrollbar. */
    KEY_LIST(PANEL_BACKGROUND, BORDER, TEXT_PRIMARY, TEXT_MUTED, SELECTION_BACKGROUND, ROW_HOVER_BACKGROUND,
            SCROLLBAR_TRACK, SCROLLBAR_THUMB, SCROLLBAR_THUMB_ACTIVE),
    /** The key editor's panel and its key line. */
    KEY_EDITOR(PANEL_BACKGROUND, BORDER, DECOR_LINE, TEXT_PRIMARY),
    /** The preview's panel and its header. */
    PREVIEW_PANEL(PANEL_BACKGROUND, BORDER, DECOR_LINE, TEXT_PRIMARY),
    /** The screen's background, which is marked inside the screen's edge. */
    SCREEN(SCREEN_BACKGROUND);

    private final @Nullable Family family;
    private final Set<WidgetThemeVariable> keys;

    PreviewSample(@Nullable Family family, WidgetThemeVariable first, WidgetThemeVariable... rest) {
        this.family = family;
        this.keys = Collections.unmodifiableSet(EnumSet.of(first, rest));
    }

    /** An element, which belongs to no family. */
    PreviewSample(WidgetThemeVariable first, WidgetThemeVariable... rest) {
        this(null, first, rest);
    }

    /** The family of a sample, none for an element. */
    @Nullable Family family() {
        return this.family;
    }

    /** The theme keys the sample or element draws. */
    Set<WidgetThemeVariable> keys() {
        return this.keys;
    }

    /** Whether the sample or element draws the key. */
    boolean uses(WidgetThemeVariable key) {
        return this.keys.contains(key);
    }

    /** The samples of a family, in declaration order. */
    static List<PreviewSample> samplesOf(Family family) {
        return Arrays.stream(values()).filter(sample -> sample.family == family).toList();
    }

    /** The groups the preview puts its samples in, in the order it shows them. */
    enum Family {
        TEXT,
        FIELD,
        CHOICES,
        BUTTONS,
        TOGGLES,
        SLIDERS,
        ACCENT,
        POPUPS,
        STATUS,
        SCROLLBARS
    }
}
