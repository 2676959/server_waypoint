package _959.server_waypoint.common.client.gui.render;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WidgetThemeColorsTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void doesNotExposeCompileTimeIntegerConstants() {
        assertFalse(Arrays.stream(WidgetThemeColors.class.getDeclaredFields())
                .anyMatch(field -> field.getType() == int.class && Modifier.isStatic(field.getModifiers())));
    }

    @Test
    void colorLookupUsesTheCurrentTheme() {
        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFF010203);

        assertEquals(0xFF010203, WidgetThemeColors.getColor(WidgetThemeVariable.TEXT_PRIMARY));
    }

    @Test
    void colorSupplierResolvesThemeChangesDynamically() {
        IntSupplier color = WidgetThemeColors.getColorSupplier(WidgetThemeVariable.TEXT_PRIMARY);

        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFF010203);
        assertEquals(0xFF010203, color.getAsInt());

        WidgetThemeManager.setColor(WidgetThemeVariable.TEXT_PRIMARY, 0xFF040506);
        assertEquals(0xFF040506, color.getAsInt());
    }
}
