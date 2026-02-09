package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.handlers.HandlerForXaerosMinimap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.FONT_COLOR;
import static _959.server_waypoint.util.ColorUtils.GREEN;
import static _959.server_waypoint.util.ColorUtils.RED;

public class ClientConfigScreen extends MovementAllowedScreen {
    private final Screen parentScreen;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    private final ToggleButton renderEnableToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setEnableWaypointRender);
    private final ToggleButton xaerosAutoSyncToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setAutoSyncToXaerosMinimap);
    private final IntegerSlider waypointRenderDistanceSlider = new IntegerSlider(0, 0, 0, 1024, WaypointClientMod.getClientConfig().getViewDistance(), WaypointClientMod.getClientConfig()::setViewDistance, textRenderer);
    private final TranslucentButton syncToXaerosButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("server_waypoint.config.confirm_sync"), this::openXaerosSyncConfirmationDialog);
    private final ConfirmationDialog xaerosSyncConfirmationDialog;

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
        row4.addChild(syncToXaerosButton);
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

        WidgetStack xaerosSyncWarningContent = new WidgetStack(0, 0, 5, true, false);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.1"), 1F, FONT_COLOR, 200, textRenderer), 0);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.2"), 1F, GREEN, 200, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.3"), 1F, FONT_COLOR, 200, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.4"), 1F, RED, 200, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.5"), 1F, FONT_COLOR, 200, textRenderer));
        this.xaerosSyncConfirmationDialog = new ConfirmationDialog(0, 0, 200, 100, Text.translatable("server_waypoint.config.sync_to_xaeros"), xaerosSyncWarningContent, this::runXaerosSync, this::closeXaerosSyncConfirmationDialog, textRenderer);
        this.xaerosSyncConfirmationDialog.visible = false;
    }

    private void runXaerosSync() {
        HandlerForXaerosMinimap.syncFromServerWaypointMod();
        this.closeXaerosSyncConfirmationDialog();
    }

    private void openXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = true;
        this.xaerosSyncConfirmationDialog.forEachChild(button -> button.active = true);
        this.setFocused(this.xaerosSyncConfirmationDialog);
        this.renderEnableToggle.active = false;
        this.xaerosAutoSyncToggle.active = false;
        this.waypointRenderDistanceSlider.active = false;
        this.syncToXaerosButton.active = false;
    }

    private void closeXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = false;
        this.xaerosSyncConfirmationDialog.forEachChild(button -> button.active = false);
        this.setFocused(this.renderEnableToggle);
        this.renderEnableToggle.active = true;
        this.xaerosAutoSyncToggle.active = true;
        this.waypointRenderDistanceSlider.active = true;
        this.syncToXaerosButton.active = true;
    }

    @Override
    public void init() {
        super.init();
        this.addDrawableChild(renderEnableToggle);
        this.addDrawableChild(xaerosAutoSyncToggle);
        this.addDrawableChild(waypointRenderDistanceSlider);
        this.addDrawableChild(syncToXaerosButton);
        this.xaerosSyncConfirmationDialog.forEachChild(this::addDrawableChild);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, WidgetThemeColors.TRANSPARENT_BG_COLOR);
        this.mainLayout.render(context, mouseX, mouseY, delta);
        int centeredX = centered(this.width, this.xaerosSyncConfirmationDialog.getWidth());
        int centeredY = centered(this.height, this.xaerosSyncConfirmationDialog.getHeight());
        this.xaerosSyncConfirmationDialog.setPosition(centeredX, centeredY);
        context.getMatrices().translate(0, 0, 1);
        this.xaerosSyncConfirmationDialog.render(context, mouseX, mouseY, delta);
        context.getMatrices().translate(0, 0, -1);
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
