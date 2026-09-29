package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which rows {@link SettingsListWidget#reveal} brings into view as the neighbors of a focused row.
 * Vanilla Tab skips inactive widgets, so a row whose widgets are all inactive can't be the next stop.
 */
class SettingsListWidgetTest {
    @Test
    void aRowWithAnActiveControlCanTakeFocus() {
        assertTrue(row(button(true), null).isInteractive());
    }

    @Test
    void aRowWhoseControlAndActionAreInactiveCannotTakeFocus() {
        assertFalse(row(button(false), button(false)).isInteractive());
    }

    @Test
    void anActiveActionLetsARowWithAnInactiveControlTakeFocus() {
        assertTrue(row(button(false), button(true)).isInteractive());
    }

    private static SettingsListWidget.Row row(IconButton control, @Nullable IconButton action) {
        SettingsListWidget.Row row = new SettingsListWidget.Row(Component.literal("Row"), control);
        if (action != null) {
            row.action(action);
        }
        return row;
    }

    private static IconButton button(boolean active) {
        IconButton button = new IconButton(0, 0, 13, 13, Component.literal("Button"), null, () -> {
        });
        button.active = active;
        return button;
    }
}
