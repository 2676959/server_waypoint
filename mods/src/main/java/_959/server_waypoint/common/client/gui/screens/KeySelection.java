package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import org.jetbrains.annotations.Nullable;

/**
 * The key list's selection rules, apart from its widgets: a click selects a key or, on the selected
 * key, clears the selection so the preview can be judged without markers, and Up and Down move the
 * selection through the keys in the order of {@link WidgetThemeVariable#values()}.
 */
final class KeySelection {
    private KeySelection() {
    }

    /** The selection after a click on {@code clicked}: that key, or none when it was already selected. */
    static @Nullable WidgetThemeVariable click(@Nullable WidgetThemeVariable selected, WidgetThemeVariable clicked) {
        return clicked == selected ? null : clicked;
    }

    /**
     * The selection after Down or Up: the next or previous key, which stays at the first or last key.
     * With nothing selected, Down selects the first key and Up the last.
     */
    static WidgetThemeVariable move(@Nullable WidgetThemeVariable selected, boolean down) {
        WidgetThemeVariable[] keys = WidgetThemeVariable.values();
        if (selected == null) {
            return down ? keys[0] : keys[keys.length - 1];
        }
        int index = selected.ordinal() + (down ? 1 : -1);
        return keys[Math.max(0, Math.min(keys.length - 1, index))];
    }
}
