package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboBoxScrollbarTest {
    @Test
    void anUnrelatedComboDoesNotConsumeAnotherFieldsScrollbarRelease() {
        ComboBoxWidget combo = combo();
        assertFalse(combo.mouseReleased(65, 120, InputConstants.MOUSE_BUTTON_LEFT));
        assertFalse(combo.mouseDragged(65, 120, InputConstants.MOUSE_BUTTON_LEFT, 0, 0));
    }

    @Test
    void choiceScrollbarDraggingStillReachesTheLastChoice() {
        ComboBoxWidget combo = combo();
        combo.setMaxPopupHeight(22);
        combo.setExpanded(true);
        assertTrue(combo.mouseClicked(65, 30, InputConstants.MOUSE_BUTTON_LEFT));
        assertTrue(combo.mouseDragged(65, 120, InputConstants.MOUSE_BUTTON_LEFT, 0, 90));
        assertEquals(29, combo.getMenuItems().get(6).getY());
        assertEquals(40, combo.getMenuItems().get(7).getY());
        assertTrue(combo.mouseReleased(65, 120, InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals("", combo.getValue());
    }

    private static ComboBoxWidget combo() {
        return new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                List.of("s1", "s2", "s3", "s4", "s5", "s6", "s7", "s8"), "", value -> { });
    }
}
