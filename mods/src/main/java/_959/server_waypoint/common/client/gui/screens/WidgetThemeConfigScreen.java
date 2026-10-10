//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Direction;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow.Orientation;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeJson;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeSelection;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.Arrangement;
import _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.Rect;
import _959.server_waypoint.common.client.gui.widgets.ColorHexCodeField;
import _959.server_waypoint.common.client.gui.widgets.ColorSquareButton;
import _959.server_waypoint.common.client.gui.widgets.IconButton;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SeparatorWidget;
import _959.server_waypoint.common.client.gui.widgets.SettingsListWidget;
import _959.server_waypoint.common.client.gui.widgets.SwatchWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TreeViewWidget;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.NO_MOUSE;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawScaledText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SELECTION_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.KEY_ROW_HEIGHT;
import static _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.KEY_X;
import static _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.PANEL_PADDING;
import static _959.server_waypoint.common.client.gui.screens.WidgetThemeEditorLayout.VALUE_INSET;

/**
 * The color theme editor, a developer tool for the runtime theme: a header line with the title and the
 * theme dropdown; on the left, the raw theme keys with their colors and, under them, the key editor; on
 * the right, the preview; and a footer with the status and Reset, Cancel and Save.
 * {@link WidgetThemeEditorLayout} works out where everything goes and {@link WidgetThemeEditorSession}
 * owns the edit, which previews at once and is undone unless it is saved.
 */
public final class WidgetThemeConfigScreen extends MovementAllowedScreen {
    private static final float TITLE_SCALE = 1.2F;
    /** The key rows, the Alpha row and the hint use the client settings rows' text scale. */
    private static final float ROW_SCALE = SettingsListWidget.ROW_TEXT_SCALE;
    private static final int ARGB_DIGITS = 8;
    private static final int FOOTER_BUTTON_GAP = 6;
    // The key editor, from the top of its padding: the 9-pixel key line, then two 11-pixel rows, 5 pixels apart.
    private static final int COLOR_ROW_Y = 14;
    private static final int ALPHA_ROW_Y = 30;
    private static final int EDITOR_ROW_HEIGHT = 11;
    /** Where the empty state's hint starts, under the key line. */
    private static final int HINT_Y = 14;
    /** Between the key line's text and its line, as in a settings header. */
    private static final int KEY_LINE_GAP = 4;
    /** Between the color button and the ARGB field. */
    private static final int CONTROL_GAP = 4;
    private static final int ALPHA_LABEL_GAP = 8;
    private static final int RESET_KEY_SIZE = 9;
    /** A track that leaves room for every locale's Alpha label in the narrowest, 152-pixel column. */
    private static final int ALPHA_TRACK_WIDTH = 64;
    private static final int ALPHA_FIELD_WIDTH = 30;

    private final Screen parentScreen;
    private final WidgetThemeEditorSession session;
    private final ScalableText titleText;
    private final ThemeSelector themeSelector;
    private final KeyList keyList;
    private final ScalableText keyText;
    /** The line after the key line's text; it is made again when that text or the layout changes. */
    private SeparatorWidget keyLine = new SeparatorWidget(0, 0, 0, 1);
    private final ColorSquareButton colorButton;
    private final ColorHexCodeField argbField;
    private final IconButton resetKeyButton;
    private final ScalableText alphaLabel;
    private final IntegerSlider alphaSlider;
    private final ScalableText hintText;
    private final WidgetPack colorRow = new WidgetPack(Orientation.HORIZONTAL);
    private final WidgetPack alphaRow = new WidgetPack(Orientation.HORIZONTAL);
    private final WidgetThemePreview preview;
    private final TranslucentButton resetButton;
    private final TranslucentButton cancelButton;
    private final TranslucentButton saveButton;
    private final WidgetPack footer = new WidgetPack(Orientation.HORIZONTAL);
    private final ScalableText statusText;
    private final SwatchWidget swatchWidget;
    private @Nullable WidgetThemeVariable selectedKey = WidgetThemeVariable.TEXT_PRIMARY;
    // True while the constructor builds the controls and while they're set from the draft, so their
    // change callbacks don't apply the values straight back.
    private boolean updatingControls = true;
    private boolean hasStatus;
    private @Nullable Arrangement arrangement;

    public WidgetThemeConfigScreen(Screen parentScreen) {
        super(Component.translatable("server_waypoint.theme.screen.title"));
        this.parentScreen = parentScreen;
        this.session = new WidgetThemeEditorSession(
                WidgetThemeManager.getTheme(),
                WaypointClientMod.getInstance().getWidgetThemePath(),
                this.loadThemeSettings()
        );
        this.titleText = new ScalableText(0, 0, this.title, TITLE_SCALE, TEXT_PRIMARY, this.font);
        this.themeSelector = new ThemeSelector();

        this.keyList = new KeyList(this.font, this::onKeyClicked, this::onArrowKey);
        this.keyText = new ScalableText(0, 0, Component.empty(), TEXT_PRIMARY, this.font);
        this.colorButton = new ColorSquareButton(0, 0, this.font.lineHeight, this::openSwatch);
        this.colorButton.setTooltip(Component.translatable("server_waypoint.theme.color_picker"));
        this.argbField = ColorHexCodeField.argb(0, 0, Component.literal("ARGB"), this.font);
        this.argbField.setResponder(this::onArgbChanged);
        this.resetKeyButton = new IconButton(0, 0, RESET_KEY_SIZE, RESET_KEY_SIZE,
                Component.translatable("server_waypoint.theme.reset_key"), WidgetTextures.RESET_ICON, this::revertKey)
                .withoutBackground()
                .withIconPadding(2)
                .withIconRegion(7, 7, 34, 32, 48, 48);
        this.resetKeyButton.setTooltip(Component.translatable("server_waypoint.theme.reset_key"));
        this.alphaLabel = new ScalableText(0, 0, Component.translatable("server_waypoint.theme.alpha"),
                ROW_SCALE, TEXT_PRIMARY, this.font);
        // syncControls below sets the value from the selected key.
        this.alphaSlider = new IntegerSlider(0, 0, ALPHA_TRACK_WIDTH, ALPHA_FIELD_WIDTH, 0, 255, 255,
                this::onAlphaChanged, this.font, ROW_SCALE);
        this.hintText = new ScalableText(0, 0, Component.translatable("server_waypoint.theme.no_key.hint"),
                ROW_SCALE, TEXT_MUTED, this.font);
        this.colorRow.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        this.colorRow.addChild(this.colorButton, Direction.FORWARD);
        this.colorRow.addChild(SpacerElement.width(CONTROL_GAP), Direction.FORWARD);
        this.colorRow.addChild(this.argbField, Direction.FORWARD);
        this.colorRow.addChild(this.resetKeyButton, Direction.REVERSE);
        this.alphaRow.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        this.alphaRow.addChild(this.alphaLabel, Direction.FORWARD);
        this.alphaRow.addChild(SpacerElement.width(ALPHA_LABEL_GAP), Direction.FORWARD);
        this.alphaRow.addChild(this.alphaSlider, Direction.FORWARD);

        this.preview = new WidgetThemePreview(this.font);

        this.resetButton = TranslucentButton.fitted(Component.translatable("waypoint.reset.button"), this::revertAll);
        this.resetButton.setTooltip(Component.translatable("server_waypoint.theme.reset.tooltip"));
        this.cancelButton = TranslucentButton.fitted(
                Component.translatable("server_waypoint.cancel.button"), this::cancelAndClose);
        this.saveButton = TranslucentButton.fitted(Component.translatable("server_waypoint.theme.save"), this::saveAndClose);
        this.footer.setCrossAxisAlignment(WidgetPack.CrossAxisAlignment.CENTER);
        this.footer.addChild(this.saveButton, Direction.REVERSE);
        this.footer.addChild(SpacerElement.width(FOOTER_BUTTON_GAP), Direction.REVERSE);
        this.footer.addChild(this.cancelButton, Direction.REVERSE);
        this.footer.addChild(SpacerElement.width(FOOTER_BUTTON_GAP), Direction.REVERSE);
        this.footer.addChild(this.resetButton, Direction.REVERSE);
        this.statusText = new ScalableText(0, 0, Component.empty(), 1.0F, TEXT_MUTED, 0, this.font);

        this.swatchWidget = new SwatchWidget(0, 0, this.font, this::onSwatchConfirmed);
        this.swatchWidget.visible = false;

        this.keyList.setSelected(this.selectedKey);
        this.syncControls(null);
    }

    private WidgetThemeJson.Settings loadThemeSettings() {
        Path path = WaypointClientMod.getInstance().getWidgetThemePath();
        if (Files.exists(path)) {
            try {
                return WidgetThemeJson.loadSettings(path);
            } catch (IOException | RuntimeException exception) {
                WaypointClientMod.LOGGER.error("Failed to load widget theme settings", exception);
            }
        }
        return new WidgetThemeJson.Settings(WidgetThemeSelection.CUSTOM, WidgetThemeManager.getTheme());
    }

    @Override
    protected void init() {
        super.init();
        this.acceptMovementKeys(false);
        this.closePopups();
        this.layoutContent();
        // Tab order: the header, the key list and the key editor, the preview, then the footer.
        this.addRenderableWidget(this.themeSelector);
        this.addRenderableWidget(this.keyList);
        this.addRenderableWidget(this.colorButton);
        this.addRenderableWidget(this.argbField);
        this.addRenderableWidget(this.resetKeyButton);
        this.addRenderableWidget(this.alphaSlider);
        this.preview.visitWidgets(this::addRenderableWidget);
        this.addRenderableWidget(this.resetButton);
        this.addRenderableWidget(this.cancelButton);
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.swatchWidget);
        this.refreshControlStates();
        if (this.swatchWidget.visible) {
            this.setFocused(this.swatchWidget);
        }
    }

    /** An open color picker keeps focus through a resize; {@link #init} focuses it. */
    @Override
    protected boolean hasOpenModal() {
        return this.swatchWidget.visible;
    }

    @Override
    int getContentWidth() {
        return this.arrangement == null ? 0 : this.arrangement.group().width();
    }

    @Override
    int getContentHeight() {
        return this.arrangement == null ? 0 : this.arrangement.group().height();
    }

    // ------------------------------------------------------------------ layout

    /**
     * Sizes and positions everything for the window and the status message. It runs in {@link #init},
     * and again when the status changes, because a wrapped status changes the footer's height.
     */
    private void layoutContent() {
        int buttonsWidth = this.resetButton.getVisualWidth() + FOOTER_BUTTON_GAP
                + this.cancelButton.getVisualWidth() + FOOTER_BUTTON_GAP + this.saveButton.getVisualWidth();
        int buttonsHeight = Math.max(this.resetButton.getVisualHeight(),
                Math.max(this.cancelButton.getVisualHeight(), this.saveButton.getVisualHeight()));
        Arrangement layout = WidgetThemeEditorLayout.arrange(this.width, this.height, this.themeSelector.getWidth(),
                buttonsWidth, buttonsHeight, this::wrapStatus);
        this.arrangement = layout;

        Rect title = layout.title();
        this.titleText.setText(AbstractWaypointPropertiesScreen.cutToWidth(this.font, this.title,
                (int) (title.width() / TITLE_SCALE)));
        this.titleText.setPosition(title.x(), title.y());
        this.themeSelector.setPosition(layout.dropdown().x(), layout.dropdown().y());

        Rect keys = layout.keyList();
        this.keyList.setVisualWidth(keys.width());
        this.keyList.setVisualHeight(keys.height());
        this.keyList.setPosition(keys.x() + PANEL_PADDING, keys.y() + PANEL_PADDING);
        this.keyList.setShowValues(WidgetThemeEditorLayout.showsValues(this.keyList.rowWidth(),
                KeyList.widestKeyWidth(this.font), KeyList.widestValueWidth(this.font)));
        this.layoutEditor(layout.editor());
        Rect preview = layout.preview();
        this.preview.setBounds(preview.x(), preview.y(), preview.width(), preview.height());

        Rect buttons = layout.buttons();
        this.footer.setDimensions(buttons.width(), buttons.height());
        this.footer.setPosition(buttons.x(), buttons.y());
        this.statusText.setPosition(layout.status().x(), layout.status().y());
        Rect group = layout.group();
        this.swatchWidget.setPosition(
                group.x() + centered(group.width(), this.swatchWidget.getWidth()),
                group.y() + centered(group.height(), this.swatchWidget.getHeight())
        );
    }

    /** Wraps the status to the width the layout gives it and reports its height, 0 without a status. */
    private int wrapStatus(int width) {
        this.statusText.setMaxWidth(width);
        return this.hasStatus ? this.statusText.getHeight() : 0;
    }

    /** Places the key editor's parts inside its panel's padding. */
    private void layoutEditor(Rect editor) {
        int x = editor.x() + PANEL_PADDING;
        int y = editor.y() + PANEL_PADDING;
        int width = editor.width() - PANEL_PADDING * 2;
        this.keyText.setPosition(x, y);
        this.colorRow.setDimensions(width, EDITOR_ROW_HEIGHT);
        this.colorRow.setPosition(x, y + COLOR_ROW_Y);
        this.alphaRow.setDimensions(width, EDITOR_ROW_HEIGHT);
        this.alphaRow.setPosition(x, y + ALPHA_ROW_Y);
        this.hintText.setWidth(width);
        this.hintText.setPosition(x, y + HINT_Y);
        this.placeKeyLine();
    }

    /** Draws the line after the key line's text to the editor's inner right edge, as a settings header does. */
    private void placeKeyLine() {
        if (this.arrangement == null) {
            return;
        }
        int lineX = this.keyText.getX() + this.keyText.getWidth() + KEY_LINE_GAP;
        int right = this.arrangement.editor().right() - PANEL_PADDING;
        this.keyLine = new SeparatorWidget(lineX, this.keyText.getY() + this.font.lineHeight / 2,
                Math.max(0, right - lineX), 1);
    }

    // ------------------------------------------------------------------ state

    /**
     * Sets every control's {@code active} flag, and which key editor controls show, from the color picker,
     * the selected key and whether anything changed since the editor opened.
     */
    private void refreshControlStates() {
        boolean modal = this.swatchWidget.visible;
        WidgetThemeVariable key = this.selectedKey;
        boolean editing = key != null;
        boolean changed = this.session.isDirty();
        this.themeSelector.active = !modal;
        this.keyList.active = !modal;
        this.colorButton.visible = editing;
        this.colorButton.active = !modal;
        this.argbField.visible = editing;
        AbstractWaypointPropertiesScreen.setControlActive(this.argbField, !modal);
        this.resetKeyButton.visible = editing && this.session.isChanged(key);
        this.resetKeyButton.active = !modal;
        this.alphaSlider.visible = editing;
        this.alphaSlider.active = !modal;
        this.preview.setActive(!modal);
        this.resetButton.active = !modal && changed;
        this.cancelButton.active = !modal;
        this.saveButton.active = !modal && changed;
    }

    private void onKeyClicked(WidgetThemeVariable key) {
        this.selectKey(KeySelection.click(this.selectedKey, key));
    }

    /** Up or Down in the key list, which then scrolls the least that shows the new key. */
    private void onArrowKey(boolean down) {
        WidgetThemeVariable key = KeySelection.move(this.selectedKey, down);
        this.selectKey(key);
        this.keyList.reveal(key);
    }

    /** Selects a key, or clears the selection, which hides the key editor's controls and the preview's markers. */
    private void selectKey(@Nullable WidgetThemeVariable key) {
        this.finishEditing();
        this.selectedKey = key;
        this.keyList.setSelected(key);
        this.syncControls(null);
        this.refreshControlStates();
    }

    /**
     * Sets the key line and the key editor's controls from the draft. {@code source} is the control the
     * change came from, which already shows it and is left alone, or null.
     */
    private void syncControls(@Nullable AbstractWidget source) {
        this.updatingControls = true;
        try {
            WidgetThemeVariable key = this.selectedKey;
            if (key == null) {
                this.keyText.setText(Component.translatable("server_waypoint.theme.no_key"));
                this.keyText.setColor(TEXT_MUTED);
            } else {
                int color = this.colorOf(key);
                this.keyText.setText(key.getJsonName());
                this.keyText.setColor(TEXT_PRIMARY);
                this.colorButton.setColor(color);
                if (source != this.argbField) {
                    this.argbField.setColor(color);
                }
                if (source != this.alphaSlider) {
                    this.alphaSlider.setValue(color >>> 24);
                }
                if (source != this.swatchWidget) {
                    this.swatchWidget.setColor(color);
                    this.swatchWidget.setPreviousColor(color);
                }
            }
            this.placeKeyLine();
        } finally {
            this.updatingControls = false;
        }
    }

    private int colorOf(WidgetThemeVariable key) {
        return this.session.getDraftTheme().getColor(key);
    }

    /**
     * Gives the selected key a color from one of the key editor's controls. When that turns a built-in
     * theme into Custom and replaces Custom colors that differ from it, the status says so.
     */
    private void applyColor(int color, AbstractWidget source) {
        WidgetThemeVariable key = this.selectedKey;
        if (key == null) {
            return;
        }
        this.session.setColor(key, color).ifPresent(this::showCustomReplaced);
        this.syncControls(source);
        this.refreshControlStates();
    }

    /** The ARGB field applies its value once it has all eight digits; Enter and leaving the field finish it. */
    private void onArgbChanged(String text) {
        if (this.updatingControls || text.length() != ARGB_DIGITS) {
            return;
        }
        this.applyColor(this.argbField.getColor(), this.argbField);
    }

    /** The slider changes the alpha and keeps the RGB. */
    private void onAlphaChanged(int alpha) {
        WidgetThemeVariable key = this.selectedKey;
        if (this.updatingControls || key == null) {
            return;
        }
        this.applyColor((alpha << 24) | (this.colorOf(key) & 0x00FFFFFF), this.alphaSlider);
    }

    /** The color picker changes the RGB and keeps the alpha. */
    private void onSwatchConfirmed(int rgb) {
        WidgetThemeVariable key = this.selectedKey;
        if (key != null) {
            this.applyColor((this.colorOf(key) & 0xFF000000) | (rgb & 0x00FFFFFF), this.swatchWidget);
        }
        this.closeSwatch();
    }

    /** The reset icon puts the selected key back to its color when the editor opened, which counts as an edit. */
    private void revertKey() {
        WidgetThemeVariable key = this.selectedKey;
        if (key == null) {
            return;
        }
        this.session.revert(key).ifPresent(this::showCustomReplaced);
        this.syncControls(null);
        this.refreshControlStates();
    }

    /** Reset puts the selected theme and the Custom colors back as they were when the editor opened. */
    private void revertAll() {
        this.session.revertAll();
        this.clearStatus();
        this.syncControls(null);
        this.refreshControlStates();
    }

    private void chooseTheme(WidgetThemeSelection selection) {
        this.session.select(selection);
        this.clearStatus();
        this.syncControls(null);
        this.refreshControlStates();
    }

    private void saveAndClose() {
        this.finishEditing();
        try {
            this.session.save();
        } catch (IOException exception) {
            WaypointClientMod.LOGGER.error("Failed to save widget theme", exception);
            this.showStatus(Component.translatable("server_waypoint.theme.save.failed"), DANGER);
            this.refreshControlStates();
            return;
        }
        MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
    }

    private void cancelAndClose() {
        this.finishEditing();
        this.session.cancel();
        MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
    }

    /**
     * Finishes a value being entered in the ARGB or the Alpha field by moving focus off it, while the
     * selected key is still the one it is for and the session is still open. A click on a key, Save or
     * Cancel runs its callback first, and vanilla moves focus to the clicked widget only afterwards,
     * which would otherwise finish the value for another key or into a closed session.
     */
    private void finishEditing() {
        if (this.getFocused() == this.argbField || this.getFocused() == this.alphaSlider) {
            this.setFocused(null);
        }
    }

    @Override
    public void onClose() {
        this.cancelAndClose();
    }

    @Override
    public void removed() {
        this.session.cancel();
        super.removed();
    }

    private void showCustomReplaced(WidgetThemeSelection preset) {
        this.showStatus(Component.translatable("server_waypoint.theme.custom_replaced", presetName(preset)), TEXT_MUTED);
    }

    /** Shows a message in the footer; the layout runs again because a wrapped message changes the footer's height. */
    private void showStatus(Component message, WidgetThemeVariable color) {
        this.hasStatus = true;
        this.statusText.setColor(color);
        this.statusText.setText(message);
        this.layoutContent();
    }

    private void clearStatus() {
        if (!this.hasStatus) {
            return;
        }
        this.hasStatus = false;
        this.statusText.setText(Component.empty());
        this.layoutContent();
    }

    // ------------------------------------------------------------------ the color picker

    private void openSwatch() {
        WidgetThemeVariable key = this.selectedKey;
        if (key == null) {
            return;
        }
        this.closePopups();
        int color = this.colorOf(key);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.swatchWidget.visible = true;
        this.refreshControlStates();
        this.setFocused(this.swatchWidget);
    }

    private void closeSwatch() {
        this.swatchWidget.visible = false;
        this.refreshControlStates();
        this.setFocused(this.colorButton);
    }

    // ------------------------------------------------------------------ input

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.handleThemeSelectorClick(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button())) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseButtonEvent, doubleClicked);
        this.normalizeModalFocus();
        return handled;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.handleThemeSelectorClick(mouseX, mouseY, button)) {
            return true;
        }
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        this.normalizeModalFocus();
        return handled;
    }
    *///?}

    /** An open theme dropdown takes a click on its popup first; a click elsewhere closes it and goes on. */
    private boolean handleThemeSelectorClick(double mouseX, double mouseY, int button) {
        if (this.themeSelector.isExpanded() && this.themeSelector.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.themeSelector);
            return true;
        }
        this.themeSelector.closeMenuIfOutside(mouseX, mouseY);
        return false;
    }

    /** Vanilla focuses the clicked widget after its callback runs, which can take focus out of the color picker. */
    private void normalizeModalFocus() {
        if (this.swatchWidget.visible) {
            this.setFocused(this.swatchWidget);
        } else if (this.getFocused() == this.swatchWidget) {
            this.setFocused(this.colorButton);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            if (this.dismissFocusedInput()) {
                return true;
            }
            if (this.swatchWidget.visible) {
                this.closeSwatch();
            } else {
                this.cancelAndClose();
            }
            return true;
        }
        // Text fields don't handle Enter; in the ARGB field it finishes the value.
        if ((keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER)
                && this.getFocused() == this.argbField) {
            this.argbField.commit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Closes every open popup, as when the color picker opens or the screen is laid out again. */
    private void closePopups() {
        this.themeSelector.closeMenuIfOpen();
    }

    /** Whether an open popup is under the pointer, so nothing drawn beneath it reacts to the mouse. */
    private boolean isMouseOverPopup(double mouseX, double mouseY) {
        return this.themeSelector.isMouseOverPopup(mouseX, mouseY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderScreenContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        Arrangement layout = this.arrangement;
        if (layout == null) {
            return;
        }
        // Under the color picker, and under an open popup, nothing reacts to the mouse or shows a tooltip.
        boolean modal = this.swatchWidget.visible;
        boolean covered = modal || this.isMouseOverPopup(mouseX, mouseY);
        int contentMouseX = covered ? NO_MOUSE : mouseX;
        int contentMouseY = covered ? NO_MOUSE : mouseY;
        int popupMouseX = modal ? NO_MOUSE : mouseX;
        int popupMouseY = modal ? NO_MOUSE : mouseY;

        this.keyList.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.renderEditor(context, layout.editor(), contentMouseX, contentMouseY, deltaTicks);
        this.preview.render(context, contentMouseX, contentMouseY, deltaTicks, null);

        this.titleText.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.themeSelector.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        if (this.hasStatus) {
            this.statusText.
            //$ render_method_swap
            extractRenderState
                    (context, contentMouseX, contentMouseY, deltaTicks);
        }
        this.resetButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.cancelButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);
        this.saveButton.
        //$ render_method_swap
        extractRenderState
                (context, contentMouseX, contentMouseY, deltaTicks);

        this.themeSelector.renderPopup(context, popupMouseX, popupMouseY, deltaTicks);
        nextLayer(context);
        this.swatchWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        previousLayer(context);
    }

    /**
     * The key editor's panel, painted once here, and its parts: the key line, then the controls for the
     * selected key, or the hint when no key is selected.
     */
    private void renderEditor(GuiGraphicsExtractor context, Rect editor, int mouseX, int mouseY, float deltaTicks) {
        context.fill(editor.x(), editor.y(), editor.right(), editor.bottom(), getColor(PANEL_BACKGROUND));
        renderOutline(context, editor.x(), editor.y(), editor.width(), editor.height(), getColor(BORDER));
        this.keyText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.keyLine.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        if (this.selectedKey == null) {
            this.hintText.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, deltaTicks);
            return;
        }
        this.colorButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.argbField.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.resetKeyButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.alphaLabel.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.alphaSlider.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
    }

    // ------------------------------------------------------------------ the theme dropdown

    private static Component presetName(WidgetThemeSelection selection) {
        return Component.translatable("server_waypoint.theme.preset." + selection.getId());
    }

    private static Component selectorLabel(WidgetThemeSelection selection) {
        return Component.translatable("server_waypoint.theme.selector.value", presetName(selection));
    }

    /** As wide as its widest label, so the dropdown keeps its width whichever theme is selected. */
    private static int themeSelectorWidth(Font font) {
        int widest = 0;
        for (WidgetThemeSelection selection : WidgetThemeSelection.values()) {
            widest = Math.max(widest, font.width(selectorLabel(selection)));
        }
        return TextChoiceDropdown.triggerWidth(widest);
    }

    /** The header's theme dropdown: Custom and the built-in themes. Its popup is drawn after the footer. */
    private final class ThemeSelector extends TextChoiceDropdown {
        private final Component tooltip = Component.translatable("server_waypoint.theme.selector.tooltip");

        private ThemeSelector() {
            super(themeSelectorWidth(WidgetThemeConfigScreen.this.font),
                    Component.translatable("server_waypoint.theme.selector"), WidgetThemeConfigScreen.this.font);
            this.setTooltip(this.tooltip);
            this.setRenderPopupSeparately(true);
            for (WidgetThemeSelection selection : WidgetThemeSelection.values()) {
                this.addMenuItem(new TextChoice(presetName(selection), () -> chooseTheme(selection)));
            }
        }

        @Override
        protected Component triggerLabel() {
            return selectorLabel(session.getSelection());
        }

        @Override
        protected int getSelectedMenuItemIndex() {
            return session.getSelection().ordinal();
        }

        /** The tooltip goes below the dropdown, onto the open popup's first choices, so it waits until the popup closes. */
        @Override
        protected void onExpandedChanged(boolean expanded) {
            this.setTooltip(expanded ? null : this.tooltip);
        }
    }

    // ------------------------------------------------------------------ the key list

    /**
     * The raw theme keys in {@link WidgetThemeVariable#values()} order, on a panel the list paints itself.
     * A row shows the key's color on a checkerboard, so a translucent color shows its opacity, the key and,
     * when the row is wide enough, its {@code #AARRGGBB} value. It reports clicks on a key and Up and Down;
     * the screen decides the selection.
     */
    static final class KeyList extends TreeViewWidget<WidgetThemeVariable> {
        private static final int CHIP_X = 2;
        private static final int CHIP_SIZE = 8;
        private static final int CHECKER_SIZE = 2;
        // The checkerboard is a transparency visualization, like the color pickers' gradients, not a theme color.
        private static final int CHECKER_LIGHT = 0xFF9A9A9A;
        private static final int CHECKER_DARK = 0xFF5E5E5E;
        /** Puts the capitals of the 85% text, 6 pixels high, in the middle of the 12-pixel row, level with the chip. */
        private static final int TEXT_Y = 3;
        private static final String HEX_DIGITS = "0123456789ABCDEF";

        private final Font font;
        private final Consumer<WidgetThemeVariable> onKeyClicked;
        private final Consumer<Boolean> onArrowKey;
        private @Nullable WidgetThemeVariable selected;
        private boolean showValues;

        /**
         * @param onKeyClicked receives the key under a left click
         * @param onArrowKey receives true for Down and false for Up, while the list has focus
         */
        KeyList(Font font, Consumer<WidgetThemeVariable> onKeyClicked, Consumer<Boolean> onArrowKey) {
            super(0, 0, 0, 0, KEY_ROW_HEIGHT, Component.translatable("server_waypoint.theme.variables"),
                    PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_BACKGROUND, BORDER, true);
            this.font = font;
            this.onKeyClicked = onKeyClicked;
            this.onArrowKey = onArrowKey;
            this.updateRoots(Arrays.asList(WidgetThemeVariable.values()));
        }

        void setSelected(@Nullable WidgetThemeVariable selected) {
            this.selected = selected;
        }

        void setShowValues(boolean showValues) {
            this.showValues = showValues;
        }

        /** The width a row has to draw in, left of the scrollbar when the list overflows. */
        int rowWidth() {
            return this.getContentWidth();
        }

        /** Scrolls the least amount that shows {@code key}'s whole row. */
        void reveal(WidgetThemeVariable key) {
            for (int i = 0; i < this.visibleEntryCount(); i++) {
                TreeEntry<WidgetThemeVariable> entry = this.getVisibleEntry(i);
                if (entry.value() != key) {
                    continue;
                }
                int top = entry.row() * this.getRowHeight();
                int bottom = top + this.getRowHeight();
                if (top < this.getScrollY()) {
                    this.setScrollY(top);
                } else if (bottom > this.getScrollY() + this.getHeight()) {
                    this.setScrollY(bottom - this.getHeight());
                }
                return;
            }
        }

        /** How wide {@code text} is at the rows' scale. */
        static int scaledWidth(Font font, String text) {
            return Math.round(font.width(text) * ROW_SCALE);
        }

        /** The widest key at the rows' scale. */
        static int widestKeyWidth(Font font) {
            int widest = 0;
            for (WidgetThemeVariable key : WidgetThemeVariable.values()) {
                widest = Math.max(widest, scaledWidth(font, key.getJsonName()));
            }
            return widest;
        }

        /** The widest a value can be at the rows' scale: a {@code #} and eight of the widest hexadecimal digit. */
        static int widestValueWidth(Font font) {
            int digit = 0;
            for (char c : HEX_DIGITS.toCharArray()) {
                digit = Math.max(digit, font.width(String.valueOf(c)));
            }
            return Math.round((font.width("#") + digit * ARGB_DIGITS) * ROW_SCALE);
        }

        /** A color as {@code widget-theme.json} writes it: {@code #AARRGGBB}. */
        static String valueText(int color) {
            return String.format(Locale.ROOT, "#%08X", color);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!this.isActive() || (keyCode != InputConstants.KEY_UP && keyCode != InputConstants.KEY_DOWN)) {
                return false;
            }
            this.onArrowKey.accept(keyCode == InputConstants.KEY_DOWN);
            return true;
        }

        @Override
        protected boolean onEntryClicked(
                TreeEntry<WidgetThemeVariable> entry,
                double contentMouseX,
                double contentMouseY,
                int button
        ) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                return false;
            }
            this.onKeyClicked.accept(entry.value());
            return true;
        }

        @Override
        protected @NotNull List<WidgetThemeVariable> getChildren(WidgetThemeVariable value) {
            return List.of();
        }

        @Override
        protected boolean isExpanded(WidgetThemeVariable value) {
            return false;
        }

        @Override
        protected void setExpanded(WidgetThemeVariable value, boolean expanded) {
        }

        @Override
        protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        }

        @Override
        protected void renderEntry(
                GuiGraphicsExtractor context,
                TreeEntry<WidgetThemeVariable> entry,
                boolean hovered,
                int rowY,
                int contentWidth,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            WidgetThemeVariable key = entry.value();
            if (key == this.selected) {
                context.fill(0, rowY, contentWidth, rowY + KEY_ROW_HEIGHT, getColor(SELECTION_BACKGROUND));
            } else if (hovered) {
                context.fill(0, rowY, contentWidth, rowY + KEY_ROW_HEIGHT, getColor(ROW_HOVER_BACKGROUND));
            }
            int color = getColor(key);
            drawChip(context, CHIP_X, rowY + (KEY_ROW_HEIGHT - CHIP_SIZE) / 2, color);
            drawScaledText(context, this.font, Component.literal(key.getJsonName()), KEY_X, rowY + TEXT_Y,
                    getColor(TEXT_PRIMARY), ROW_SCALE);
            if (this.showValues) {
                String value = valueText(color);
                drawScaledText(context, this.font, Component.literal(value),
                        contentWidth - VALUE_INSET - scaledWidth(this.font, value), rowY + TEXT_Y,
                        getColor(TEXT_MUTED), ROW_SCALE);
            }
        }

        /** An 8 by 8 chip of {@code color} over a 2-pixel checkerboard. */
        private static void drawChip(GuiGraphicsExtractor context, int x, int y, int color) {
            context.fill(x, y, x + CHIP_SIZE, y + CHIP_SIZE, CHECKER_LIGHT);
            for (int dy = 0; dy < CHIP_SIZE; dy += CHECKER_SIZE) {
                // The top-left square is light; the dark ones alternate with it along each row.
                int firstDark = (dy / CHECKER_SIZE) % 2 == 0 ? CHECKER_SIZE : 0;
                for (int dx = firstDark; dx < CHIP_SIZE; dx += CHECKER_SIZE * 2) {
                    context.fill(x + dx, y + dy, x + dx + CHECKER_SIZE, y + dy + CHECKER_SIZE, CHECKER_DARK);
                }
            }
            context.fill(x, y, x + CHIP_SIZE, y + CHIP_SIZE, color);
        }

        /** The list's label, "Theme keys". */
        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            output.add(NarratedElementType.TITLE, this.getMessage());
        }
    }
}
