package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.TestFont;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ComboBoxMatchingColorTest {
    @Test
    void bareVanillaDimensionResolvesWithoutReplacingTheTypedText() {
        ColorRecordingCombo combo = new ColorRecordingCombo();
        combo.useResourceIdMatching();
        combo.focused = true;
        combo.setValue("overworld");
        assertEquals("overworld", combo.getValue());
        assertEquals("minecraft:overworld", combo.getResolvedValue());
        assertEquals(getColor(SUCCESS), combo.color.getAsInt());
        combo.focused = false;
        assertEquals(WidgetThemeState.text(true), combo.color.getAsInt());
    }

    @Test
    void customDimensionNeedsItsFullIdForValidationAndFollowsCatalogUpdates() {
        ColorRecordingCombo combo = new ColorRecordingCombo();
        combo.useResourceIdMatching();
        combo.setValues(List.of("examplemod:moon_base"));
        combo.focused = true;
        combo.setValue("moon_base");
        assertEquals(getColor(DANGER), combo.color.getAsInt());
        combo.setValue("examplemod:moon_base");
        assertEquals(getColor(SUCCESS), combo.color.getAsInt());
        assertEquals("examplemod:moon_base", combo.getResolvedValue());
        combo.setValues(List.of("minecraft:overworld"));
        assertEquals(getColor(DANGER), combo.color.getAsInt());
    }

    @Test
    void exactChoiceUsesSuccessWhenFocusedAndNormalWhenUnfocused() {
        ColorRecordingCombo combo = new ColorRecordingCombo();
        combo.useMatchingValueColors();
        combo.setValue("minecraft:overworld");
        combo.focused = true;
        assertEquals(getColor(SUCCESS), combo.color.getAsInt());
        combo.focused = false;
        assertEquals(WidgetThemeState.text(true), combo.color.getAsInt());
    }

    @Test
    void unmatchedAndPartialValuesUseDangerRegardlessOfFocus() {
        ColorRecordingCombo combo = new ColorRecordingCombo();
        combo.useMatchingValueColors();
        for (boolean focused : new boolean[]{true, false}) {
            combo.focused = focused;
            combo.setValue("minecraft:over");
            assertEquals(getColor(DANGER), combo.color.getAsInt());
            combo.setValue("new list");
            assertEquals(getColor(DANGER), combo.color.getAsInt());
            combo.setValue("");
            assertEquals(WidgetThemeState.text(true), combo.color.getAsInt());
        }
    }

    @Test
    void catalogChangesReevaluateTheTypedListNameWithoutChangingIt() {
        ColorRecordingCombo combo = new ColorRecordingCombo();
        combo.useMatchingValueColors();
        combo.focused = true;
        combo.setValue("Home Base");
        assertEquals(getColor(SUCCESS), combo.color.getAsInt());

        combo.setValues(List.of("Mining"));
        assertEquals("Home Base", combo.getValue());
        assertEquals(getColor(DANGER), combo.color.getAsInt());

        combo.setValues(List.of("Home Base"));
        assertEquals(getColor(SUCCESS), combo.color.getAsInt());
        combo.setValue("home base");
        assertEquals(getColor(DANGER), combo.color.getAsInt(), "list identities are case-sensitive");
    }

    /** Captures the real provider supplied to the input, without starting Minecraft text input. */
    private static final class ColorRecordingCombo extends ComboBoxWidget {
        private IntSupplier color;
        private boolean focused;

        private ColorRecordingCombo() {
            super(10, 20, 100, Component.empty(), new TestFont(),
                    List.of("minecraft:overworld", "Home Base"), "", value -> { });
        }

        @Override
        public void setTextColorProvider(IntSupplier provider) {
            super.setTextColorProvider(provider);
            this.color = provider;
        }

        @Override
        public boolean isFocused() {
            return this.focused;
        }
    }
}
