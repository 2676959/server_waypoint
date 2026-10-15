//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.SuggestingTextInput;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementAllowedScreenDropdownFocusTest {
    @Test
    void arrowReopensTheMenuAfterSelectingWithoutRefocusing() throws Exception {
        List<String> changes = new ArrayList<>();
        ComboBoxWidget combo = combo(changes);
        TestScreen screen = TestScreen.create(combo);

        assertTrue(screen.click(62, 23));
        assertTrue(combo.isExpanded());
        assertSame(combo, screen.getFocused());

        assertTrue(screen.click(20, 33));
        assertEquals("Mining", combo.getValue());
        assertEquals(List.of("Mining"), changes);
        assertFalse(combo.isExpanded());
        assertTrue(combo.isFocused());

        assertTrue(screen.click(62, 23));
        assertTrue(combo.isExpanded(), "reassigning the same focus must not close the reopened menu");
        assertEquals("Home", combo.getHoveredValue(20, 33));
        assertEquals(List.of("Mining"), changes, "reopening does not select another value");

        assertTrue(screen.click(62, 23));
        assertFalse(combo.isExpanded());
        assertTrue(screen.click(62, 23));
        assertTrue(combo.isExpanded());
    }

    @Test
    void reassigningTheFocusedComboPreservesItsOpenMenu() throws Exception {
        ComboBoxWidget combo = combo(new ArrayList<>());
        TestScreen screen = TestScreen.create(combo);
        screen.setFocused(combo);
        combo.setExpanded(true);

        screen.setFocused(combo);

        assertSame(combo, screen.getFocused());
        assertTrue(combo.isFocused());
        assertTrue(combo.isExpanded());
    }

    @Test
    void movingFocusToAnotherControlStillClosesTheMenu() throws Exception {
        ComboBoxWidget first = combo(new ArrayList<>());
        ComboBoxWidget second = combo(new ArrayList<>());
        TestScreen screen = TestScreen.create(first);
        screen.setFocused(first);
        first.setExpanded(true);

        screen.setFocused(second);

        assertFalse(first.isFocused());
        assertFalse(first.isExpanded());
        assertSame(second, screen.getFocused());
        assertTrue(second.isFocused());
    }

    @Test
    void clearingFocusStillClosesTheMenu() throws Exception {
        ComboBoxWidget combo = combo(new ArrayList<>());
        TestScreen screen = TestScreen.create(combo);
        screen.setFocused(combo);
        combo.setExpanded(true);

        screen.setFocused((GuiEventListener) null);

        assertNull(screen.getFocused());
        assertFalse(combo.isFocused());
        assertFalse(combo.isExpanded());
    }

    private static ComboBoxWidget combo(List<String> changes) throws Exception {
        ComboBoxWidget combo = new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                List.of("Home", "Mining", "Travel"), "Home", changes::add);
        // Keep real widget focus and menu routing, but skip Minecraft's OS text-input notification.
        var inputField = ComboBoxWidget.class.getDeclaredField("input");
        inputField.setAccessible(true);
        ((SuggestingTextInput) inputField.get(combo)).setEditable(false);
        return combo;
    }

    private static final class TestScreen extends MovementAllowedScreen {
        private ComboBoxWidget combo;

        // create() skips construction because the base reads the absent Minecraft instance.
        private TestScreen() {
            super(Component.empty());
        }

        private static TestScreen create(ComboBoxWidget combo) throws Exception {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            TestScreen screen = (TestScreen) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(unsafeField.get(null), TestScreen.class);
            screen.combo = combo;
            screen.acceptMovementKeys(true);
            return screen;
        }

        private boolean click(double x, double y) {
            //? if >= 1.21.9 {
            return this.mouseClicked(new MouseButtonEvent(x, y,
                    new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
            //?} else {
            /*return this.mouseClicked(x, y, InputConstants.MOUSE_BUTTON_LEFT);
            *///?}
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.combo);
        }

        @Override
        boolean testMovementKeysDown(int keyCode) {
            return false;
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
