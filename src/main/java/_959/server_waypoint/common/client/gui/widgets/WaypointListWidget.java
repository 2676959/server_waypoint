package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class WaypointListWidget extends ElementListWidget<WaypointListWidget.WaypointEntry> {

    public WaypointListWidget(MinecraftClient minecraftClient, Screen screen, int width, int height, int y, int itemHeight) {
        super(minecraftClient, width, height, y, itemHeight);
        this.addEntry(new WaypointEntry(screen, "test1"));
        this.addEntry(new WaypointEntry(screen, "test2"));
    }

    @Override
    public int getRowWidth() {
        return 210;
    }

    public static class WaypointEntry extends ElementListWidget.Entry<WaypointEntry> {
        private final Screen screen;
        private final TextWidget waypointNameWidget;
        private final List<ClickableWidget> widgets = new ArrayList<>();

        WaypointEntry(Screen screen, String waypointName) {
            this.screen = screen;
            waypointNameWidget = new TextWidget(Text.literal(waypointName), this.screen.getTextRenderer());
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
            waypointNameWidget.setPosition(this.screen.width/2 - 105, y);
            waypointNameWidget.render(context, mouseX, mouseY, tickProgress);
        }
    }
}
