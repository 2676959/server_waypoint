package _959.server_waypoint.common.client.gui.screens;

import java.util.List;
import net.minecraft.client.gui.components.events.AbstractContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigScreenFocusTest {
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
