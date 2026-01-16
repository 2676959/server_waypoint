package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
import static _959.server_waypoint.util.ColorUtils.*;
import static _959.server_waypoint.util.CommandGenerator.editCmd;
import static net.minecraft.util.Colors.LIGHT_GRAY;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public class WaypointEditScreen extends MovementAllowedScreen {
    private final Screen previousScreen;
    private final int CONTENT_WIDTH;
    private final int CONTENT_HEIGHT;
    private static final int BG_PADDING_X = 20;
    private static final int BG_PADDING_Y = 15;
    // main layout
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    private final TranslucentTextField nameEditBox = new TranslucentTextField(0, 0, 60, Text.of("name"), textRenderer);
    private final TranslucentTextField initialsEditBox = new TranslucentTextField(0, 0, 30, Text.of("initials"), textRenderer);
    private final ColorHexCodeField colorEditBox = new ColorHexCodeField(0, 0, Text.of("color"), textRenderer);
    private final ColorSquareButton colorPickerButton = new ColorSquareButton(0, 0, 9, this::openSwatch);
    // coords label
    ScalableText coordsLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.coords_yaw"), 0xFFFFFFFF, textRenderer);
    private final IntegerField xEditBox = new IntegerField(0, 0, 44, Text.of("X"), textRenderer);
    private final IntegerField yEditBox = new IntegerField(0, 0, 44, Text.of("Y"), textRenderer);
    private final IntegerField zEditBox = new IntegerField(0, 0, 44, Text.of("Z"), textRenderer);
    private final IntegerField yawEditBox = new IntegerField(0, 0, 27, Text.of("Yaw"), textRenderer);
    private final ToggleButton globalToggle = new ToggleButton(0, 0, 40, 11, Text.translatable("waypoint.local"), Text.translatable("waypoint.global"), 0x04E500,0x005AE5, (state) -> {});
    private final TranslucentButton updateButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.update.button"), this::sendUpdateCommand);
    private final TranslucentButton resetButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.reset.button"), this::resetProperties);
    private final TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.cancel.button"), this::close);
    private final SwatchWidget swatchWidget = new SwatchWidget(0, 0, textRenderer, (color) -> {this.closeSwatch(); this.colorEditBox.setColor(color); this.colorPickerButton.setColor(color);});
    private final String dimensionName;
    private final String listName;
    private final String waypointName;
    private final String initials;
    private final int x;
    private final int y;
    private final int z;
    private final int rgb;
    private final int yaw;
    private final boolean global;

    public WaypointEditScreen(Screen previousScreen, String dimensionName, String listName, SimpleWaypoint waypoint) {
        super(Text.translatable("waypoint.edit.screen.title", waypoint.name()));
        this.previousScreen = previousScreen;
        this.dimensionName = dimensionName;
        this.listName = listName;
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
        this.swatchWidget.visible = false;
        this.xEditBox.setText(Integer.toString(this.x));
        this.yEditBox.setText(Integer.toString(this.y));
        this.zEditBox.setText(Integer.toString(this.z));
        this.yawEditBox.setText(Integer.toString(this.yaw));
        this.globalToggle.setState(this.global);

        ScalableText titleLabel = new ScalableText(0, 0, this.getTitle(), 0xFFFFFFFF, textRenderer);
        WidgetStack infoRow = new WidgetStack(0, 0, 5);
        ScalableText dimensionLabel = new ScalableText(0, 0, Text.translatable("waypoint.dimension.info", ""), 0.8F, LIGHT_GRAY, textRenderer);
        int dimensionColor = ColorHelper.scaleRgb(getDimensionColor(this.dimensionName).value(), 0.8F);
        ScalableText dimensionNameLabel = new ScalableText(0, 0, Text.of(this.dimensionName), 0.8F, dimensionColor, textRenderer);
        ScalableText listNameLabel = new ScalableText(0, 0, Text.translatable("waypoint.list_name.info", this.listName), 0.8F, LIGHT_GRAY, textRenderer);
        infoRow.addChild(dimensionLabel, 0);
        infoRow.addChild(dimensionNameLabel, 0);
        infoRow.addChild(listNameLabel);
        // title row
        WidgetStack titleRow = new WidgetStack(0, 0, 2, true, false);
        titleRow.addChild(titleLabel, 0);
        titleRow.addChild(infoRow);
        LOGGER.info("row height: {}", titleRow.getHeight());

        ScalableText wpNameLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.name.entry"), 0xFFFFFFFF, textRenderer);
        ScalableText initialsLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.initials.entry"), 0xFFFFFFFF, textRenderer);
        // name & initials row
        WidgetStack nameInitialsRow = new WidgetStack(0, 0, 5);
        nameInitialsRow.addChild(wpNameLabel, 0);
        nameInitialsRow.addChild(this.nameEditBox);
        nameInitialsRow.addChild(initialsLabel, 10);
        nameInitialsRow.addChild(this.initialsEditBox);

        ScalableText colorLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.color"), 0xFFFFFFFF, textRenderer);
        // color row
        WidgetStack colorRow = new WidgetStack(0, 0, 5);
        colorRow.addChild(colorLabel, 0);
        colorRow.addChild(this.colorEditBox, 10);
        colorRow.addChild(this.colorPickerButton);

        ScalableText xLabel = new ScalableText(0, 0, Text.of("X"), RED, textRenderer);
        ScalableText yLabel = new ScalableText(0, 0, Text.of("Y"), GREEN, textRenderer);
        ScalableText zLabel = new ScalableText(0, 0, Text.of("Z"), BLUE, textRenderer);
        ScalableText yawLabel = new ScalableText(0, 0, Text.of("Yaw"), 0xFFFFFFFF, textRenderer);
        this.yawEditBox.setMaxLength(4);
        // coords row
        WidgetStack coordsRow = new WidgetStack(0, 0, 5);
        coordsRow.addChild(xLabel, 0);
        coordsRow.addChild(this.xEditBox, 4);
        coordsRow.addChild(yLabel, 13);
        coordsRow.addChild(this.yEditBox, 4);
        coordsRow.addChild(zLabel, 13);
        coordsRow.addChild(this.zEditBox, 4);
        coordsRow.addChild(yawLabel, 5);
        coordsRow.addChild(this.yawEditBox, 4);

        ScalableText visibilityLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.visibility"), 0xFFFFFFFF, textRenderer);
        // visibility row
        WidgetStack visibilityRow = new WidgetStack(0, 0, 5);
        visibilityRow.addChild(visibilityLabel, 0);
        visibilityRow.addChild(this.globalToggle);

        // buttons row
        WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);
        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.resetButton);
        buttonRow.addChild(this.updateButton);

        this.mainLayout.addChild(titleRow, 0);
        this.mainLayout.addChild(nameInitialsRow);
        this.mainLayout.addChild(colorRow);
        this.mainLayout.addChild(this.coordsLabel);
        this.mainLayout.addChild(coordsRow);
        this.mainLayout.addChild(visibilityRow);
        this.mainLayout.addChild(buttonRow);

        CONTENT_WIDTH = this.mainLayout.getWidth();
        CONTENT_HEIGHT = this.mainLayout.getHeight();
        LOGGER.info("width: {}, height: {}", CONTENT_WIDTH, CONTENT_HEIGHT);
        buttonRow.setXOffset(CONTENT_WIDTH);
    }

    public void sendUpdateCommand() {
        sendCommand(editCmd(this.dimensionName, this.listName, this.waypointName,
                new SimpleWaypoint(
                        this.nameEditBox.getText(),
                        this.initialsEditBox.getText(),
                        this.xEditBox.getValue(),
                        this.yEditBox.getValue(),
                        this.zEditBox.getValue(),
                        this.colorPickerButton.getColor() & 0xFFFFFF,
                        this.yawEditBox.getValue(),
                        this.globalToggle.getState()
                ), false));
    }

    public void resetProperties() {
        this.nameEditBox.setText(this.waypointName);
        this.initialsEditBox.setText(this.initials);
        int color = 0xFF000000 | this.rgb;
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.swatchWidget.visible = false;
        this.xEditBox.setText(Integer.toString(this.x));
        this.yEditBox.setText(Integer.toString(this.y));
        this.zEditBox.setText(Integer.toString(this.z));
        this.yawEditBox.setText(Integer.toString(this.yaw));
        this.globalToggle.setState(this.global);
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
        this.updateButton.active = false;
        this.resetButton.active = false;
        this.cancelButton.active = false;
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
        this.updateButton.active = true;
        this.resetButton.active = true;
        this.cancelButton.active = true;
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
        this.addDrawableChild(this.nameEditBox);
        this.addDrawableChild(this.initialsEditBox);
        this.addDrawableChild(this.colorEditBox);
        this.addDrawableChild(this.colorPickerButton);
        this.addDrawableChild(this.xEditBox);
        this.addDrawableChild(this.yEditBox);
        this.addDrawableChild(this.zEditBox);
        this.addDrawableChild(this.yawEditBox);
        this.addDrawableChild(this.globalToggle);
        this.addDrawableChild(this.updateButton);
        this.addDrawableChild(this.resetButton);
        this.addDrawableChild(this.cancelButton);
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
        this.client.setScreen(this.previousScreen);
    }
}