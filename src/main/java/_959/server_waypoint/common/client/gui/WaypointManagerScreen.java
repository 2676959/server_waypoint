package _959.server_waypoint.common.client.gui;

import _959.server_waypoint.common.client.gui.widgets.WaypointListWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.Text;

public class WaypointManagerScreen extends Screen {
    public WaypointManagerScreen(Text title) {
        super(title);
    }

    @Override
    protected void init() {
        WaypointListWidget waypointListWidget = new WaypointListWidget(this.client, this, 250, 300, 10, 20);
        waypointListWidget.setX(250);
        this.addDrawableChild(waypointListWidget);

        TextWidget textWidget = new TextWidget(Text.literal("This is a TextWidget"), this.textRenderer);
        this.addDrawableChild(textWidget);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
    }
}
