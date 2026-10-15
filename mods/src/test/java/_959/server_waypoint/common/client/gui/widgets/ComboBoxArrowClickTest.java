package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboBoxArrowClickTest {
    @Test
    void arrowOpensWhenTheOnlyChoiceIsAlreadyEntered() {
        List<String> changes = new ArrayList<>();
        ComboBoxWidget combo = combo(List.of("Home"), "Home", changes);

        assertTrue(clickArrow(combo));
        assertTrue(combo.isExpanded(), "the only choice must remain available to open the menu");
        assertEquals("Home", combo.getHoveredValue(20, 33));
        assertEquals("Home", combo.getValue());
        assertTrue(changes.isEmpty(), "opening a menu does not edit the value");

        assertTrue(clickArrow(combo));
        assertFalse(combo.isExpanded());
        assertTrue(clickArrow(combo));
        assertTrue(combo.isExpanded());
        assertTrue(changes.isEmpty());
    }

    @Test
    void arrowOpensWhenTheOnlyResourceChoiceMatchesABareId() {
        ComboBoxWidget combo = combo(List.of("minecraft:overworld"), "overworld", new ArrayList<>());
        combo.useResourceIdMatching();

        assertTrue(clickArrow(combo));
        assertTrue(combo.isExpanded());
        assertEquals("minecraft:overworld", combo.getHoveredValue(20, 33));
        assertEquals("overworld", combo.getValue());
    }

    @Test
    void multipleChoicesStillOmitTheCurrentValue() {
        List<String> changes = new ArrayList<>();
        ComboBoxWidget combo = combo(List.of("Home", "Mining"), "Home", changes);

        assertTrue(clickArrow(combo));
        assertTrue(combo.isExpanded());
        assertEquals("Mining", combo.getHoveredValue(20, 33));
        assertTrue(combo.mouseClicked(20, 33, InputConstants.MOUSE_BUTTON_LEFT));
        assertEquals("Mining", combo.getValue());
        assertEquals(List.of("Mining"), changes);
        assertFalse(combo.isExpanded());
    }

    @Test
    void arrowOpensASingleChoiceAfterAnArbitraryValueIsEntered() {
        ComboBoxWidget combo = combo(List.of("Home"), "New list", new ArrayList<>());

        assertTrue(clickArrow(combo));
        assertTrue(combo.isExpanded());
        assertEquals("Home", combo.getHoveredValue(20, 33));
    }

    @Test
    void emptyAndDisabledControlsDoNotOpenAMenu() {
        ComboBoxWidget empty = combo(List.of(), "New list", new ArrayList<>());
        clickArrow(empty);
        assertFalse(empty.isExpanded());

        ComboBoxWidget disabled = combo(List.of("Home"), "", new ArrayList<>());
        disabled.active = false;
        assertFalse(clickArrow(disabled));
        assertFalse(disabled.isExpanded());
    }

    private static boolean clickArrow(ComboBoxWidget combo) {
        return combo.mouseClicked(62, 23, InputConstants.MOUSE_BUTTON_LEFT);
    }

    private static ComboBoxWidget combo(List<String> choices, String value, List<String> changes) {
        return new ComboBoxWidget(10, 20, 60, Component.empty(), new TestFont(),
                choices, value, changes::add);
    }
}
