//? if >= 26 {
package _959.server_waypoint.common.client.gui.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DrawContextHelperTextShadowTest {
    @AfterEach
    void resetTheme() {
        WidgetThemeManager.resetTheme();
    }

    @Test
    void allTextOverloadsResolveLiveShadowSettingAndKeepExplicitFalse() throws Exception {
        RecordingGraphics context = RecordingGraphics.create();
        Component component = Component.literal("label");
        FormattedCharSequence formatted = FormattedCharSequence.forward("label", Style.EMPTY);
        for (boolean enabled : new boolean[]{true, false, true}) {
            WidgetThemeManager.setBoolean(WidgetThemeVariable.TEXT_SHADOW, enabled);
            DrawContextHelper.drawText(context, null, "label", 0, 0, -1);
            DrawContextHelper.drawText(context, null, component, 0, 0, -1);
            DrawContextHelper.drawText(context, null, formatted, 0, 0, -1);
            DrawContextHelper.drawText(context, null, "label", 0, 0, -1, false);
            DrawContextHelper.drawText(context, null, component, 0, 0, -1, false);
            DrawContextHelper.drawText(context, null, formatted, 0, 0, -1, false);
        }
        assertEquals(List.of(true, true, true, false, false, false,
                false, false, false, false, false, false,
                true, true, true, false, false, false), context.shadows);
    }

    /** Records the drawing boundary without constructing Minecraft's atlas or using a GPU. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private List<Boolean> shadows;

        private RecordingGraphics() {
            super(null, null, 0, 0);
        }

        private static RecordingGraphics create() throws Exception {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            RecordingGraphics context = (RecordingGraphics) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(field.get(null), RecordingGraphics.class);
            context.shadows = new ArrayList<>();
            return context;
        }

        @Override
        public void text(Font font, String text, int x, int y, int color, boolean shadow) {
            this.shadows.add(shadow);
        }

        @Override
        public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
            this.shadows.add(shadow);
        }

        @Override
        public void text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
            this.shadows.add(shadow);
        }
    }
}
//?}
