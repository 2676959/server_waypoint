//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ColorHexCodeField;
import _959.server_waypoint.common.client.gui.widgets.ColorSquareButton;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.SwatchWidget;
import _959.server_waypoint.common.client.gui.widgets.ToggleButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.gui.widgets.TreeViewWidget;
import _959.server_waypoint.common.client.gui.widgets.TrueFalseToggleButton;
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
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ACCENT;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ACCENT_HOVER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.CONTROL_SELECTED_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DANGER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.DIALOG_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.POPUP_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SELECTION_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SUCCESS_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_ON_ACCENT;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.WARNING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.WARNING_BACKGROUND;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;

public final class WidgetThemeConfigScreen extends MovementAllowedScreen {
    private static final int CONTENT_WIDTH = 392;
    private static final int PANEL_PADDING = 8;
    private static final int BODY_Y_OFFSET = 18;
    private static final int BODY_HEIGHT = 152;
    private static final int LIST_WIDTH = 142;
    private static final int LIST_HEIGHT = 78;
    private static final int LIST_DECORATION_PADDING = 2;
    private static final int SECTION_GAP = 0;
    private static final int EDITOR_CONTROLS_HEIGHT = BODY_HEIGHT - LIST_HEIGHT - SECTION_GAP;
    private static final int COLUMN_GAP = PANEL_PADDING;
    private static final int GALLERY_WIDTH = CONTENT_WIDTH - LIST_WIDTH - COLUMN_GAP;
    private static final int GALLERY_PADDING = 7;
    private static final int GALLERY_SAMPLE_X_OFFSET = 136;
    private static final int GALLERY_SAMPLE_WIDTH = GALLERY_WIDTH - GALLERY_SAMPLE_X_OFFSET - GALLERY_PADDING;
    private static final float STATUS_SCALE = 0.7F;
    private static final int STATUS_WRAP_WIDTH = Math.round(
            (GALLERY_WIDTH - GALLERY_PADDING * 2) / STATUS_SCALE);
    private static final int SELECTED_TEXT_WRAP_WIDTH = 144;
    private static final int SELECTED_KEY_WRAP_WIDTH = 185;
    private static final int BUTTON_WIDTH = 60;
    private static final int BUTTON_HEIGHT = 11;
    private static final int OPACITY_SLIDER_Y_OFFSET = 55;
    private static final int FOOTER_Y_OFFSET = BODY_Y_OFFSET + BODY_HEIGHT + PANEL_PADDING;

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
            0.9F,
            TEXT_PRIMARY,
            SELECTED_TEXT_WRAP_WIDTH,
            font
    );
    private final ScalableText selectedKeyText = new ScalableText(
            0,
            0,
            Component.empty(),
            0.7F,
            TEXT_MUTED,
            SELECTED_KEY_WRAP_WIDTH,
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
            STATUS_SCALE,
            SUCCESS,
            STATUS_WRAP_WIDTH,
            font
    );
    private final ThemeVariableListWidget variableList = new ThemeVariableListWidget(
            0,
            0,
            LIST_WIDTH - LIST_DECORATION_PADDING * 2,
            LIST_HEIGHT - LIST_DECORATION_PADDING * 2,
            font,
            this::selectVariable
    );
    private final ColorHexCodeField rgbField = new ColorHexCodeField(
            0, 0, Component.translatable("server_waypoint.theme.rgb"), font);
    private final ColorSquareButton colorPickerButton = new ColorSquareButton(
            0, 0, font.lineHeight, this::openSwatch);
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
    private final ScalableText galleryTitleText = new ScalableText(
            0,
            0,
            Component.translatable("server_waypoint.theme.preview.title"),
            TEXT_PRIMARY,
            font
    );
    private final ScalableText galleryPrimaryText = new ScalableText(
            0,
            0,
            Component.translatable("server_waypoint.theme.preview.primary"),
            TEXT_PRIMARY,
            font
    );
    private final ScalableText galleryMutedText = new ScalableText(
            0,
            0,
            Component.translatable("server_waypoint.theme.preview.muted"),
            0.8F,
            TEXT_MUTED,
            font
    );
    private final TranslucentTextField galleryTextField = new TranslucentTextField(
            0,
            0,
            118,
            Component.translatable("server_waypoint.theme.preview.placeholder"),
            font
    );
    private final TranslucentButton galleryButton = new TranslucentButton(
            0,
            0,
            54,
            BUTTON_HEIGHT,
            Component.translatable("server_waypoint.theme.preview.button"),
            () -> {
            }
    );
    private final TranslucentButton galleryDisabledButton = new TranslucentButton(
            0,
            0,
            60,
            BUTTON_HEIGHT,
            Component.translatable("server_waypoint.theme.preview.disabled"),
            () -> {
            }
    );
    private final ToggleButton gallerySelectionToggle = new ToggleButton(
            0,
            0,
            64,
            BUTTON_HEIGHT,
            Component.translatable("server_waypoint.theme.preview.normal"),
            Component.translatable("server_waypoint.theme.preview.selected"),
            CONTROL_BACKGROUND,
            CONTROL_SELECTED_BACKGROUND,
            ignored -> {
            }
    );
    private final TrueFalseToggleButton galleryBooleanToggle = new TrueFalseToggleButton(
            0,
            0,
            ignored -> {
            }
    );
    private final IntegerSlider gallerySlider = new IntegerSlider(
            0,
            0,
            84,
            28,
            0,
            100,
            65,
            ignored -> {
            },
            font
    );
    private final WidgetStack rgbInputRow = new WidgetStack(
            0,
            0,
            1,
            LayoutFlow.Orientation.HORIZONTAL,
            LayoutFlow.Direction.FORWARD,
            true
    );
    private final WidgetStack footerLayout = new WidgetStack(
            0,
            0,
            8,
            LayoutFlow.Orientation.HORIZONTAL,
            LayoutFlow.Direction.FORWARD,
            true
    );
    private final WidgetStack galleryButtonRow = new WidgetStack(
            0,
            0,
            6,
            LayoutFlow.Orientation.HORIZONTAL,
            LayoutFlow.Direction.FORWARD,
            true
    );
    private final WidgetStack galleryToggleRow = new WidgetStack(
            0,
            0,
            6,
            LayoutFlow.Orientation.HORIZONTAL,
            LayoutFlow.Direction.FORWARD,
            true
    );
    private final WidgetStack galleryLayout = new WidgetStack(
            0,
            0,
            0,
            LayoutFlow.Orientation.VERTICAL,
            LayoutFlow.Direction.FORWARD,
            true
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
        this.galleryTextField.setHint(Component.translatable("server_waypoint.theme.preview.placeholder"));
        this.galleryDisabledButton.active = false;
        this.gallerySelectionToggle.setState(true);
        this.galleryBooleanToggle.setState(true);

        this.rgbInputRow.addPadded(this.rgbField, 0);
        this.rgbInputRow.addPadded(this.colorPickerButton, 1);
        this.footerLayout.addPadded(this.resetButton, 0);
        this.footerLayout.addPadded(this.cancelButton, 8);
        this.footerLayout.addPadded(this.saveButton, 8);
        this.galleryButtonRow.addPadded(this.galleryButton, 0);
        this.galleryButtonRow.addPadded(this.galleryDisabledButton, 6);
        this.galleryToggleRow.addPadded(this.gallerySelectionToggle, 0);
        this.galleryToggleRow.addPadded(this.galleryBooleanToggle, 6);
        this.galleryLayout.addChild(this.galleryTitleText, 0);
        this.galleryLayout.addChild(this.galleryPrimaryText, 7);
        this.galleryLayout.addChild(this.galleryMutedText, 2);
        this.galleryLayout.addChild(this.galleryTextField, 5);
        this.galleryLayout.addChild(this.galleryButtonRow, 6);
        this.galleryLayout.addChild(this.galleryToggleRow, 6);
        this.galleryLayout.addChild(this.gallerySlider, 6);

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
        this.addRenderableWidget(this.galleryTextField);
        this.addRenderableWidget(this.galleryButton);
        this.addRenderableWidget(this.galleryDisabledButton);
        this.addRenderableWidget(this.gallerySelectionToggle);
        this.addRenderableWidget(this.galleryBooleanToggle);
        this.addRenderableWidget(this.gallerySlider);
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
        this.galleryTextField.active = active;
        this.galleryButton.active = active;
        this.galleryDisabledButton.active = false;
        this.gallerySelectionToggle.active = active;
        this.galleryBooleanToggle.active = active;
        this.gallerySlider.active = active;
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
        ThemeEditorLayoutGeometry geometry = this.calculateLayoutGeometry();
        LayoutRectangle panel = geometry.panel();
        LayoutRectangle editorControls = geometry.editorControls();
        LayoutRectangle gallery = geometry.gallery();
        context.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), getColor(PANEL_BACKGROUND));
        renderOutline(context, panel.x(), panel.y(), panel.width(), panel.height(), getColor(BORDER));
        context.fill(
                editorControls.x(),
                editorControls.y(),
                editorControls.right(),
                editorControls.bottom(),
                getColor(PANEL_BACKGROUND)
        );
        renderOutline(
                context,
                editorControls.x(),
                editorControls.y(),
                editorControls.width(),
                editorControls.height(),
                getColor(BORDER)
        );
        context.fill(
                gallery.x(),
                gallery.y(),
                gallery.right(),
                gallery.bottom(),
                getColor(PANEL_BACKGROUND)
        );
        renderOutline(context, gallery.x(), gallery.y(), gallery.width(), gallery.height(), getColor(BORDER));

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
        this.rgbInputRow.
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
        this.galleryLayout.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.renderGallerySamples(context, gallery.x(), gallery.y());
        this.statusText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);
        this.footerLayout.
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

    private void renderGallerySamples(GuiGraphicsExtractor context, int galleryX, int galleryY) {
        int sampleX = galleryX + GALLERY_SAMPLE_X_OFFSET;
        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 23,
                GALLERY_SAMPLE_WIDTH,
                18,
                "server_waypoint.theme.preview.popup",
                POPUP_BACKGROUND,
                TEXT_PRIMARY
        );
        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 45,
                GALLERY_SAMPLE_WIDTH,
                18,
                "server_waypoint.theme.preview.dialog",
                DIALOG_BACKGROUND,
                TEXT_PRIMARY
        );

        int accentGap = 3;
        int accentWidth = (GALLERY_SAMPLE_WIDTH - accentGap) / 2;
        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 67,
                accentWidth,
                13,
                "server_waypoint.theme.preview.accent",
                ACCENT,
                TEXT_ON_ACCENT
        );
        this.renderPreviewChip(
                context,
                sampleX + accentWidth + accentGap,
                galleryY + 67,
                GALLERY_SAMPLE_WIDTH - accentWidth - accentGap,
                13,
                "server_waypoint.theme.preview.hover",
                ACCENT_HOVER,
                TEXT_ON_ACCENT
        );

        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 86,
                GALLERY_SAMPLE_WIDTH,
                14,
                "server_waypoint.theme.preview.success",
                SUCCESS_BACKGROUND,
                SUCCESS
        );
        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 104,
                GALLERY_SAMPLE_WIDTH,
                14,
                "server_waypoint.theme.preview.warning",
                WARNING_BACKGROUND,
                WARNING
        );
        this.renderPreviewChip(
                context,
                sampleX,
                galleryY + 122,
                GALLERY_SAMPLE_WIDTH,
                14,
                "server_waypoint.theme.preview.danger",
                DANGER_BACKGROUND,
                DANGER
        );
    }

    private void renderPreviewChip(
            GuiGraphicsExtractor context,
            int x,
            int y,
            int width,
            int height,
            String translationKey,
            WidgetThemeVariable background,
            WidgetThemeVariable textColor
    ) {
        Component label = Component.translatable(translationKey);
        context.fill(x, y, x + width, y + height, getColor(background));
        renderOutline(context, x, y, width, height, getColor(BORDER));
        drawText(
                context,
                this.font,
                label,
                x + centered(width, this.font.width(label)),
                y + centered(height, this.font.lineHeight),
                getColor(textColor),
                true
        );
    }

    private void positionContent() {
        ThemeEditorLayoutGeometry geometry = this.calculateLayoutGeometry();
        LayoutRectangle list = geometry.variableList();
        LayoutRectangle listContent = list.inset(LIST_DECORATION_PADDING);
        LayoutRectangle editorControls = geometry.editorControls();
        LayoutRectangle gallery = geometry.gallery();
        LayoutRectangle status = geometry.status(this.statusText.getHeight());

        this.titleText.setPosition(
                geometry.contentX() + centered(CONTENT_WIDTH, this.titleText.getWidth()),
                geometry.contentY()
        );
        this.variableList.setPosition(listContent.x(), listContent.y());
        this.selectedVariableText.setPosition(editorControls.x() + 6, editorControls.y() + 4);
        this.selectedKeyText.setPosition(editorControls.x() + 6, editorControls.y() + 15);
        this.rgbLabel.setPosition(editorControls.x() + 6, editorControls.y() + 30);
        this.rgbInputRow.setPosition(editorControls.x() + 38, editorControls.y() + 28);
        this.opacityLabel.setPosition(editorControls.x() + 6, editorControls.y() + 45);
        this.opacitySlider.setPosition(editorControls.x() + 6, geometry.opacitySliderY());
        this.galleryLayout.setPosition(gallery.x() + GALLERY_PADDING, gallery.y() + GALLERY_PADDING);
        this.statusText.setPosition(status.x(), status.y());
        this.footerLayout.setPosition(geometry.footer().x(), geometry.footer().y());
        this.swatchWidget.setPosition(
                geometry.contentX() + centered(CONTENT_WIDTH, this.swatchWidget.getWidth()),
                geometry.contentY() + centered(this.getContentHeight(), this.swatchWidget.getHeight())
        );
    }

    private ThemeEditorLayoutGeometry calculateLayoutGeometry() {
        return calculateLayoutGeometry(
                this.getCenteredX(),
                this.getCenteredY(),
                this.footerLayout.getWidth(),
                this.footerLayout.getHeight()
        );
    }

    static ThemeEditorLayoutGeometry calculateLayoutGeometry(
            int contentX,
            int contentY,
            int footerWidth,
            int footerHeight
    ) {
        int contentHeight = FOOTER_Y_OFFSET + footerHeight;
        int bodyY = contentY + BODY_Y_OFFSET;
        int editorControlsY = bodyY + LIST_HEIGHT + SECTION_GAP;
        int galleryX = contentX + LIST_WIDTH + COLUMN_GAP;
        return new ThemeEditorLayoutGeometry(
                contentX,
                contentY,
                new LayoutRectangle(
                        contentX - PANEL_PADDING,
                        contentY - PANEL_PADDING,
                        CONTENT_WIDTH + PANEL_PADDING * 2,
                        contentHeight + PANEL_PADDING * 2
                ),
                new LayoutRectangle(contentX, bodyY, LIST_WIDTH, LIST_HEIGHT),
                new LayoutRectangle(
                        contentX,
                        editorControlsY,
                        LIST_WIDTH,
                        EDITOR_CONTROLS_HEIGHT
                ),
                new LayoutRectangle(galleryX, bodyY, GALLERY_WIDTH, BODY_HEIGHT),
                new LayoutRectangle(
                        contentX + centered(CONTENT_WIDTH, footerWidth),
                        contentY + FOOTER_Y_OFFSET,
                        footerWidth,
                        footerHeight
                )
        );
    }

    @Override
    int getContentWidth() {
        return CONTENT_WIDTH;
    }

    @Override
    int getContentHeight() {
        return FOOTER_Y_OFFSET + this.footerLayout.getHeight();
    }

    record LayoutRectangle(int x, int y, int width, int height) {
        int right() {
            return this.x + this.width;
        }

        int bottom() {
            return this.y + this.height;
        }

        LayoutRectangle inset(int padding) {
            return new LayoutRectangle(
                    this.x + padding,
                    this.y + padding,
                    this.width - padding * 2,
                    this.height - padding * 2
            );
        }
    }

    record ThemeEditorLayoutGeometry(
            int contentX,
            int contentY,
            LayoutRectangle panel,
            LayoutRectangle variableList,
            LayoutRectangle editorControls,
            LayoutRectangle gallery,
            LayoutRectangle footer
    ) {
        int opacitySliderY() {
            return this.editorControls.y() + OPACITY_SLIDER_Y_OFFSET;
        }

        LayoutRectangle status(int statusHeight) {
            return new LayoutRectangle(
                    this.gallery.x() + GALLERY_PADDING,
                    this.gallery.bottom() - GALLERY_PADDING - statusHeight,
                    this.gallery.width() - GALLERY_PADDING * 2,
                    statusHeight
            );
        }
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
                    LIST_DECORATION_PADDING,
                    LIST_DECORATION_PADDING,
                    LIST_DECORATION_PADDING,
                    LIST_DECORATION_PADDING,
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
