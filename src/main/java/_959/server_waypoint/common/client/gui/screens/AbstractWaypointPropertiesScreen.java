package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.util.ColorUtils.*;

public abstract class AbstractWaypointPropertiesScreen extends MovementAllowedScreen {
    protected final Screen previousScreen;
    protected final int CONTENT_WIDTH;
    protected final int CONTENT_HEIGHT;
    protected static final int BG_PADDING_X = 20;
    protected static final int BG_PADDING_Y = 15;
    protected final WidgetStack titleRow;
    protected final WidgetStack buttonRow;
    // main layout
    protected final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    protected final TranslucentTextField nameEditBox = new TranslucentTextField(0, 0, 60, Text.translatable("waypoint.edit.screen.name.entry"), textRenderer);
    protected final TranslucentTextField initialsEditBox = new TranslucentTextField(0, 0, 30, Text.translatable("waypoint.edit.screen.initials.entry"), textRenderer);
    protected final ColorHexCodeField colorEditBox = new ColorHexCodeField(0, 0, Text.translatable("waypoint.edit.screen.color"), textRenderer);
    protected final ColorSquareButton colorPickerButton = new ColorSquareButton(0, 0, 9, this::openSwatch);
    // coords label
    ScalableText coordsLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.coords_yaw"), 0xFFFFFFFF, textRenderer);
    protected final IntegerField xEditBox = new IntegerField(0, 0, 44, Text.of("X"), textRenderer);
    protected final IntegerField yEditBox = new IntegerField(0, 0, 44, Text.of("Y"), textRenderer);
    protected final IntegerField zEditBox = new IntegerField(0, 0, 44, Text.of("Z"), textRenderer);
    protected final IntegerField yawEditBox = new IntegerField(0, 0, 27, Text.of("Yaw"), textRenderer);
    protected final ToggleButton globalToggle = new ToggleButton(0, 0, 40, 11, Text.translatable("waypoint.local"), Text.translatable("waypoint.global"), 0x04E500,0x005AE5, (state) -> {});
    protected final SwatchWidget swatchWidget = new SwatchWidget(0, 0, textRenderer, (color) -> {this.closeSwatch(); this.colorEditBox.setColor(color); this.colorPickerButton.setColor(color);});
    protected final TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.cancel.button"), this::close);
    protected final String dimensionName;
    protected final String listName;
    protected final String waypointName;
    protected final String initials;
    protected final int x;
    protected final int y;
    protected final int z;
    protected final int rgb;
    protected final int yaw;
    protected final boolean global;

    protected abstract @NotNull WidgetStack createTitleRow();
    protected abstract @NotNull WidgetStack createButtonRow();
    protected abstract @Unmodifiable List<ClickableWidget> getTitleRowClickableWidgets();
    protected abstract @Unmodifiable List<ClickableWidget> getButtonRowClickableWidgets();

    public AbstractWaypointPropertiesScreen(Screen previousScreen, Text title, String dimensionName, String listName, @Nullable SimpleWaypoint waypoint) {
        super(title);
        this.previousScreen = previousScreen;
        this.dimensionName = dimensionName;
        this.listName = listName;
        if (waypoint == null) {
            this.waypointName = "";
            this.initials = "";
            this.x = 0;
            this.y = 0;
            this.z = 0;
            this.rgb = 0xFF000000 | randomColor();
            this.yaw = 0;
            this.global = true;
            this.colorEditBox.setColor(rgb);
            this.colorPickerButton.setColor(rgb);
            this.swatchWidget.setColor(rgb);
            this.swatchWidget.setPreviousColor(rgb);
        } else {
            this.waypointName = waypoint.name();
            this.initials = waypoint.initials();
            WaypointPos pos = waypoint.pos();
            this.x = pos.x();
            this.y = pos.y();
            this.z = pos.z();
            this.rgb = 0xFF000000 | waypoint.rgb();
            this.yaw = waypoint.yaw();
            this.global = waypoint.global();
            this.nameEditBox.setText(this.waypointName);
            this.initialsEditBox.setText(this.initials);
            int color = 0xFF000000 | this.rgb;
            this.colorEditBox.setColor(color);
            this.colorEditBox.setChangedListener(text -> this.colorPickerButton.setColor(this.colorEditBox.getColor()));
            this.colorPickerButton.setColor(color);
            this.swatchWidget.setColor(color);
            this.swatchWidget.setPreviousColor(color);
            this.xEditBox.setText(Integer.toString(this.x));
            this.xEditBox.setDefaultValue(this.x);
            this.yEditBox.setText(Integer.toString(this.y));
            this.yEditBox.setDefaultValue(this.y);
            this.zEditBox.setText(Integer.toString(this.z));
            this.zEditBox.setDefaultValue(this.z);
            this.yawEditBox.setText(Integer.toString(this.yaw));
            this.yawEditBox.setDefaultValue(this.yaw);
            this.globalToggle.setState(this.global);
        }
        this.swatchWidget.visible = false;

        // title row
        this.titleRow = createTitleRow();
        // name & initials row
        WidgetStack nameInitialsRow = new WidgetStack(0, 0, 0);
        ScalableText wpNameLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.name.entry"), 0xFFFFFFFF, textRenderer);
        ScalableText initialsLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.initials.entry"), 0xFFFFFFFF, textRenderer);
        nameInitialsRow.addChild(wpNameLabel, 0);
        nameInitialsRow.addChild(this.nameEditBox);
        nameInitialsRow.addChild(initialsLabel, 10);
        nameInitialsRow.addChild(this.initialsEditBox);

        // color row
        WidgetStack colorRow = new WidgetStack(0, 0, 0);
        ScalableText colorLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.color"), 0xFFFFFFFF, textRenderer);
        colorRow.addChild(colorLabel, 0);
        colorRow.addChild(this.colorEditBox, 6);
        colorRow.addChild(this.colorPickerButton);

        // coords row
        WidgetStack coordsRow = new WidgetStack(0, 0, 5);
        ScalableText xLabel = new ScalableText(0, 0, Text.of("X"), RED, textRenderer);
        ScalableText yLabel = new ScalableText(0, 0, Text.of("Y"), GREEN, textRenderer);
        ScalableText zLabel = new ScalableText(0, 0, Text.of("Z"), BLUE, textRenderer);
        ScalableText yawLabel = new ScalableText(0, 0, Text.of("Yaw"), 0xFFFFFFFF, textRenderer);
        this.yawEditBox.setMaxLength(4);
        coordsRow.addChild(xLabel, 0);
        coordsRow.addChild(this.xEditBox, 4);
        coordsRow.addChild(yLabel, 13);
        coordsRow.addChild(this.yEditBox, 4);
        coordsRow.addChild(zLabel, 13);
        coordsRow.addChild(this.zEditBox, 4);
        coordsRow.addChild(yawLabel, 5);
        coordsRow.addChild(this.yawEditBox, 4);

        // visibility row
        WidgetStack visibilityRow = new WidgetStack(0, 0, 0);
        ScalableText visibilityLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.visibility"), 0xFFFFFFFF, textRenderer);
        visibilityRow.addChild(visibilityLabel, 0);
        visibilityRow.addChild(this.globalToggle);

        // buttons row
        this.buttonRow = createButtonRow();

        this.mainLayout.addChild(this.titleRow, 0);
        this.mainLayout.addChild(nameInitialsRow);
        this.mainLayout.addChild(colorRow);
        this.mainLayout.addChild(this.coordsLabel);
        this.mainLayout.addChild(coordsRow);
        this.mainLayout.addChild(visibilityRow);
        this.mainLayout.addChild(this.buttonRow);

        CONTENT_WIDTH = this.mainLayout.getWidth();
        CONTENT_HEIGHT = this.mainLayout.getHeight();
    }

    public void setOffsets(int x, int y) {
        this.mainLayout.setOffsets(x, y);

        int xOffset = centered(this.CONTENT_WIDTH, this.swatchWidget.getWidth());
        int yOffset = centered(this.CONTENT_HEIGHT, this.swatchWidget.getHeight());
        this.swatchWidget.setPosition(x, y);
        this.swatchWidget.setOffsets(xOffset, yOffset);
    }

    private void openSwatch() {
        this.swatchWidget.visible = true;
        this.setFocused(this.swatchWidget);
        this.swatchWidget.setColor(this.colorPickerButton.getColor());
        this.nameEditBox.active = false;
        this.initialsEditBox.active = false;
        this.colorEditBox.active = false;
        this.colorPickerButton.active = false;
        this.xEditBox.active = false;
        this.yEditBox.active = false;
        this.zEditBox.active = false;
        this.yawEditBox.active = false;
        this.globalToggle.active = false;
        for (var child : this.getTitleRowClickableWidgets()) {
            child.active = false;
        }
        for (var child : this.getButtonRowClickableWidgets()) {
            child.active = false;
        }
    }

    private void closeSwatch() {
        this.swatchWidget.visible = false;
        this.setFocused(this.nameEditBox);
        this.nameEditBox.active = true;
        this.initialsEditBox.active = true;
        this.colorEditBox.active = true;
        this.colorPickerButton.active = true;
        this.xEditBox.active = true;
        this.yEditBox.active = true;
        this.zEditBox.active = true;
        this.yawEditBox.active = true;
        this.globalToggle.active = true;
        for (var child : this.getTitleRowClickableWidgets()) {
            child.active = true;
        }
        for (var child : this.getButtonRowClickableWidgets()) {
            child.active = true;
        }
    }

    @Override
    int getContentWidth() {
        return CONTENT_WIDTH;
    }

    @Override
    int getContentHeight() {
        return CONTENT_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        for (var child : this.getTitleRowClickableWidgets()) {
            this.addDrawableChild(child);
        }
        this.addDrawableChild(this.nameEditBox);
        this.addDrawableChild(this.initialsEditBox);
        this.addDrawableChild(this.colorEditBox);
        this.addDrawableChild(this.colorPickerButton);
        this.addDrawableChild(this.xEditBox);
        this.addDrawableChild(this.yEditBox);
        this.addDrawableChild(this.zEditBox);
        this.addDrawableChild(this.yawEditBox);
        this.addDrawableChild(this.globalToggle);
        for (var child : this.getButtonRowClickableWidgets()) {
            this.addDrawableChild(child);
        }
        this.addDrawableChild(this.swatchWidget);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Element focused = this.getFocused();
        this.acceptMovementKeys(!(focused instanceof TextFieldWidget));
        if (keyCode == 256 && this.swatchWidget.visible) {
            this.closeSwatch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centeredX = getCenteredX();
        int centeredY = getCenteredY();
        setOffsets(centeredX, centeredY);

        this.drawBackground(context);
        this.mainLayout.render(context, mouseX, mouseY, delta);
        context.getMatrices().translate(0, 0, 1);
        this.swatchWidget.renderWidget(context, mouseX, mouseY, delta);
        context.getMatrices().translate(0, 0, -1);
    }

    private void drawBackground(DrawContext context) {
        int bgWidth = (BG_PADDING_X << 1) + CONTENT_WIDTH;
        int bgHeight = (BG_PADDING_Y << 1) + CONTENT_HEIGHT;
        int bgCenteredX = centered(this.width, bgWidth);
        int bgCenteredY = centered(this.height, bgHeight);
        context.fill(bgCenteredX, bgCenteredY, bgCenteredX + bgWidth, bgCenteredY + bgHeight, 0, TRANSPARENT_BG_COLOR);
    }

    @Override
    public void close() {
        super.close();
        this.client.setScreen(this.previousScreen);
    }
}
