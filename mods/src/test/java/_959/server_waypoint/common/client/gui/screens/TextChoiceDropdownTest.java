package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.widgets.AbstractDropdownMenuWidget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChoiceDropdownTest {
    @Test
    void theTriggerAndItsChoicesAreALineOfTextPlusTwoPixelsHigh() {
        Dropdown dropdown = new Dropdown(72, new ArrayList<>());

        assertEquals(72, dropdown.getWidth());
        assertEquals(11, dropdown.getHeight());
        for (AbstractDropdownMenuWidget.AbstractMenuItem choice : dropdown.getMenuItems()) {
            assertEquals(72, choice.getWidth());
            assertEquals(11, choice.getHeight());
        }
    }

    @Test
    void aChoiceRunsItsActionAndClosesThePopup() {
        List<String> chosen = new ArrayList<>();
        Dropdown dropdown = new Dropdown(72, chosen);
        dropdown.setPosition(10, 20);

        assertTrue(dropdown.mouseClicked(15, 25, InputConstants.MOUSE_BUTTON_LEFT));
        assertTrue(dropdown.isExpanded());
        // The popup opens downward: One at y 31, Two at y 42 and Three at y 53.
        assertTrue(dropdown.mouseClicked(15, 46, InputConstants.MOUSE_BUTTON_LEFT));

        assertEquals(List.of("Two"), chosen);
        assertFalse(dropdown.isExpanded());
    }

    @Test
    void aTriggerHasRoomForItsLabelTheTextInsetAndTheArrow() {
        assertEquals(2 + 100 + 14, TextChoiceDropdown.triggerWidth(100));
    }

    private static final class Dropdown extends TextChoiceDropdown {
        private Dropdown(int width, List<String> chosen) {
            super(width, Component.literal("Choices"), new TestFont());
            for (String label : List.of("One", "Two", "Three")) {
                this.addMenuItem(new TextChoice(Component.literal(label), () -> chosen.add(label)));
            }
        }

        @Override
        protected Component triggerLabel() {
            return Component.literal("Choices");
        }
    }
}
