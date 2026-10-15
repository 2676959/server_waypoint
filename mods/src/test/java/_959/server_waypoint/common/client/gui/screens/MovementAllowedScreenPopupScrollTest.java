//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.api.PopupOwner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAllowedScreenPopupScrollTest {
    @Test
    void theFocusedPopupGetsTheWheelBeforeTheWidgetUnderThePointer() {
        WheelPopup popup = new WheelPopup(true);
        WidgetUnderThePointer covered = new WidgetUnderThePointer();
        TestScreen screen = TestScreen.create(covered);
        screen.setFocused(popup);

        assertTrue(scroll(screen, 12.5, 34.5, -1.0));

        assertEquals(List.of(new Wheel(12.5, 34.5, -1.0)), popup.wheels);
        assertFalse(covered.scrolled, "the popup used the wheel, so it must not also reach the widget it covers");
    }

    @Test
    void aFocusedPopupThatDeclinesLeavesTheWheelToTheWidgetUnderThePointer() {
        WheelPopup popup = new WheelPopup(false);
        WidgetUnderThePointer covered = new WidgetUnderThePointer();
        TestScreen screen = TestScreen.create(covered);
        screen.setFocused(popup);

        assertTrue(scroll(screen, 12.5, 34.5, -1.0));

        assertEquals(1, popup.wheels.size());
        assertTrue(covered.scrolled);
    }

    @Test
    void aFocusedWidgetWithoutAPopupLeavesTheWheelToTheWidgetUnderThePointer() {
        WidgetUnderThePointer covered = new WidgetUnderThePointer();
        TestScreen screen = TestScreen.create(covered);
        screen.setFocused(new FocusOnlyListener());

        assertTrue(scroll(screen, 12.5, 34.5, -1.0));

        assertTrue(covered.scrolled);
    }

    /** Scrolls the screen; from 1.20.2 the event also carries a horizontal amount, which a popup must not receive. */
    private static boolean scroll(TestScreen screen, double mouseX, double mouseY, double verticalAmount) {
        //? if <= 1.20.1 {
        /*return screen.mouseScrolled(mouseX, mouseY, verticalAmount);
        *///?} else {
        return screen.mouseScrolled(mouseX, mouseY, 0.5, verticalAmount);
        //?}
    }

    private record Wheel(double mouseX, double mouseY, double verticalAmount) {
    }

    /** A focusable popup owner that records the wheel events it is offered and answers with a fixed result. */
    private static final class WheelPopup extends FocusOnlyListener implements PopupOwner {
        private final boolean scrolls;
        private final List<Wheel> wheels = new ArrayList<>();

        private WheelPopup(boolean scrolls) {
            this.scrolls = scrolls;
        }

        @Override
        public boolean closePopupIfOpen() {
            return false;
        }

        @Override
        public boolean scrollPopupIfOver(double mouseX, double mouseY, double verticalAmount) {
            this.wheels.add(new Wheel(mouseX, mouseY, verticalAmount));
            return this.scrolls;
        }
    }

    /** Stands in for whatever the popup hangs over: it is under every pointer and takes any wheel event. */
    private static final class WidgetUnderThePointer extends FocusOnlyListener {
        private boolean scrolled;

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return true;
        }

        //? if <= 1.20.1 {
        /*@Override
        public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
            this.scrolled = true;
            return true;
        }
        *///?} else {
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            this.scrolled = true;
            return true;
        }
        //?}
    }

    /** A listener that only takes and loses focus, which is all the screen asks of the focused widget. */
    private static class FocusOnlyListener implements GuiEventListener {
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

    private static final class TestScreen extends MovementAllowedScreen {
        private List<GuiEventListener> listeners;

        private TestScreen() {
            super(Component.empty());
        }

        /** Skips the field initializers, which read the Minecraft instance absent in unit tests. */
        private static TestScreen create(GuiEventListener... listeners) {
            try {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                TestScreen screen = (TestScreen) unsafeClass
                        .getMethod("allocateInstance", Class.class)
                        .invoke(unsafeField.get(null), TestScreen.class);
                screen.listeners = List.of(listeners);
                return screen;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Failed to create a screen test double", e);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.listeners;
        }

        @Override
        protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        int getContentWidth() {
            return 0;
        }

        @Override
        int getContentHeight() {
            return 0;
        }
    }
}
