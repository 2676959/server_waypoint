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
 * every sample and element that uses the selected key.
 * <p>
 * A sample lists every key it draws while the color picker is closed, in every state a player can put it
 * in: at rest, hovered, focused, with its popup open and, for a toggle, in both states. The inactive look
 * the open picker gives every sample is left out, except for the samples that always show it. An element
 * lists the keys of its own drawing, not those of the widgets placed on it. A test checks that some
 * constant uses every theme key, so a new key needs a sample or an element that draws it.
 */
enum PreviewSample {
    PRIMARY_TEXT(Family.TEXT, TEXT_PRIMARY),
    MUTED_TEXT(Family.TEXT, TEXT_MUTED),
    DISABLED_TEXT(Family.TEXT, TEXT_DISABLED),
    /** With its placeholder while it is empty and unfocused. */
    TEXT_FIELD(Family.FIELD, CONTROL_BACKGROUND, CONTROL_HOVER_BACKGROUND, BORDER, FOCUS_RING, TEXT_PRIMARY,
            TEXT_PLACEHOLDER),
    /** With its choices and, for typed text, its suggestions and their inline completion. */
    COMBOBOX(Family.CHOICES, CONTROL_BACKGROUND, CONTROL_HOVER_BACKGROUND, BORDER, FOCUS_RING, TEXT_PRIMARY,
            POPUP_BACKGROUND, SELECTION_BACKGROUND, TEXT_MUTED, TEXT_PLACEHOLDER),
    DROPDOWN(Family.CHOICES, POPUP_BACKGROUND, ROW_HOVER_BACKGROUND, BORDER, FOCUS_RING, TEXT_PRIMARY),
    BUTTON(Family.BUTTONS, CONTROL_BACKGROUND, CONTROL_HOVER_BACKGROUND, BORDER, FOCUS_RING, TEXT_PRIMARY),
    DISABLED_BUTTON(Family.BUTTONS, CONTROL_DISABLED_BACKGROUND, BORDER, TEXT_DISABLED),
    /** Normal and Selected: a click toggles it. */
    SELECTED_TOGGLE(Family.TOGGLES, CONTROL_SELECTED_BACKGROUND, CONTROL_BACKGROUND, BORDER, FOCUS_RING,
            TEXT_ON_ACCENT),
    /** On and Off: a click toggles it. */
    ON_TOGGLE(Family.TOGGLES, SUCCESS_BACKGROUND, DANGER_BACKGROUND, BORDER, FOCUS_RING, TEXT_ON_ACCENT),
    /** Off and On: a click toggles it. */
    OFF_TOGGLE(Family.TOGGLES, DANGER_BACKGROUND, SUCCESS_BACKGROUND, BORDER, FOCUS_RING, TEXT_ON_ACCENT),
    /** Its track and handle, and its number field. */
    SLIDER(Family.SLIDERS, ACCENT, CONTROL_BACKGROUND, CONTROL_HOVER_BACKGROUND, BORDER, FOCUS_RING, TEXT_PRIMARY),
    DISABLED_SLIDER(Family.SLIDERS, SLIDER_THUMB_DISABLED, CONTROL_DISABLED_BACKGROUND, BORDER, TEXT_DISABLED),
    ACCENT_CHIP(Family.ACCENT, ACCENT, BORDER, TEXT_ON_ACCENT),
    HOVERED_ACCENT_CHIP(Family.ACCENT, ACCENT_HOVER, BORDER, TEXT_ON_ACCENT),
    TOOLTIP(Family.POPUPS, POPUP_BACKGROUND, BORDER, TEXT_PRIMARY),
    POPUP_CHIP(Family.POPUPS, POPUP_BACKGROUND, BORDER, TEXT_PRIMARY),
    DIALOG_CHIP(Family.POPUPS, DIALOG_BACKGROUND, BORDER, TEXT_PRIMARY),
    SUCCESS_CHIP(Family.STATUS, SUCCESS_BACKGROUND, BORDER, SUCCESS),
    WARNING_CHIP(Family.STATUS, WARNING_BACKGROUND, BORDER, WARNING),
    DANGER_CHIP(Family.STATUS, DANGER_BACKGROUND, BORDER, DANGER),
    SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB),
    ACTIVE_SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB_ACTIVE),
    DISABLED_SCROLLBAR(Family.SCROLLBARS, SCROLLBAR_TRACK, SCROLLBAR_THUMB_DISABLED),

    /** The key list: its panel, its rows and its scrollbar, but not the chips that show each key's value. */
    KEY_LIST(PANEL_BACKGROUND, BORDER, TEXT_PRIMARY, TEXT_MUTED, SELECTION_BACKGROUND, ROW_HOVER_BACKGROUND,
            SCROLLBAR_TRACK, SCROLLBAR_THUMB, SCROLLBAR_THUMB_ACTIVE),
    /** The key editor's panel, its key line and its Alpha label, as drawn while a key is selected. */
    KEY_EDITOR(PANEL_BACKGROUND, BORDER, DECOR_LINE, TEXT_PRIMARY),
    /** The preview's panel, its header and its scrollbar. */
    PREVIEW_PANEL(PANEL_BACKGROUND, BORDER, DECOR_LINE, TEXT_PRIMARY, SCROLLBAR_TRACK, SCROLLBAR_THUMB,
            SCROLLBAR_THUMB_ACTIVE),
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
