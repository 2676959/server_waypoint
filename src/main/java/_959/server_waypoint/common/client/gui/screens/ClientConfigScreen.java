package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.handlers.HandlerForXaerosMinimap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClientConfigScreen extends MovementAllowedScreen {
    private final Screen parentScreen;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    private final ToggleButton renderEnableToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setEnableWaypointRender);
    private final ToggleButton xaerosAutoSyncToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setAutoSyncToXaerosMinimap);
    private final IntegerSlider waypointRenderDistanceSlider = new IntegerSlider(0, 0, 0, 1024, WaypointClientMod.getClientConfig().getViewDistance(), WaypointClientMod.getClientConfig()::setViewDistance, textRenderer);
    private final TranslucentButton addToXaerosButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("server_waypoint.config.confirm"), HandlerForXaerosMinimap::syncFromServerWaypointMod);

    public ClientConfigScreen(Screen parentScreen) {
        super(Text.empty());
        this.parentScreen = parentScreen;
        ScalableText title = new ScalableText(0, 0, Text.translatable("waypoint.config.screen.title"), 1.2F, 0xFFFFFFFF, textRenderer);
        title.setXOffset(5);
        WidgetStack row1 = new WidgetStack(0, 0, 8);
        row1.addChild(new ScalableText(0, 0, Text.translatable("waypoint.config.enable_waypoint_render"), 0xFFFFFFFF, textRenderer));
        row1.addChild(renderEnableToggle);
        WidgetStack row2 = new WidgetStack(0, 0, 8);
        row2.addChild(new ScalableText(0, 0, Text.translatable("waypoint.config.auto_sync_to_xaeros"), 0xFFFFFFFF, textRenderer));
        row2.addChild(xaerosAutoSyncToggle);
        WidgetStack row3 = new WidgetStack(0, 0, 8);
        row3.addChild(new ScalableText(0, 0, Text.translatable("waypoint.config.local_waypoint_view_distance"), 0xFFFFFFFF, textRenderer));
        row3.addChild(waypointRenderDistanceSlider);
        WidgetStack row4 = new WidgetStack(0, 0, 8);
        row4.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros"), 0xFFFFFFFF, textRenderer));
        row4.addChild(addToXaerosButton);
        renderEnableToggle.setState(WaypointClientMod.getClientConfig().isEnableWaypointRender());
        xaerosAutoSyncToggle.setState(WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap());

        mainLayout.addChild(title);
        mainLayout.addChild(row1);
        mainLayout.addChild(row2);
        mainLayout.addChild(row3);
        mainLayout.addChild(row4);
        waypointRenderDistanceSlider.setYOffset(-2);
        this.width = mainLayout.getWidth();
        this.height = mainLayout.getHeight();
    }

    @Override
    public void init() {
        super.init();
        this.addDrawableChild(renderEnableToggle);
        this.addDrawableChild(xaerosAutoSyncToggle);
        this.addDrawableChild(waypointRenderDistanceSlider);
        this.addDrawableChild(addToXaerosButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, WidgetThemeColors.TRANSPARENT_BG_COLOR);
        this.mainLayout.render(context, mouseX, mouseY, delta);
    }

    @Override
    int getContentWidth() {
        return this.width;
    }

    @Override
    int getContentHeight() {
        return this.height;
    }

    @Override
    public void close() {
        WaypointClientMod.getInstance().saveConfig();
        this.client.setScreen(parentScreen);
    }

    // TODO: implement a scrollable widget to contain the configuration options
}
