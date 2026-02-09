package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.WidgetThemeColors;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.handlers.HandlerForXaerosMinimap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.ClientConfig.isXaerosMinimapLoaded;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.FONT_COLOR;
import static _959.server_waypoint.util.ColorUtils.GREEN;
import static _959.server_waypoint.util.ColorUtils.RED;

public class ClientConfigScreen extends MovementAllowedScreen {
    private final Screen parentScreen;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    private final ToggleButton renderToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setEnableWaypointRender);
    private final IntegerSlider scaleSlider = new IntegerSlider(0, 0, 0, 500, WaypointClientMod.getClientConfig().getWaypointScalingFactor(), WaypointClientMod.getClientConfig()::setWaypointScalingFactor, textRenderer);
    private final IntegerSlider vertOffsetSlider = new IntegerSlider(0, 0, -100, 100, WaypointClientMod.getClientConfig().getWaypointVerticalOffset(), WaypointClientMod.getClientConfig()::setWaypointVerticalOffset, textRenderer);
    private final IntegerSlider alphaSlider = new IntegerSlider(0, 0, 0, 255, WaypointClientMod.getClientConfig().getWaypointBackgroundAlpha(), WaypointClientMod.getClientConfig()::setWaypointBackgroundAlpha, textRenderer);
    private final IntegerSlider renderDistanceSlider = new IntegerSlider(0, 0, 0, 1024, WaypointClientMod.getClientConfig().getViewDistance(), WaypointClientMod.getClientConfig()::setViewDistance, textRenderer);
    private final ToggleButton xaerosAutoSyncToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setAutoSyncToXaerosMinimap);
    private final TranslucentButton syncToXaerosButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("server_waypoint.config.confirm_sync"), this::openXaerosSyncConfirmationDialog);
    private final ConfirmationDialog xaerosSyncConfirmationDialog;

    public ClientConfigScreen(Screen parentScreen) {
        super(Text.empty());
        this.parentScreen = parentScreen;
        ScalableText title = new ScalableText(0, 0, Text.translatable("server_waypoint.config.screen.title"), 1.2F, FONT_COLOR, textRenderer);
        title.setXOffset(5);
        WidgetStack row1 = new WidgetStack(0, 0, 8);
        WidgetStack row2 = new WidgetStack(0, 0, 8);
        WidgetStack row3 = new WidgetStack(0, 0, 8);
        WidgetStack row4 = new WidgetStack(0, 0, 8);
        WidgetStack row5 = new WidgetStack(0, 0, 8);
        WidgetStack row6 = new WidgetStack(0, 0, 8);
        WidgetStack row7 = new WidgetStack(0, 0, 8);
        row1.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.enable_waypoint_render"), FONT_COLOR, textRenderer));
        row1.addChild(renderToggle);

        row2.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.waypoint_scale_factor"), FONT_COLOR, textRenderer));
        row2.addChild(scaleSlider);

        row3.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.waypoint_vertical_offset"), FONT_COLOR, textRenderer));
        row3.addChild(vertOffsetSlider);

        row4.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.waypoint_bg_alpha"), FONT_COLOR, textRenderer));
        row4.addChild(alphaSlider);

        row5.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.local_waypoint_view_distance"), FONT_COLOR, textRenderer));
        row5.addChild(renderDistanceSlider);

        row6.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.auto_sync_to_xaeros"), FONT_COLOR, textRenderer));
        row6.addChild(xaerosAutoSyncToggle);

        MutableText xaerosSyncDialogTitle = Text.translatable("server_waypoint.config.sync_to_xaeros");
        row7.addChild(new ScalableText(0, 0, xaerosSyncDialogTitle, FONT_COLOR, textRenderer));
        row7.addChild(syncToXaerosButton);

        renderToggle.setState(WaypointClientMod.getClientConfig().isEnableWaypointRender());
        xaerosAutoSyncToggle.setState(WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap());

        mainLayout.addChild(title);
        mainLayout.addChild(row1);
        mainLayout.addChild(row2);
        mainLayout.addChild(row3);
        mainLayout.addChild(row4);
        mainLayout.addChild(row5);
        mainLayout.addChild(row6);
        mainLayout.addChild(row7);
        renderDistanceSlider.setYOffset(-2);
        this.width = mainLayout.getWidth();
        this.height = mainLayout.getHeight();

        WidgetStack xaerosSyncWarningContent = new WidgetStack(0, 0, 5, true, false);
        int warnMaxWidth = Math.round(textRenderer.getWidth(xaerosSyncDialogTitle) * 1.2F);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.1"), 1F, FONT_COLOR, warnMaxWidth, textRenderer), 0);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.2"), 1F, GREEN, warnMaxWidth, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.3"), 1F, FONT_COLOR, warnMaxWidth, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.4"), 1F, RED, warnMaxWidth, textRenderer));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0, Text.translatable("server_waypoint.config.sync_to_xaeros.warn.5"), 1F, FONT_COLOR, warnMaxWidth, textRenderer));
        this.xaerosSyncConfirmationDialog = new ConfirmationDialog(0, 0, xaerosSyncDialogTitle, xaerosSyncWarningContent, this::runXaerosSync, this::closeXaerosSyncConfirmationDialog, textRenderer);
        this.xaerosSyncConfirmationDialog.visible = false;
    }

    private void runXaerosSync() {
        if (isXaerosMinimapLoaded) {
            HandlerForXaerosMinimap.syncFromServerWaypointMod();
        }
        this.closeXaerosSyncConfirmationDialog();
    }

    private void openXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = true;
        this.xaerosSyncConfirmationDialog.forEachChild(button -> button.active = true);
        this.setFocused(this.xaerosSyncConfirmationDialog);
        this.renderToggle.active = false;
        this.scaleSlider.active = false;
        this.vertOffsetSlider.active = false;
        this.alphaSlider.active = false;
        this.renderDistanceSlider.active = false;
        this.xaerosAutoSyncToggle.active = false;
        this.syncToXaerosButton.active = false;
    }

    private void closeXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = false;
        this.xaerosSyncConfirmationDialog.forEachChild(button -> button.active = false);
        this.setFocused(this.renderToggle);
        this.renderToggle.active = true;
        this.scaleSlider.active = true;
        this.vertOffsetSlider.active = true;
        this.alphaSlider.active = true;
        this.renderDistanceSlider.active = true;
        this.xaerosAutoSyncToggle.active = true;
        this.syncToXaerosButton.active = true;
    }

    @Override
    public void init() {
        super.init();
        this.addDrawableChild(renderToggle);
        this.addDrawableChild(scaleSlider);
        this.addDrawableChild(vertOffsetSlider);
        this.addDrawableChild(alphaSlider);
        this.addDrawableChild(renderDistanceSlider);
        this.addDrawableChild(xaerosAutoSyncToggle);
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
