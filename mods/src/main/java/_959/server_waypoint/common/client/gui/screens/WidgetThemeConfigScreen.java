//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ColorHexCodeField;
import _959.server_waypoint.common.client.gui.widgets.ColorSquareButton;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SwatchWidget;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TreeViewWidget;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SELECTION_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public final class WidgetThemeConfigScreen extends MovementAllowedScreen {
    private static final int CONTENT_WIDTH = 292;
    private static final int CONTENT_HEIGHT = 160;
    private static final int PANEL_PADDING = 8;
    private static final int LIST_WIDTH = 122;
    private static final int LIST_HEIGHT = 108;
    private static final int EDITOR_GAP = 10;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 11;

    private final Screen parentScreen;
    private final WidgetThemeEditorSession session;
    private final ScalableText titleText = new ScalableText(
            0,
            0,
            Component.translatable("server_waypoint.theme.screen.title"),
            1.2F,
            TEXT_PRIMARY,
            font
    );
    private final ScalableText selectedVariableText = new ScalableText(
            0,
            0,
            Component.empty(),
            1.0F,
            TEXT_PRIMARY,
            CONTENT_WIDTH - LIST_WIDTH - EDITOR_GAP,
            font
    );
    private final ScalableText selectedKeyText = new ScalableText(
            0,
            0,
            Component.empty(),
            0.8F,
            TEXT_MUTED,
            CONTENT_WIDTH - LIST_WIDTH - EDITOR_GAP,
            font
    );
    private final ScalableText rgbLabel = new ScalableText(
            0, 0, Component.translatable("server_waypoint.theme.rgb"), TEXT_PRIMARY, font);
    private final ScalableText opacityLabel = new ScalableText(
            0, 0, Component.translatable("server_waypoint.theme.opacity"), TEXT_PRIMARY, font);
    private final ScalableText statusText = new ScalableText(
            0,
            0,
            Component.empty(),
            0.8F,
            SUCCESS,
            CONTENT_WIDTH,
            font
    );
    private final ThemeVariableListWidget variableList = new ThemeVariableListWidget(
            0, 0, LIST_WIDTH, LIST_HEIGHT, font, this::selectVariable);
    private final ColorHexCodeField rgbField = new ColorHexCodeField(
            0, 0, Component.translatable("server_waypoint.theme.rgb"), font);
    private final ColorSquareButton colorPickerButton = new ColorSquareButton(
            0, 0, 11, this::openSwatch);
    private final IntegerSlider opacitySlider = new IntegerSlider(
            0, 0, 84, 28, 0, 255, 255, this::updateOpacity, font);
    private final TranslucentButton resetButton = new TranslucentButton(
            0,
            0,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            Component.translatable("waypoint.reset.button"),
            this::resetTheme
    );
    private final TranslucentButton cancelButton = new TranslucentButton(
            0,
            0,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            Component.translatable("server_waypoint.cancel.button"),
            this::cancelAndClose
    );
    private final TranslucentButton saveButton = new TranslucentButton(
            0,
            0,
            BUTTON_WIDTH,
            BUTTON_HEIGHT,
            Component.translatable("server_waypoint.theme.save"),
            this::saveAndClose
    );
    private final SwatchWidget swatchWidget = new SwatchWidget(0, 0, font, this::applySwatchColor);
    private WidgetThemeVariable selectedVariable = WidgetThemeVariable.TEXT_PRIMARY;
    private boolean updatingControls;

    public WidgetThemeConfigScreen(Screen parentScreen) {
        super(Component.translatable("server_waypoint.theme.screen.title"));
        this.parentScreen = parentScreen;
        this.session = new WidgetThemeEditorSession(
                WidgetThemeManager.getTheme(),
                WaypointClientMod.getInstance().getWidgetThemePath()
        );
        this.variableList.setSelected(this.selectedVariable);
        this.rgbField.setResponder(this::updateRgb);
        this.colorPickerButton.setTooltip(Tooltip.create(
                Component.translatable("server_waypoint.theme.color_picker")));
        this.swatchWidget.visible = false;
        this.syncControls();
    }

    @Override
    protected void init() {
        super.init();
        this.acceptMovementKeys(false);
        this.addRenderableWidget(this.variableList);
        this.addRenderableWidget(this.rgbField);
        this.addRenderableWidget(this.colorPickerButton);
        this.addRenderableWidget(this.opacitySlider);
        this.addRenderableWidget(this.resetButton);
        this.addRenderableWidget(this.cancelButton);
        this.addRenderableWidget(this.saveButton);
        this.addRenderableWidget(this.swatchWidget);
        this.positionContent();
    }

    private void selectVariable(WidgetThemeVariable variable) {
        if (this.swatchWidget.visible) {
            this.closeSwatch();
        }
        this.selectedVariable = variable;
        this.variableList.setSelected(variable);
        this.statusText.setText(Component.empty());
        this.syncControls();
    }

    private void updateRgb(String value) {
        if (this.updatingControls || value.length() != 6) {
            return;
        }
        int currentColor = this.getSelectedColor();
        int color = currentColor & 0xFF000000 | this.rgbField.getColor() & 0x00FFFFFF;
        this.session.setColor(this.selectedVariable, color);
        this.colorPickerButton.setColor(color);
        this.statusText.setText(Component.empty());
    }

    private void updateOpacity(int opacity) {
        if (this.session == null || this.updatingControls) {
            return;
        }
        int color = opacity << 24 | this.getSelectedColor() & 0x00FFFFFF;
        this.session.setColor(this.selectedVariable, color);
        this.statusText.setText(Component.empty());
    }

    private void applySwatchColor(int rgb) {
        int color = this.getSelectedColor() & 0xFF000000 | rgb & 0x00FFFFFF;
        this.session.setColor(this.selectedVariable, color);
        this.syncControls();
        this.closeSwatch();
    }

    private int getSelectedColor() {
        return this.session.getDraftTheme().getColor(this.selectedVariable);
    }

    private void syncControls() {
        this.updatingControls = true;
        try {
            int color = this.getSelectedColor();
            this.selectedVariableText.setText(Component.translatable(variableTranslationKey(this.selectedVariable)));
            this.selectedKeyText.setText(this.selectedVariable.getJsonName());
            this.rgbField.setColor(color);
            this.colorPickerButton.setColor(color);
            this.opacitySlider.setValue(color >>> 24);
            this.swatchWidget.setColor(color);
            this.swatchWidget.setPreviousColor(color);
        } finally {
            this.updatingControls = false;
        }
    }

    private void openSwatch() {
        int color = this.getSelectedColor();
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.swatchWidget.visible = true;
        this.setEditorControlsActive(false);
        this.setFocused(this.swatchWidget);
    }

    private void closeSwatch() {
        this.swatchWidget.visible = false;
        this.setEditorControlsActive(true);
        this.setFocused(this.colorPickerButton);
    }

    private void setEditorControlsActive(boolean active) {
        this.variableList.active = active;
        this.rgbField.active = active;
        this.colorPickerButton.active = active;
        this.opacitySlider.active = active;
        this.resetButton.active = active;
        this.cancelButton.active = active;
        this.saveButton.active = active;
    }

    private void resetTheme() {
        this.session.reset();
        this.syncControls();
        this.statusText.setColor(SUCCESS);
        this.statusText.setText(Component.translatable("server_waypoint.theme.reset.preview"));
    }

    private void saveAndClose() {
        try {
            this.session.save();
            MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
        } catch (IOException exception) {
            WaypointClientMod.LOGGER.error("Failed to save widget theme", exception);
            this.statusText.setColor(DANGER);
            this.statusText.setText(Component.translatable("server_waypoint.theme.save.failed"));
        }
    }

    private void cancelAndClose() {
        this.session.cancel();
        MinecraftClientHelper.setScreen(this.minecraft, this.parentScreen);
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

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        boolean handled = super.mouseClicked(mouseButtonEvent, doubleClicked);
        this.normalizeModalFocus();
        return handled;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        this.normalizeModalFocus();
        return handled;
    }
    *///?}

    private void normalizeModalFocus() {
        if (this.swatchWidget.visible) {
            this.setFocused(this.swatchWidget);
        } else if (this.getFocused() == this.swatchWidget) {
            this.setFocused(this.colorPickerButton);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW_KEY_ESCAPE) {
            if (this.swatchWidget.visible) {
                this.closeSwatch();
            } else {
                this.cancelAndClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderScreenContents(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float deltaTicks
    ) {
        this.positionContent();
        int contentX = this.getCenteredX();
        int contentY = this.getCenteredY();
        int panelX = contentX - PANEL_PADDING;
        int panelY = contentY - PANEL_PADDING;
        int panelWidth = CONTENT_WIDTH + PANEL_PADDING * 2;
        int panelHeight = CONTENT_HEIGHT + PANEL_PADDING * 2;
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, getColor(PANEL_BACKGROUND));
        renderOutline(context, panelX, panelY, panelWidth, panelHeight, getColor(BORDER));

        this.titleText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.variableList.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.selectedVariableText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.selectedKeyText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.rgbLabel.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.rgbField.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.colorPickerButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.opacityLabel.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.opacitySlider.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.statusText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.resetButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.cancelButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.saveButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);

        nextLayer(context);
        this.swatchWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        previousLayer(context);
    }

    private void positionContent() {
        int x = this.getCenteredX();
        int y = this.getCenteredY();
        int editorX = x + LIST_WIDTH + EDITOR_GAP;
        int footerWidth = BUTTON_WIDTH * 3 + 16;
        int footerX = x + centered(CONTENT_WIDTH, footerWidth);

        this.titleText.setPosition(x + centered(CONTENT_WIDTH, this.titleText.getWidth()), y);
        this.variableList.setPosition(x, y + 22);
        this.selectedVariableText.setPosition(editorX, y + 22);
        this.selectedKeyText.setPosition(editorX, y + 35);
        this.rgbLabel.setPosition(editorX, y + 58);
        this.rgbField.setPosition(editorX + 34, y + 58);
        this.colorPickerButton.setPosition(editorX + 84, y + 59);
        this.opacityLabel.setPosition(editorX, y + 84);
        this.opacitySlider.setPosition(editorX, y + 99);
        this.statusText.setPosition(x, y + 134);
        this.resetButton.setPosition(footerX, y + 149);
        this.cancelButton.setPosition(footerX + BUTTON_WIDTH + 8, y + 149);
        this.saveButton.setPosition(footerX + (BUTTON_WIDTH + 8) * 2, y + 149);
        this.swatchWidget.setPosition(
                x + centered(CONTENT_WIDTH, this.swatchWidget.getWidth()),
                y + centered(CONTENT_HEIGHT, this.swatchWidget.getHeight())
        );
    }

    @Override
    int getContentWidth() {
        return CONTENT_WIDTH;
    }

    @Override
    int getContentHeight() {
        return CONTENT_HEIGHT;
    }

    private static String variableTranslationKey(WidgetThemeVariable variable) {
        return "server_waypoint.theme.variable." + variable.getJsonName();
    }

    private static final class ThemeVariableListWidget extends TreeViewWidget<WidgetThemeVariable> {
        private static final int ROW_HEIGHT = 14;

        private final Font font;
        private final Consumer<WidgetThemeVariable> selectionCallback;
        private WidgetThemeVariable selected;

        private ThemeVariableListWidget(
                int x,
                int y,
                int width,
                int height,
                Font font,
                Consumer<WidgetThemeVariable> selectionCallback
        ) {
            super(
                    x,
                    y,
                    width,
                    height,
                    ROW_HEIGHT,
                    Component.translatable("server_waypoint.theme.variables"),
                    2,
                    2,
                    2,
                    2,
                    PANEL_BACKGROUND,
                    BORDER,
                    true
            );
            this.font = font;
            this.selectionCallback = selectionCallback;
            this.updateRoots(Arrays.asList(WidgetThemeVariable.values()));
        }

        private void setSelected(WidgetThemeVariable selected) {
            this.selected = selected;
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
                TreeEntry<WidgetThemeVariable> entry,
                boolean hovered,
                int rowY,
                int contentWidth,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            WidgetThemeVariable variable = entry.value();
            if (variable == this.selected) {
                context.fill(0, rowY, contentWidth, rowY + ROW_HEIGHT, getColor(SELECTION_BACKGROUND));
            } else if (hovered) {
                context.fill(0, rowY, contentWidth, rowY + ROW_HEIGHT, getColor(ROW_HOVER_BACKGROUND));
            }
            int textY = rowY + centered(ROW_HEIGHT, this.font.lineHeight);
            drawText(context, this.font, variable.getJsonName(), 3, textY, getColor(TEXT_PRIMARY), true);
        }

        @Override
        protected boolean onEntryClicked(
                TreeEntry<WidgetThemeVariable> entry,
                double contentMouseX,
                double contentMouseY,
                int button
        ) {
            if (button != 0) {
                return false;
            }
            this.selectionCallback.accept(entry.value());
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }
}
