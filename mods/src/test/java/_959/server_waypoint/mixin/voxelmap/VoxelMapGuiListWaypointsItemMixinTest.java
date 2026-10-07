//~ gui_graphics_26
//~ resource_location_import
//? if voxelmap {
package _959.server_waypoint.mixin.voxelmap;

import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.util.SyncedWaypointName;
import java.lang.reflect.Method;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?} elif >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?} elif >1.21 {
/*import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoxelMapGuiListWaypointsItemMixinTest {
    @Test
    void syncedIconUsesRowBoundsInsteadOfTheCenteredLabel() throws Exception {
        RecordingGraphics graphics = RecordingGraphics.create();
        draw(graphics, SyncedWaypointName.format("Server", "Home"), 100, 80, 320);

        assertEquals(WidgetTextures.SYNCED_ICON, graphics.texture);
        assertEquals(88, graphics.iconX);
        assertEquals(83, graphics.iconY);
        assertEquals(12, graphics.iconWidth);
        assertEquals(12, graphics.iconHeight);
        assertEquals("Home", graphics.label);
        assertEquals(320, graphics.labelX);
        assertEquals(80 + labelOffset(), graphics.labelY);
    }

    @Test
    void syncedIconFollowsMovedAndScrolledRows() throws Exception {
        RecordingGraphics graphics = RecordingGraphics.create();
        draw(graphics, SyncedWaypointName.format("Server", "Home"), 215, 37, 500);

        assertEquals(203, graphics.iconX);
        assertEquals(40, graphics.iconY);
        assertEquals(500, graphics.labelX);
    }

    @Test
    void localWaypointsKeepTheirLabelWithoutASyncIcon() throws Exception {
        RecordingGraphics graphics = RecordingGraphics.create();
        draw(graphics, "Local home", 100, 80, 320);

        assertNull(graphics.texture);
        assertEquals("Local home", graphics.label);
        assertEquals(320, graphics.labelX);
    }

    private static int labelOffset() {
        //? if >=1.21.11 {
        return 5;
        //?} else {
        /*return 3;
        *///?}
    }

    private static void draw(RecordingGraphics graphics, String name, int rowX, int rowY, int labelX)
            throws Exception {
        Entry entry = new Entry();
        //? if >=1.21.9 {
        entry.setX(rowX);
        entry.setY(rowY);
        //?}
        for (Method method : VoxelMapGuiListWaypointsItemMixin.class.getDeclaredMethods()) {
            if (method.getName().equals("sw$drawSyncedWaypointIcon") && method.getParameterCount() > 4) {
                method.setAccessible(true);
                //? if >=1.21.9 {
                method.invoke(entry, graphics, null, name, labelX, rowY + labelOffset(), 0xFF123456);
                //?} else {
                /*method.invoke(entry, graphics, null, name, labelX, rowY + labelOffset(), 0xFF123456,
                        graphics, 0, rowY, rowX, 215, 18, 0, 0, false, 0.0F);
                *///?}
                return;
            }
        }
        throw new AssertionError("Missing waypoint label redirect");
    }

    private static final class Entry extends VoxelMapGuiListWaypointsItemMixin {
        @Override
        //? if >=26 {
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
        //?} elif >=1.21.9 {
        /*public void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float delta) {
        *///?} else {
        /*public void render(GuiGraphicsExtractor graphics, int index, int y, int x, int width, int height,
                int mouseX, int mouseY, boolean hovered, float delta) {
        *///?}
        }
    }

    /** Records the actual draw calls without a Minecraft instance or GPU. */
    private static final class RecordingGraphics extends GuiGraphicsExtractor {
        private
        //$ resource_location_type_swap
        Identifier
        texture;
        private int iconX;
        private int iconY;
        private int iconWidth;
        private int iconHeight;
        private String label;
        private int labelX;
        private int labelY;

        private RecordingGraphics() {
            //? if >=1.21.11 {
            super(null, null, 0, 0);
            //?} else {
            /*super(null, null);
            *///?}
        }

        private static RecordingGraphics create() throws Exception {
            Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
            var field = unsafeClass.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return (RecordingGraphics) unsafeClass.getMethod("allocateInstance", Class.class)
                    .invoke(field.get(null), RecordingGraphics.class);
        }

        @Override
        //? if >=1.21.6 {
        public void blit(RenderPipeline pipeline,
                //$ resource_location_type_swap
                Identifier
                texture, int x, int y, float u, float v,
                int width, int height, int textureWidth, int textureHeight) {
        //?} elif >1.21 {
        /*public void blit(Function<ResourceLocation, RenderType> renderType, ResourceLocation texture, int x, int y, float u, float v,
                int width, int height, int textureWidth, int textureHeight) {
        *///?} else {
        /*public void blit(ResourceLocation texture, int x, int y, float u, float v,
                int width, int height, int textureWidth, int textureHeight) {
        *///?}
            this.texture = texture;
            this.iconX = x;
            this.iconY = y;
            this.iconWidth = width;
            this.iconHeight = height;
        }

        @Override
        //? if >=26 {
        public void centeredText(Font font, String text, int x, int y, int color) {
        //?} else {
        /*public void drawCenteredString(Font font, String text, int x, int y, int color) {
        *///?}
            this.label = text;
            this.labelX = x;
            this.labelY = y;
        }
    }
}
//?}
