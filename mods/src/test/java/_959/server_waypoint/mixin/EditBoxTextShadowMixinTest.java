package _959.server_waypoint.mixin;

import _959.server_waypoint.common.client.gui.TestFont;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.SuggestingTextInput;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditBoxTextShadowMixinTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void customInputsAndSubclassesFollowTheLiveTheme() throws Exception {
        TestFont font = new TestFont();
        for (EditBox input : new EditBox[]{
                new SuggestingTextInput(0, 0, 100, Component.empty(), font),
                new TranslucentTextField(0, 0, 100, Component.empty(), font)}) {
            WidgetThemeManager.setBoolean(WidgetThemeVariable.TEXT_SHADOW, false);
            assertFalse(resolve(input, true));
            WidgetThemeManager.setBoolean(WidgetThemeVariable.TEXT_SHADOW, true);
            assertTrue(resolve(input, true));
            assertFalse(resolve(input, false));
        }
    }

    @Test
    void vanillaInputsKeepTheirOriginalShadowSetting() throws Exception {
        EditBox input = new EditBox(new TestFont(), 0, 0, 100, 9, Component.empty());
        WidgetThemeManager.setBoolean(WidgetThemeVariable.TEXT_SHADOW, false);
        assertTrue(resolve(input, true));
        assertFalse(resolve(input, false));
    }

    private static boolean resolve(EditBox input, boolean original) throws Exception {
        var method = EditBoxTextShadowMixin.class.getDeclaredMethod("sw$resolveTextShadow", Object.class, boolean.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, input, original);
    }
}
