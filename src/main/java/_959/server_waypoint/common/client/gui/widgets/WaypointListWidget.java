package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.render.WaypointRenderData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class WaypointListWidget extends ElementListWidget<WaypointListWidget.WaypointEntry> {
    private final static int BASE_WIDTH = 210;
    private final static int HALF_WIDTH = BASE_WIDTH / 2;
    private final WaypointClientMod waypointClientMod;
//    private final MinecraftClient client;

    public WaypointListWidget(MinecraftClient minecraftClient, WaypointClientMod waypointClientMod, Screen screen, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
        this.waypointClientMod = waypointClientMod;
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 3F, 5.5F), 0x33BBAD, "储", "全物品", "全")));
        this.addEntry(new WaypointEntry(screen, new WaypointRenderData(new Vector3f(1.5F, 0F, 5.5F), 0xAACCFF, "World", "Spawn", "SP")));

    }

    @Override
    public int getRowWidth() {
        return BASE_WIDTH;
    }

    public static class WaypointEntry extends ElementListWidget.Entry<WaypointEntry> {
        private static final Matrix4f identity = new Matrix4f();
        private final Screen screen;
        private final TextWidget waypointNameWidget;
        private final List<ClickableWidget> widgets = new ArrayList<>();
        private final int rgb;
        private final String shortName;
        private final String waypointName;
        private final Vector3f pos;

        WaypointEntry(Screen screen, WaypointRenderData data) {
            this(screen, data.initials(), data.name(), data.rgb(), data.pos());
        }

        WaypointEntry(Screen screen, String shortName, String waypointName, int rgb, Vector3f pos) {
            this.screen = screen;
            this.rgb = rgb;
            this.pos = pos;
            this.shortName = shortName;
            this.waypointName = waypointName;
            TextRenderer textRenderer = this.screen.getTextRenderer();
            waypointNameWidget = new TextWidget(Text.literal(waypointName), textRenderer);
            this.widgets.add(waypointNameWidget);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return this.widgets;
        }

        @Override
        public List<? extends Element> children() {
            return this.widgets;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickProgress) {
            waypointNameWidget.setPosition(this.screen.width / 2 - HALF_WIDTH + 15, y);
            waypointNameWidget.render(context, mouseX, mouseY, tickProgress);
            TextRenderer textRenderer = this.screen.getTextRenderer();
            int color = 0xFF000000 + this.rgb;
            context.draw((vertexConsumerProvider -> {
                textRenderer.draw(this.shortName, (float) this.screen.width / 2 - HALF_WIDTH, y, 0xFFFFFFFF, true, identity, vertexConsumerProvider, TextRenderer.TextLayerType.SEE_THROUGH, color, 0xFF);
            }));
        }
    }
}
