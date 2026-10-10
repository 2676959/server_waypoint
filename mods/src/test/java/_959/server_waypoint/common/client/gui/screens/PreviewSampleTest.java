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

    @Test
    void samplesUseTheKeysTheSpecLists() {
        assertTrue(PreviewSample.BUTTON.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        assertFalse(PreviewSample.DISABLED_BUTTON.uses(WidgetThemeVariable.CONTROL_HOVER_BACKGROUND));
        assertEquals(Set.of(WidgetThemeVariable.SCREEN_BACKGROUND), PreviewSample.SCREEN.keys());
        assertEquals(List.of(PreviewSample.COMBOBOX, PreviewSample.DROPDOWN),
                PreviewSample.samplesOf(PreviewSample.Family.CHOICES));
    }
}
