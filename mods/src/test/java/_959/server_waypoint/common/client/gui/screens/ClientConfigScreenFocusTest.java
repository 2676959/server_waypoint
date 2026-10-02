package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigScreenFocusTest {
    // A row holding a 13-pixel button is 21 pixels high: the button plus 4 above and below, at least 21.
    private static final int ROW_HEIGHT = 21;

    @Test
    void handingOverFocusAfterAClickEndsTheClicksDrag() {
        TestContainer screen = new TestContainer();
        Listener resetButton = new Listener();
        Listener slider = new Listener();
        // What vanilla does for a left click on the reset button: focus it and start a drag.
        screen.setFocused(resetButton);
        screen.setDragging(true);

        ClientConfigScreen.handOverFocus(screen, slider);

        assertSame(slider, screen.getFocused());
        assertTrue(slider.isFocused());
        // Vanilla sends a drag to the focused widget, which would move the slider to the pointer.
        assertFalse(screen.isDragging());
    }

    @Test
    void aFocusedRowWidgetThatARelayoutHidScrollsBackIntoView() {
        IconButton[] buttons = {button(), button(), button(), button(), button()};
        SettingsListWidget list = list(ROW_HEIGHT * 2, buttons);
        TestContainer screen = new TestContainer();
        // As when closing a dialog focuses the Sync button that opened it, after a longer status
        // made the list shorter.
        screen.setFocused(buttons[3]);
        assertFalse(buttons[3].visible);

        ClientConfigScreen.keepFocusVisible(screen, list);

        assertTrue(buttons[3].visible);
        assertSame(buttons[3], screen.getFocused());
    }

    @Test
    void aFocusedRowWidgetInViewLeavesTheListWhereItIs() {
        IconButton[] buttons = {button(), button(), button()};
        SettingsListWidget list = list(ROW_HEIGHT * 2, buttons);
        TestContainer screen = new TestContainer();
        // The next row is out of view, which revealing the focused one would change.
        screen.setFocused(buttons[1]);

        ClientConfigScreen.keepFocusVisible(screen, list);

        assertEquals(0.0, list.getScrollY());
        assertSame(buttons[1], screen.getFocused());
    }

    @Test
    void focusLeavesARowWidgetTallerThanTheList() {
        IconButton tall = button(40);
        SettingsListWidget list = list(30, button(), tall);
        TestContainer screen = new TestContainer();
        screen.setFocused(tall);

        ClientConfigScreen.keepFocusVisible(screen, list);

        assertNull(screen.getFocused());
        assertFalse(tall.isFocused());
    }

    /** A list {@code height} pixels high and 200 wide, with a row for each control. */
    private static SettingsListWidget list(int height, IconButton... controls) {
        SettingsListWidget list = new SettingsListWidget(new TestFont());
        List<SettingsListWidget.Entry> rows = new ArrayList<>();
        for (IconButton control : controls) {
            rows.add(new SettingsListWidget.Row(Component.literal("Row"), control));
        }
        list.setEntries(rows);
        list.setWidth(200);
        list.setHeight(height);
        return list;
    }

    private static IconButton button() {
        return button(13);
    }

    private static IconButton button(int height) {
        return new IconButton(0, 0, 13, height, Component.literal("Button"), null, () -> {
        });
    }

    private static final class TestContainer extends AbstractContainerEventHandler {
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }
    }

    private static final class Listener implements GuiEventListener {
        private boolean focused;

        @Override
        public void setFocused(boolean focused) {
            this.focused = focused;
        }

        @Override
        public boolean isFocused() {
            return this.focused;
        }
    }
}
