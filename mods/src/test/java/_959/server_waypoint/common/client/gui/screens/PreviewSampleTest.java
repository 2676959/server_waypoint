package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewSampleTest {
    @Test
    void everyThemeKeyIsUsedBySomeSampleOrElement() {
        for (WidgetThemeVariable key : WidgetThemeVariable.values()) {
            assertTrue(Arrays.stream(PreviewSample.values()).anyMatch(sample -> sample.uses(key)), key.getJsonName());
        }
    }

    @Test
    void everyFamilyHasASampleAndTheElementsHaveNone() {
        for (PreviewSample.Family family : PreviewSample.Family.values()) {
            assertFalse(PreviewSample.samplesOf(family).isEmpty(), family.name());
        }
        for (PreviewSample element : List.of(PreviewSample.KEY_LIST, PreviewSample.KEY_EDITOR,
                PreviewSample.PREVIEW_PANEL, PreviewSample.SCREEN)) {
            assertNull(element.family());
        }
    }

    /**
     * A sample declares what its widget draws in every state a player can put it in, but not the inactive
     * look the open color picker gives it; an element declares only its own drawing.
     */
    @Test
    void samplesDeclareTheKeysTheyDraw() {
        assertTrue(PreviewSample.BUTTON.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        assertFalse(PreviewSample.BUTTON.uses(WidgetThemeVariable.TEXT_DISABLED));
        assertEquals(Set.of(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND, WidgetThemeVariable.BORDER,
                WidgetThemeVariable.TEXT_DISABLED), PreviewSample.DISABLED_BUTTON.keys());
        // The combobox hovers in control.hoverBackground, never row.hoverBackground, and typed text brings
        // suggestions and an inline completion; the dropdown's choices hover in row.hoverBackground.
        assertTrue(PreviewSample.COMBOBOX.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        assertFalse(PreviewSample.COMBOBOX.uses(WidgetThemeVariable.ROW_HOVER_BACKGROUND));
        assertTrue(PreviewSample.COMBOBOX.uses(WidgetThemeVariable.SELECTION_BACKGROUND));
        assertTrue(PreviewSample.COMBOBOX.uses(WidgetThemeVariable.TEXT_PLACEHOLDER));
        assertTrue(PreviewSample.DROPDOWN.uses(WidgetThemeVariable.ROW_HOVER_BACKGROUND));
        assertTrue(PreviewSample.TEXT_FIELD.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        // The slider's number field is a text field.
        assertTrue(PreviewSample.SLIDER.uses(WidgetThemeVariable.FOCUS_RING));
        assertTrue(PreviewSample.SLIDER.uses(WidgetThemeVariable.TEXT_PRIMARY));
        // Every chip has an outline, and the Popup and Dialog chips write in text.primary.
        for (PreviewSample chip : List.of(PreviewSample.ACCENT_CHIP, PreviewSample.HOVERED_ACCENT_CHIP,
                PreviewSample.POPUP_CHIP, PreviewSample.DIALOG_CHIP, PreviewSample.SUCCESS_CHIP,
                PreviewSample.WARNING_CHIP, PreviewSample.DANGER_CHIP)) {
            assertTrue(chip.uses(WidgetThemeVariable.BORDER), chip.name());
        }
        assertEquals(Set.of(WidgetThemeVariable.POPUP_BACKGROUND, WidgetThemeVariable.BORDER,
                WidgetThemeVariable.TEXT_PRIMARY), PreviewSample.POPUP_CHIP.keys());
        // A click toggles the toggles, so they declare both of their states.
        assertTrue(PreviewSample.SELECTED_TOGGLE.uses(WidgetThemeVariable.CONTROL_BACKGROUND));
        assertEquals(PreviewSample.ON_TOGGLE.keys(), PreviewSample.OFF_TOGGLE.keys());
        // The preview can overflow, so its panel draws a scrollbar.
        assertTrue(PreviewSample.PREVIEW_PANEL.uses(WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE));
        assertEquals(Set.of(WidgetThemeVariable.SCREEN_BACKGROUND), PreviewSample.SCREEN.keys());
        assertEquals(List.of(PreviewSample.COMBOBOX, PreviewSample.DROPDOWN),
                PreviewSample.samplesOf(PreviewSample.Family.CHOICES));
    }
}
