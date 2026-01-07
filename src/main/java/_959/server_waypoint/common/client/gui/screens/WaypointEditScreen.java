package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
import static _959.server_waypoint.util.ColorUtils.*;
import static net.minecraft.util.Colors.LIGHT_GRAY;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public class WaypointEditScreen extends MovementAllowedScreen {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private final TextRenderer textRenderer = MC.textRenderer;
    private final Screen previousScreen;
    private final int CONTENT_WIDTH;
    private final int CONTENT_HEIGHT;
    private static final int BG_PADDING_X = 20;
    private static final int BG_PADDING_Y = 15;
    // main layout
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 10, true, false);
    private final TranslucentTextField nameEditBox = new TranslucentTextField(textRenderer, 0, 0, 60, Text.of("name"));
    private final TranslucentTextField initialsEditBox = new TranslucentTextField(textRenderer, 0, 0, 30, Text.of("initials"));
    private final ColorHexCodeField colorEditBox = new ColorHexCodeField(textRenderer, 0, 0, Text.of("color"));
    private final ColorPickerButton colorPickerButton = new ColorPickerButton(0, 0, 10);
    // coords label
    ScalableText coordsLabel = new ScalableText(0, 0, Text.translatable("waypoint.edit.screen.coords_yaw"), 0xFFFFFFFF, textRenderer);
    private final IntegerField xEditBox = new IntegerField(textRenderer, 0, 0, 44, Text.of("X"));
    private final IntegerField yEditBox = new IntegerField(textRenderer, 0, 0, 44, Text.of("Y"));
    private final IntegerField zEditBox = new IntegerField(textRenderer, 0, 0, 44, Text.of("Z"));
    private final IntegerField yawEditBox = new IntegerField(textRenderer, 0, 0, 27, Text.of("Yaw"));
    private final ToggleButton globalToggle = new ToggleButton(0, 0, 40, 10, Text.translatable("waypoint.local"), Text.translatable("waypoint.global"), 0x04E500,0x005AE5, (state) -> {});
    private final TranslucentButton updateButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.update.button"), ()->{});
    private final TranslucentButton resetButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.reset.button"), ()->{});
    private final TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.cancel.button"), this::close);
    private final HSVColorPicker hsvPicker = new HSVColorPicker(10, 40, 150, 10, this::updateRGBPicker);
    private final RGBColorPicker rgbPicker = new RGBColorPicker(10, 70, 150, 10, this::updateHSVPicker);
    private final SimpleWaypoint waypoint;
    private final String dimensionName;
    private final String listName;
    private final String waypointName;
    private String initials;
    private int x;
    private int y;
    private int z;
    private int rgb;
    private int yaw;
    private boolean global;

    public WaypointEditScreen(Screen previousScreen, String dimensionName, String listName, SimpleWaypoint waypoint) {
        super(Text.translatable("waypoint.edit.screen.title", waypoint.name()));
        this.previousScreen = previousScreen;
        this.waypoint = waypoint;
        this.dimensionName = dimensionName;
        this.listName = listName;
        this.waypointName = waypoint.name();
        this.initials = waypoint.initials();
        WaypointPos pos = waypoint.pos();
        this.x = pos.x();
        this.y = pos.y();
        this.z = pos.z();
        this.rgb = waypoint.rgb();
        this.yaw = waypoint.yaw();
        this.global = waypoint.global();
        this.nameEditBox.setText(this.waypointName);
        this.initialsEditBox.setText(this.initials);
        this.initialsEditBox.setChangedListener(this::updateInitials);
        int color = 0xFF000000 | this.rgb;
        this.colorEditBox.setColor(this.rgb);
        this.colorEditBox.setChangedListener(text -> this.colorPickerButton.setColor(this.colorEditBox.getColor()));
        this.colorPickerButton.setColor(color);
        this.rgbPicker.setColor(color);
        this.hsvPicker.setColor(color);
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

    public void resetSettings() {

    }

    public void updateInitials(String initials) {
        this.initials = initials;
    }

    public void updateRGBPicker(int rgb) {
        this.rgb = rgb;
        this.rgbPicker.setColor(rgb);
    }

    public void updateHSVPicker(int rgb) {
        this.rgb = rgb;
        this.hsvPicker.setColor(rgb);
    }

    public void setOffsets(int x, int y) {
        this.mainLayout.setOffsets(x, y);
//        this.titleRow.setOffsets(x, y);
//        this.nameInitialsRow.setOffsets(x, y);
//        this.colorRow.setOffsets(x, y);
//        this.coordsLabel.setOffsets(x, y);
//        this.coordsRow.setOffsets(x, y);
//        this.visibilityRow.setOffsets(x, y);
//        this.buttonRow.setOffsets(x, y);

        this.rgbPicker.setOffsets(x, y);
        this.hsvPicker.setOffsets(x, y);
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
        this.addDrawableChild(this.xEditBox);
        this.addDrawableChild(this.yEditBox);
        this.addDrawableChild(this.zEditBox);
        this.addDrawableChild(this.yawEditBox);
        this.addDrawableChild(this.globalToggle);
        this.addDrawableChild(this.updateButton);
        this.addDrawableChild(this.resetButton);
        this.addDrawableChild(this.cancelButton);
//        this.addDrawableChild(this.rgbPicker);
//        this.addDrawableChild(this.hsvPicker);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Element focused = this.getFocused();
        this.acceptMovementKeys(!(focused instanceof TextFieldWidget));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int centeredX = getCenteredX();
        int centeredY = getCenteredY();
        setOffsets(centeredX, centeredY);

        this.drawBackground(context);
        this.mainLayout.render(context, mouseX, mouseY, delta);
//        this.titleRow.render(context, mouseX, mouseY, delta);
//        this.nameInitialsRow.render(context, mouseX, mouseY, delta);
//        this.colorRow.render(context, mouseX, mouseY, delta);
//        this.coordsLabel.render(context, mouseX, mouseY, delta);
//        this.coordsRow.render(context, mouseX, mouseY, delta);
//        this.visibilityRow.render(context, mouseX, mouseY, delta);
//        this.buttonRow.render(context, mouseX, mouseY, delta);
//        MatrixStack matrixStack = context.getMatrices();
//        this.rgbPicker.renderWidget(context, mouseX, mouseY, delta);
//        this.hsvPicker.renderWidget(context, mouseX, mouseY, delta);
        context.drawText(this.textRenderer, this.initials, 0, 0, this.rgb, true);

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