//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.NotNull;

import static _959.server_waypoint.common.client.ClientConfig.isXaerosMinimapLoaded;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.translate;

public class ClientConfigScreen extends MovementAllowedScreen {
    private static final int CONTENT_GAP = 10;
    private static final int SCREEN_MARGIN = 10;

    private final Screen parentScreen;
    private final ToggleButton renderToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setEnableWaypointRender);
    private final IntegerSlider scaleSlider = new IntegerSlider(0, 0, 0, 500, WaypointClientMod.getClientConfig().getWaypointScalingFactor(), WaypointClientMod.getClientConfig()::setWaypointScalingFactor, font);
    private final IntegerSlider vertOffsetSlider = new IntegerSlider(0, 0, -100, 100, WaypointClientMod.getClientConfig().getWaypointVerticalOffset(), WaypointClientMod.getClientConfig()::setWaypointVerticalOffset, font);
    private final IntegerSlider alphaSlider = new IntegerSlider(0, 0, 0, 255, WaypointClientMod.getClientConfig().getWaypointBackgroundAlpha(), WaypointClientMod.getClientConfig()::setWaypointBackgroundAlpha, font);
    private final IntegerSlider renderDistanceSlider = new IntegerSlider(0, 0, 0, 1024, WaypointClientMod.getClientConfig().getViewDistance(), WaypointClientMod.getClientConfig()::setViewDistance, font);
    private final ToggleButton xaerosAutoSyncToggle = new TrueFalseToggleButton(0, 0, WaypointClientMod.getClientConfig()::setAutoSyncToXaerosMinimap);
    private final TranslucentButton syncToXaerosButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("server_waypoint.config.confirm_sync"), this::openXaerosSyncConfirmationDialog);
    private final TranslucentButton themeButton = new TranslucentButton(
            0,
            0,
            70,
            11,
            Component.translatable("server_waypoint.config.theme.open"),
            this::openThemeConfigScreen
    );
    private final ScalableText titleText;
    private final ConfigTreeView configTree;
    private final ConfirmationDialog xaerosSyncConfirmationDialog;

    public ClientConfigScreen(Screen parentScreen) {
        super(Component.translatable("server_waypoint.config.screen.title", ModInfo.MOD_VERSION));
        this.parentScreen = parentScreen;
        this.titleText = new ScalableText(
                0, 0, this.title, 1.2F, WidgetThemeVariable.TEXT_PRIMARY, font);
        this.titleText.setXOffset(5);
        WidgetStack row1 = createConfigRow();
        WidgetStack row2 = createConfigRow();
        WidgetStack row3 = createConfigRow();
        WidgetStack row4 = createConfigRow();
        WidgetStack row5 = createConfigRow();
        WidgetStack row6 = createConfigRow();
        WidgetStack row7 = createConfigRow();
        WidgetStack row8 = createConfigRow();
        row1.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.enable_waypoint_render"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row1.addChild(renderToggle);

        row2.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.waypoint_scale_factor"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row2.addChild(scaleSlider);

        row3.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.waypoint_vertical_offset"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row3.addChild(vertOffsetSlider);

        row4.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.waypoint_bg_alpha"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row4.addChild(alphaSlider);

        row5.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.local_waypoint_view_distance"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row5.addChild(renderDistanceSlider);

        WidgetThemeVariable xaerosSyncFontColor = isXaerosMinimapLoaded
                ? WidgetThemeVariable.TEXT_PRIMARY
                : WidgetThemeVariable.TEXT_DISABLED;
        row6.addChild(new ScalableText(0, 0, Component.translatable("server_waypoint.config.auto_sync_to_xaeros"), xaerosSyncFontColor, font));
        row6.addChild(xaerosAutoSyncToggle);

        MutableComponent xaerosSyncDialogTitle = Component.translatable("server_waypoint.config.sync_to_xaeros");
        row7.addChild(new ScalableText(0, 0, xaerosSyncDialogTitle, xaerosSyncFontColor, font));
        row7.addChild(syncToXaerosButton);

        row8.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.theme"),
                WidgetThemeVariable.TEXT_PRIMARY, font));
        row8.addChild(this.themeButton);

        if (!isXaerosMinimapLoaded) {
            this.xaerosAutoSyncToggle.active = false;
            this.syncToXaerosButton.active = false;
        }

        renderToggle.setState(WaypointClientMod.getClientConfig().isEnableWaypointRender());
        xaerosAutoSyncToggle.setState(WaypointClientMod.getClientConfig().isAutoSyncToXaerosMinimap());

        List<WidgetStack> configRows = List.of(row1, row2, row3, row4, row5, row6, row7, row8);
        this.configTree = new ConfigTreeView(configRows);
        this.configTree.updateRoots(configRows);
        renderDistanceSlider.setYOffset(-2);

        WidgetStack xaerosSyncWarningContent = new WidgetStack(0, 0, 5, true, false);
        int warnMaxWidth = Math.round(font.width(xaerosSyncDialogTitle) * 1.2F);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.sync_to_xaeros.warn.1"),
                1F, WidgetThemeVariable.TEXT_PRIMARY, warnMaxWidth, font), 0);
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.sync_to_xaeros.warn.2"),
                1F, WidgetThemeVariable.SUCCESS, warnMaxWidth, font));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.sync_to_xaeros.warn.3"),
                1F, WidgetThemeVariable.TEXT_PRIMARY, warnMaxWidth, font));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.sync_to_xaeros.warn.4"),
                1F, WidgetThemeVariable.DANGER, warnMaxWidth, font));
        xaerosSyncWarningContent.addChild(new ScalableText(0, 0,
                Component.translatable("server_waypoint.config.sync_to_xaeros.warn.5"),
                1F, WidgetThemeVariable.TEXT_PRIMARY, warnMaxWidth, font));
        this.xaerosSyncConfirmationDialog = new ConfirmationDialog(0, 0, xaerosSyncDialogTitle, xaerosSyncWarningContent, this::runXaerosSync, this::closeXaerosSyncConfirmationDialog, font);
        this.xaerosSyncConfirmationDialog.visible = false;
    }

    private static WidgetStack createConfigRow() {
        return new WidgetStack(0, 0, 8, Orientation.HORIZONTAL, Direction.FORWARD, true);
    }

    private void runXaerosSync() {
        if (isXaerosMinimapLoaded) {
            MapModIntegrations.syncXaerosMinimap(WaypointClientMod.getInstance());
        }
        this.closeXaerosSyncConfirmationDialog();
    }

    private void openThemeConfigScreen() {
        MinecraftClientHelper.setScreen(this.minecraft, new WidgetThemeConfigScreen(this));
    }

    private void openXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = true;
        this.xaerosSyncConfirmationDialog.visitWidgets(button -> button.active = true);
        this.setFocused(this.xaerosSyncConfirmationDialog);
        this.configTree.active = false;
        this.renderToggle.active = false;
        this.scaleSlider.active = false;
        this.vertOffsetSlider.active = false;
        this.alphaSlider.active = false;
        this.renderDistanceSlider.active = false;
        this.xaerosAutoSyncToggle.active = false;
        this.syncToXaerosButton.active = false;
        this.themeButton.active = false;
    }

    private void closeXaerosSyncConfirmationDialog() {
        this.xaerosSyncConfirmationDialog.visible = false;
        this.xaerosSyncConfirmationDialog.visitWidgets(button -> button.active = false);
        this.setFocused(this.renderToggle);
        this.configTree.active = true;
        this.renderToggle.active = true;
        this.scaleSlider.active = true;
        this.vertOffsetSlider.active = true;
        this.alphaSlider.active = true;
        this.renderDistanceSlider.active = true;
        this.xaerosAutoSyncToggle.active = true;
        this.syncToXaerosButton.active = true;
        this.themeButton.active = true;
    }

    @Override
    public void init() {
        super.init();
        this.addRenderableWidget(renderToggle);
        this.addRenderableWidget(scaleSlider);
        this.addRenderableWidget(vertOffsetSlider);
        this.addRenderableWidget(alphaSlider);
        this.addRenderableWidget(renderDistanceSlider);
        this.addRenderableWidget(xaerosAutoSyncToggle);
        this.addRenderableWidget(syncToXaerosButton);
        this.addRenderableWidget(this.themeButton);
        this.addRenderableWidget(this.configTree);
        this.xaerosSyncConfirmationDialog.visitWidgets(this::addRenderableWidget);
        this.positionContent();
    }

    @Override
    protected void renderScreenContents
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.positionContent();
        this.titleText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        this.configTree.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        int centeredX = centered(this.width, this.xaerosSyncConfirmationDialog.getWidth());
        int centeredY = centered(this.height, this.xaerosSyncConfirmationDialog.getHeight());
        this.xaerosSyncConfirmationDialog.setPosition(centeredX, centeredY);
        nextLayer(context);
        this.xaerosSyncConfirmationDialog.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        previousLayer(context);
    }

    @Override
    int getContentWidth() {
        return Math.max(this.titleText.getWidth() + 5, this.configTree.getWidth());
    }

    @Override
    int getContentHeight() {
        return this.titleText.getHeight() + CONTENT_GAP + this.getConfigTreeHeight();
    }

    private int getConfigTreeHeight() {
        int availableHeight = this.height - SCREEN_MARGIN * 2 - this.titleText.getHeight() - CONTENT_GAP;
        return Math.min(this.configTree.getContentHeight(), Math.max(40, availableHeight));
    }

    private void positionContent() {
        int x = this.getCenteredX();
        int y = this.getCenteredY();
        this.titleText.setPosition(x, y);
        this.configTree.setHeight(this.getConfigTreeHeight());
        this.configTree.setPosition(x, y + this.titleText.getHeight() + CONTENT_GAP);
        this.configTree.positionRows();
    }

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (this.scrollConfigTree(mouseX, mouseY, 0, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollConfigTree(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    private boolean scrollConfigTree(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (!this.configTree.isMouseOver(mouseX, mouseY)
                || !this.configTree.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return false;
        }
        if (this.getFocused() instanceof AbstractWidget widget && !widget.visible) {
            this.setFocused(this.configTree);
        }
        return true;
    }

    @Override
    public void onClose() {
        WaypointClientMod.getInstance().saveConfig();
        MinecraftClientHelper.setScreen(this.minecraft, parentScreen);
    }

    private static final class ConfigTreeView extends TreeViewWidget<WidgetStack> {
        private static final int ROW_GAP = 10;

        private ConfigTreeView(List<WidgetStack> rows) {
            super(
                    0,
                    0,
                    getContentWidth(rows),
                    1,
                    getRowHeight(rows),
                    Component.empty(),
                    0,
                    0,
                    0,
                    0,
                    0x00000000,
                    0x00000000,
                    false
            );
            this.setWidth(this.getWidth() + this.SCROLLBAR_WIDTH);
        }

        private static int getContentWidth(List<WidgetStack> rows) {
            return rows.stream().mapToInt(WidgetStack::getWidth).max().orElse(0);
        }

        private static int getRowHeight(List<WidgetStack> rows) {
            return rows.stream().mapToInt(WidgetStack::getHeight).max().orElse(0) + ROW_GAP;
        }

        @Override
        protected @NotNull List<WidgetStack> getChildren(WidgetStack value) {
            return List.of();
        }

        @Override
        protected boolean isExpanded(WidgetStack value) {
            return false;
        }

        @Override
        protected void setExpanded(WidgetStack value, boolean expanded) {
        }

        @Override
        protected void renderEmpty(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
        }

        @Override
        protected void renderEntry(
                GuiGraphicsExtractor context,
                TreeEntry<WidgetStack> entry,
                boolean hovered,
                int rowY,
                int contentWidth,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            WidgetStack row = entry.value();
            this.positionRow(entry);
            row.visitWidgets(widget -> widget.visible = true);
            push(context);
            translate(context, -this.getX(), -this.getY() + (float)this.getScrollY());
            row.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
            pop(context);
            this.positionRow(entry);
        }

        @Override
        protected void onScrollChanged(double scrollY) {
            this.positionRows();
        }

        private void positionRows() {
            for (int i = 0; i < this.visibleEntryCount(); i++) {
                this.positionRow(this.getVisibleEntry(i));
            }
        }

        private void positionRow(TreeEntry<WidgetStack> entry) {
            int rowY = this.getY() + entry.row() * this.getRowHeight() - (int)this.getScrollY();
            WidgetStack row = entry.value();
            row.setPosition(this.getX(), rowY);
            boolean fullyVisible = rowY >= this.getY()
                    && rowY + this.getRowHeight() <= this.getY() + this.getHeight();
            row.visitWidgets(widget -> widget.visible = fullyVisible);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
