//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.SettingsListLayout;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ConfirmationDialog;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.OnOffToggleButton;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.integrations.MapModIntegration;
import _959.server_waypoint.common.client.integrations.MapModIntegrations;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.network.upload.UploadTarget;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;

/**
 * The client settings: waypoint rendering, map-mod sync and appearance in a
 * {@link SettingsListWidget}, with per-row and global reset and confirmation dialogs.
 */
public class ClientConfigScreen extends MovementAllowedScreen {
    private static final int SCREEN_MARGIN = 10;
    private static final int SECTION_SPACING = 6;
    private static final float TITLE_SCALE = 1.2F;
    private static final float ROW_CONTROL_SCALE = SettingsListWidget.ROW_TEXT_SCALE;
    private static final int FOOTER_BUTTON_GAP = 6;
    private static final int STATUS_GAP = 8;
    // Below this width beside the buttons, the status moves to its own line above them.
    private static final int MIN_STATUS_WIDTH = 100;
    private static final int STATUS_LINE_GAP = 4;
    private static final int RESET_BUTTON_SIZE = 11;
    private static final int SLIDER_TRACK_WIDTH = 100;
    private static final int SLIDER_FIELD_WIDTH = 30;
    private static final int DIALOG_TEXT_WIDTH = 220;
    private static final int DIALOG_LINE_GAP = 5;

    private final Screen parentScreen;
    private final ClientConfig config = WaypointClientMod.getClientConfig();
    private final List<SettingControl> settingControls = new ArrayList<>();
    private final List<MapModControls> mapModControls = new ArrayList<>();
    private final WidgetPack footer = new WidgetPack(Orientation.HORIZONTAL);
    private final ScalableText titleText;
    private final SettingsListWidget settingsList;
    private final TranslucentButton themeButton;
    private final TranslucentButton resetAllButton;
    private final TranslucentButton doneButton;
    private final ScalableText statusText;
    private final ConfirmationDialog resetAllDialog;
    private final List<ClientConfigSettings.Setting> shownSettings;
    private @Nullable ConfirmationDialog openDialog;
    private @Nullable AbstractWidget dialogOpener;
    private @Nullable GuiEventListener pendingFocus;
    // True while the constructor builds the controls and while they're refreshed from the config,
    // so their change callbacks don't write the values straight back.
    private boolean updatingControls = true;
    private boolean hasStatus;
    private int contentWidth;
    private int contentHeight;

    public ClientConfigScreen(Screen parentScreen) {
        super(Component.translatable("server_waypoint.config.screen.title", ModInfo.MOD_VERSION));
        this.parentScreen = parentScreen;
        this.titleText = new ScalableText(0, 0, this.title, TITLE_SCALE, TEXT_PRIMARY, this.font);
        this.settingsList = new SettingsListWidget(this.font);
        this.themeButton = TranslucentButton.fitted(Component.translatable("server_waypoint.config.theme.open"),
                this::openThemeConfigScreen);
        scaleRowButton(this.themeButton);
        this.resetAllButton = TranslucentButton.fitted(Component.translatable("server_waypoint.config.reset_all"),
                this::openResetAllDialog);
        this.doneButton = TranslucentButton.fitted(CommonComponents.GUI_DONE, this::onClose);
        this.statusText = new ScalableText(0, 0, Component.empty(), 1.0F, SUCCESS, MIN_STATUS_WIDTH, this.font);
        this.resetAllDialog = new ConfirmationDialog(
                0,
                0,
                Component.translatable("server_waypoint.config.reset_all.title"),
                this.dialogText(List.of(new DialogLine(
                        Component.translatable("server_waypoint.config.reset_all.body"), TEXT_PRIMARY))),
                Component.translatable("server_waypoint.config.reset_all.confirm"),
                this::confirmResetAll,
                this::closeDialog,
                this.font
        );

        Set<UploadTarget> installedMapMods = EnumSet.noneOf(UploadTarget.class);
        List<SettingsListWidget.Entry> entries = new ArrayList<>();
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.rendering")));
        for (ClientConfigSettings.Setting setting : ClientConfigSettings.RENDERING) {
            entries.add(this.createSettingRow(setting));
        }
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.map_mods")));
        for (UploadTarget target : ClientConfigSettings.MAP_MODS) {
            if (this.addMapModRows(entries, target)) {
                installedMapMods.add(target);
            }
        }
        entries.add(new SettingsListWidget.Header(Component.translatable("server_waypoint.config.section.appearance")));
        entries.add(new SettingsListWidget.Row(Component.translatable("server_waypoint.config.theme"), this.themeButton)
                .tooltip(() -> Component.translatable("server_waypoint.config.theme.tooltip")));
        this.settingsList.setEntries(entries);
        this.shownSettings = ClientConfigSettings.forScreen(installedMapMods);

        this.footer.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        this.footer.addChild(this.doneButton, Direction.REVERSE);
        this.footer.addChild(SpacerElement.width(FOOTER_BUTTON_GAP), Direction.REVERSE);
        this.footer.addChild(this.resetAllButton, Direction.REVERSE);
        this.updatingControls = false;
    }

    @Override
    protected void init() {
        super.init();
        // Row widgets first and the list last, so the list can't take clicks meant for its rows.
        this.settingsList.visitWidgets(this::addRenderableWidget);
        this.addRenderableWidget(this.resetAllButton);
        this.addRenderableWidget(this.doneButton);
        for (ConfirmationDialog dialog : this.dialogs()) {
            dialog.visitWidgets(this::addRenderableWidget);
        }
        this.layoutContent();
        this.refreshControlStates();
        if (this.openDialog != null) {
            this.setFocused(this.openDialog.getCancelButton());
        }
    }

    @Override
    int getContentWidth() {
        return this.contentWidth;
    }

    @Override
    int getContentHeight() {
        return this.contentHeight;
    }

    /** Sizes and positions the title, the panel, the footer and the dialogs for the window. */
    private void layoutContent() {
        int panelWidth = Math.min(
                this.settingsList.getPreferredWidth() + SettingsListWidget.PANEL_PADDING * 2,
                Math.max(0, this.width - SCREEN_MARGIN * 2)
        );
        this.settingsList.setVisualWidth(panelWidth);
        this.titleText.setWidth(panelWidth);
        int buttonsWidth = this.resetAllButton.getVisualWidth() + FOOTER_BUTTON_GAP + this.doneButton.getVisualWidth();
        boolean statusAbove = statusAboveButtons(panelWidth, buttonsWidth);
        this.statusText.setMaxWidth(statusAbove ? panelWidth : panelWidth - buttonsWidth - STATUS_GAP);
        int buttonHeight = Math.max(this.resetAllButton.getVisualHeight(), this.doneButton.getVisualHeight());
        int statusHeight = this.hasStatus ? this.statusText.getHeight() : 0;
        int footerHeight = statusAbove && statusHeight > 0
                ? statusHeight + STATUS_LINE_GAP + buttonHeight
                : Math.max(buttonHeight, statusHeight);
        int titleHeight = this.titleText.getHeight();
        int minimumPanelHeight = SettingsListWidget.PANEL_PADDING * 2 + SettingsListLayout.MIN_ROW_HEIGHT;
        int availablePanelHeight = this.height - SCREEN_MARGIN * 2 - titleHeight - footerHeight - SECTION_SPACING * 2;
        int panelHeight = Math.min(
                this.settingsList.getContentHeight() + SettingsListWidget.PANEL_PADDING * 2,
                Math.max(minimumPanelHeight, availablePanelHeight)
        );
        this.settingsList.setVisualHeight(panelHeight);
        this.contentWidth = panelWidth;
        this.contentHeight = titleHeight + SECTION_SPACING + panelHeight + SECTION_SPACING + footerHeight;

        int x = this.getCenteredX();
        int y = this.getCenteredY();
        this.titleText.setPosition(x, y);
        int panelY = y + titleHeight + SECTION_SPACING;
        this.settingsList.setPosition(x + SettingsListWidget.PANEL_PADDING, panelY + SettingsListWidget.PANEL_PADDING);
        int footerY = panelY + panelHeight + SECTION_SPACING;
        int buttonRowHeight = statusAbove ? buttonHeight : footerHeight;
        this.footer.setDimensions(panelWidth, buttonRowHeight);
        this.footer.setPosition(x, footerY + footerHeight - buttonRowHeight);
        this.statusText.setPosition(x, statusAbove ? footerY : footerY + centered(footerHeight, statusHeight));
        for (ConfirmationDialog dialog : this.dialogs()) {
            dialog.setPosition(centered(this.width, dialog.getWidth()), centered(this.height, dialog.getHeight()));
        }
        keepFocusVisible(this, this.settingsList);
    }

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Under an open dialog, nothing reacts to the mouse or shows a tooltip.
        int contentMouseX = this.openDialog != null ? NO_MOUSE : mouseX;
        int contentMouseY = this.openDialog != null ? NO_MOUSE : mouseY;
        this.titleText.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.settingsList.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.statusText.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.resetAllButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.doneButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        if (this.openDialog != null) {
            nextLayer(context);
            this.openDialog.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
            previousLayer(context);
        }
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        boolean handled = super.mouseClicked(event, doubleClick);
        this.applyPendingFocus();
        return handled;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        this.applyPendingFocus();
        return handled;
    }
    *///?}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Only the dialog's buttons can take focus while it's open, so there's no popup or text
        // entry for dismissFocusedInput() to handle first.
        if (keyCode == InputConstants.KEY_ESCAPE && this.openDialog != null) {
            this.closeDialog();
            this.pendingFocus = null;
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            this.settingsList.revealTabTarget(this, !isShiftDown(keyCode, scanCode, modifiers));
        }
        GuiEventListener focusedBefore = this.getFocused();
        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        GuiEventListener focused = this.getFocused();
        if (focused != null && focused != focusedBefore) {
            this.settingsList.reveal(focused);
        }
        this.pendingFocus = null;
        return handled;
    }

    /** Whether Shift is held, which turns Tab around, read the way vanilla reads it for Tab. */
    private static boolean isShiftDown(int keyCode, int scanCode, int modifiers) {
        //? if >= 1.21.9 {
        return new KeyEvent(keyCode, scanCode, modifiers).hasShiftDown();
        //?} else {
        /*return Screen.hasShiftDown();
        *///?}
    }

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        if (this.scrollSettingsList(mouseX, mouseY, 0, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.scrollSettingsList(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    /** Gives the wheel to the list first, so it scrolls instead of changing a slider under the cursor. */
    private boolean scrollSettingsList(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.settingsList.isMouseOver(mouseX, mouseY)
                || !this.settingsList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return false;
        }
        if (this.getFocused() instanceof AbstractWidget widget && !widget.visible) {
            this.setFocused(null);
        }
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        this.refreshControlStates();
    }

    /** Every exit reaches this, including a disconnect, so the config is always saved. */
    @Override
    public void removed() {
        WaypointClientMod.getInstance().saveConfig();
        super.removed();
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
    }

    /** An open dialog keeps its Cancel button focused through a resize; {@link #init} focuses it. */
    @Override
    protected boolean hasOpenModal() {
        return this.openDialog != null;
    }

    private SettingsListWidget.Row createSettingRow(ClientConfigSettings.Setting setting) {
        AbstractWidget widget;
        if (setting instanceof ClientConfigSettings.IntSetting intSetting) {
            widget = new IntegerSlider(0, 0, SLIDER_TRACK_WIDTH, SLIDER_FIELD_WIDTH, intSetting.min(), intSetting.max(),
                    intSetting.get(this.config), value -> this.onIntChanged(intSetting, value), this.font, ROW_CONTROL_SCALE);
        } else {
            ClientConfigSettings.BooleanSetting booleanSetting = (ClientConfigSettings.BooleanSetting) setting;
            OnOffToggleButton toggle = new OnOffToggleButton(0, 0, value -> this.onBooleanChanged(booleanSetting, value));
            toggle.setTextScale(ROW_CONTROL_SCALE);
            toggle.setWidth(Math.round(toggle.getWidth() * ROW_CONTROL_SCALE));
            toggle.setHeight(Math.round(toggle.getHeight() * ROW_CONTROL_SCALE));
            toggle.setState(booleanSetting.get(this.config));
            widget = toggle;
        }
        Component resetLabel = Component.translatable("server_waypoint.config.reset", setting.defaultText());
        int resetSize = Math.round(RESET_BUTTON_SIZE * ROW_CONTROL_SCALE);
        IconButton resetButton = new IconButton(0, 0, resetSize, resetSize, resetLabel,
                WidgetTextures.RESET_ICON, () -> this.resetSetting(setting, widget))
                .withoutBackground()
                .withIconPadding(2)
                .withIconRegion(7, 7, 34, 32, 48, 48);
        resetButton.setTooltip(Tooltip.create(resetLabel));
        this.settingControls.add(new SettingControl(setting, widget, resetButton));
        return new SettingsListWidget.Row(setting.text().label(), widget)
                .action(resetButton, () -> !setting.isDefault(this.config))
                .tooltip(() -> Component.empty()
                        .append(setting.text().description())
                        .append("\n")
                        .append(Component.translatable("server_waypoint.config.default", setting.defaultText())));
    }

    /** Adds the rows of one map mod and reports whether it's installed. */
    private boolean addMapModRows(List<SettingsListWidget.Entry> entries, UploadTarget target) {
        Optional<MapModIntegration> integration = MapModIntegrations.find(target);
        boolean installed = integration.map(MapModIntegration::isInstalled).orElse(false);
        Component name = Component.translatable(ClientConfigSettings.mapModNameKey(target));
        ClientConfigSync.MapModRowState state = ClientConfigSync.resolveMapModRowState(integration.isPresent(), installed);
        if (state == ClientConfigSync.MapModRowState.HIDDEN) {
            return false;
        }
        if (state == ClientConfigSync.MapModRowState.NOT_INSTALLED) {
            ScalableText notInstalled = new ScalableText(0, 0,
                    Component.translatable("server_waypoint.config.map_mod.not_installed"), ROW_CONTROL_SCALE, TEXT_MUTED, this.font);
            entries.add(new SettingsListWidget.Row(name, notInstalled)
                    .labelColor(TEXT_MUTED)
                    .tooltip(() -> Component.translatable("server_waypoint.config.map_mod.not_installed.tooltip", name)));
            return false;
        }
        entries.add(this.createSettingRow(ClientConfigSettings.autoSync(target)));
        MapModControls controls = this.createMapModControls(target, name, integration.orElseThrow());
        entries.add(new SettingsListWidget.Row(
                Component.translatable("server_waypoint.config.map_mod.sync_now", name), controls.syncButton())
                .tooltip(() -> this.syncTooltip(controls)));
        return true;
    }

    private MapModControls createMapModControls(UploadTarget target, Component name, MapModIntegration integration) {
        ConfirmationDialog dialog = new ConfirmationDialog(
                0,
                0,
                Component.translatable("server_waypoint.config.sync.title", name),
                this.dialogText(List.of(
                        new DialogLine(Component.translatable("server_waypoint.config.sync.body", name), TEXT_PRIMARY),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.stays"), SUCCESS),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.stays.detail"), TEXT_PRIMARY),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.lost"), DANGER),
                        new DialogLine(Component.translatable("server_waypoint.config.sync.lost.detail"), TEXT_PRIMARY)
                )),
                Component.translatable("server_waypoint.config.confirm_sync"),
                () -> this.confirmSync(target),
                this::closeDialog,
                this.font
        );
        TranslucentButton syncButton = TranslucentButton.fitted(
                Component.translatable("server_waypoint.config.map_mod.sync_button"), () -> this.openSyncDialog(target));
        scaleRowButton(syncButton);
        MapModControls controls = new MapModControls(target, name, integration, syncButton, dialog);
        this.mapModControls.add(controls);
        return controls;
    }

    private static void scaleRowButton(TranslucentButton button) {
        button.setTextScale(ROW_CONTROL_SCALE);
        button.setWidth(Math.round(button.getWidth() * ROW_CONTROL_SCALE));
        button.setHeight(Math.round(button.getHeight() * ROW_CONTROL_SCALE));
    }

    private WidgetStack dialogText(List<DialogLine> lines) {
        WidgetStack stack = new WidgetStack(0, 0, DIALOG_LINE_GAP, true, false);
        for (int i = 0; i < lines.size(); i++) {
            DialogLine line = lines.get(i);
            ScalableText text = new ScalableText(0, 0, line.text(), 1.0F, line.color(), DIALOG_TEXT_WIDTH, this.font);
            if (i == 0) {
                stack.addChild(text, 0);
            } else {
                stack.addChild(text);
            }
        }
        return stack;
    }

    private void onIntChanged(ClientConfigSettings.IntSetting setting, int value) {
        if (this.updatingControls) {
            return;
        }
        setting.set(this.config, value);
        this.refreshControlStates();
    }

    private void onBooleanChanged(ClientConfigSettings.BooleanSetting setting, boolean value) {
        if (this.updatingControls) {
            return;
        }
        setting.set(this.config, value);
        this.refreshControlStates();
    }

    private void resetSetting(ClientConfigSettings.Setting setting, AbstractWidget widget) {
        setting.reset(this.config);
        this.syncControlsFromConfig();
        this.refreshControlStates();
        this.requestFocus(widget);
    }

    // A method reference rather than a lambda: javac rejects a lambda in the constructor that reads
    // final fields the constructor hasn't assigned yet.
    private void openResetAllDialog() {
        this.openDialog(this.resetAllDialog, this.resetAllButton);
    }

    private void confirmResetAll() {
        ClientConfigSettings.resetAll(this.config, this.shownSettings);
        this.syncControlsFromConfig();
        this.showStatus(Component.translatable("server_waypoint.config.reset_all.done"), SUCCESS);
        this.closeDialog();
    }

    private void openSyncDialog(UploadTarget target) {
        MapModControls controls = this.mapModControls(target);
        this.openDialog(controls.dialog(), controls.syncButton());
    }

    /** Checks the blocker again, because the connection may have changed while the dialog was open. */
    private void confirmSync(UploadTarget target) {
        MapModControls controls = this.mapModControls(target);
        ClientConfigSync.SyncBlocker blocker = this.syncBlocker(controls);
        if (blocker != null) {
            this.showStatus(this.blockerMessage(blocker, controls.name()), DANGER);
        } else {
            try {
                controls.integration().syncAll(WaypointClientMod.getInstance());
                this.showStatus(Component.translatable("server_waypoint.config.sync.done", controls.name()), SUCCESS);
            } catch (RuntimeException exception) {
                WaypointClientMod.LOGGER.error("Failed to sync waypoints to {}", controls.name().getString(), exception);
                this.showStatus(Component.translatable("server_waypoint.config.sync.failed", controls.name()), DANGER);
            }
        }
        this.closeDialog();
    }

    private void openThemeConfigScreen() {
        MinecraftClientHelper.setScreen(this.minecraft, new WidgetThemeConfigScreen(this));
    }

    /** Sets every control's {@code active} flag from the dialog, the values and sync availability. */
    private void refreshControlStates() {
        boolean modal = this.openDialog != null;
        this.settingsList.active = !modal;
        for (SettingControl control : this.settingControls) {
            control.widget().active = !modal;
            control.resetButton().active = !modal && !control.setting().isDefault(this.config);
        }
        this.settingsList.refreshWidgetVisibility();
        for (MapModControls controls : this.mapModControls) {
            controls.syncButton().active = !modal && this.syncBlocker(controls) == null;
        }
        this.themeButton.active = !modal;
        this.resetAllButton.active = !modal && !ClientConfigSettings.allDefault(this.config, this.shownSettings);
        this.doneButton.active = !modal;
        for (ConfirmationDialog dialog : this.dialogs()) {
            boolean open = dialog == this.openDialog;
            dialog.visible = open;
            dialog.visitWidgets(button -> button.active = open);
        }
    }

    private void syncControlsFromConfig() {
        this.updatingControls = true;
        try {
            for (SettingControl control : this.settingControls) {
                control.readFrom(this.config);
            }
        } finally {
            this.updatingControls = false;
        }
    }

    private @Nullable ClientConfigSync.SyncBlocker syncBlocker(MapModControls controls) {
        boolean inWorld = this.minecraft != null && this.minecraft.level != null;
        return ClientConfigSync.resolveSyncBlocker(
                inWorld,
                WaypointManagerScreen.resolveViewState(WaypointServerMod.runsWithClient(), WaypointClientMod.getNetworkState()),
                controls.integration().isReady()
        );
    }

    private Component blockerMessage(ClientConfigSync.SyncBlocker blocker, Component name) {
        return blocker.namesMapMod()
                ? Component.translatable(blocker.messageKey(), name)
                : Component.translatable(blocker.messageKey());
    }

    private Component syncTooltip(MapModControls controls) {
        MutableComponent tooltip = Component.translatable("server_waypoint.config.sync.body", controls.name());
        ClientConfigSync.SyncBlocker blocker = this.syncBlocker(controls);
        if (blocker != null) {
            tooltip.append("\n").append(this.blockerMessage(blocker, controls.name()));
        }
        return tooltip;
    }

    /**
     * Whether the footer's status goes on its own line above the buttons, because they leave it
     * less than {@link #MIN_STATUS_WIDTH} beside them, as with Spanish at a 320-pixel GUI.
     */
    static boolean statusAboveButtons(int panelWidth, int buttonsWidth) {
        return panelWidth - buttonsWidth - STATUS_GAP < MIN_STATUS_WIDTH;
    }

    private void showStatus(Component message, WidgetThemeVariable color) {
        this.hasStatus = true;
        this.statusText.setColor(color);
        this.statusText.setText(message);
        // A wrapped status can change the footer's height, so the layout runs again.
        this.layoutContent();
    }

    private void openDialog(ConfirmationDialog dialog, AbstractWidget opener) {
        this.openDialog = dialog;
        this.dialogOpener = opener;
        this.refreshControlStates();
        this.requestFocus(dialog.getCancelButton());
    }

    private void closeDialog() {
        this.openDialog = null;
        this.refreshControlStates();
        AbstractWidget opener = this.dialogOpener;
        this.dialogOpener = null;
        this.requestFocus(opener != null && opener.active ? opener : this.doneButton);
    }

    private List<ConfirmationDialog> dialogs() {
        List<ConfirmationDialog> dialogs = new ArrayList<>();
        dialogs.add(this.resetAllDialog);
        for (MapModControls controls : this.mapModControls) {
            dialogs.add(controls.dialog());
        }
        return dialogs;
    }

    private MapModControls mapModControls(UploadTarget target) {
        for (MapModControls controls : this.mapModControls) {
            if (controls.target() == target) {
                return controls;
            }
        }
        throw new IllegalStateException("No controls for map mod " + target);
    }

    /**
     * Focuses {@code target} now, and again after the current click: vanilla focuses the clicked
     * widget after its callback runs, which would undo the change. Enter and Space don't move focus
     * after a button's callback, so {@link #keyPressed} only drops the pending request. A hidden row
     * widget, such as a Sync button that a longer status pushed out of view, is scrolled into view.
     */
    private void requestFocus(GuiEventListener target) {
        this.setFocused(target);
        keepFocusVisible(this, this.settingsList);
        this.pendingFocus = this.getFocused();
    }

    private void applyPendingFocus() {
        if (this.pendingFocus != null) {
            handOverFocus(this, this.pendingFocus);
            this.pendingFocus = null;
        }
    }

    /**
     * Moves focus away from the widget a click just focused, and ends the click's drag: vanilla sends
     * a drag to the focused widget, so a slider that takes focus from its reset button would jump to
     * the pointer.
     */
    static void handOverFocus(ContainerEventHandler screen, GuiEventListener target) {
        screen.setFocused(target);
        screen.setDragging(false);
    }

    /**
     * Scrolls a focused row widget that a relayout or a refocus left hidden back into view, so focus
     * never sits where nobody can see it. When its row can't be shown, focus leaves it, as it does
     * when scrolling hides it.
     */
    static void keepFocusVisible(ContainerEventHandler screen, SettingsListWidget list) {
        if (screen.getFocused() instanceof AbstractWidget widget && !widget.visible) {
            list.reveal(widget);
            if (!widget.visible) {
                screen.setFocused(null);
            }
        }
    }

    private record SettingControl(ClientConfigSettings.Setting setting, AbstractWidget widget, IconButton resetButton) {
        void readFrom(ClientConfig config) {
            if (this.setting instanceof ClientConfigSettings.IntSetting intSetting
                    && this.widget instanceof IntegerSlider slider) {
                slider.setValue(intSetting.get(config));
            } else if (this.setting instanceof ClientConfigSettings.BooleanSetting booleanSetting
                    && this.widget instanceof OnOffToggleButton toggle) {
                toggle.setState(booleanSetting.get(config));
            }
        }
    }

    private record MapModControls(UploadTarget target, Component name, MapModIntegration integration,
                                  TranslucentButton syncButton, ConfirmationDialog dialog) {
    }

    private record DialogLine(Component text, WidgetThemeVariable color) {
    }
}
