//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.common.util.CoordinateInputParser;
import _959.server_waypoint.common.util.CoordinateSuggestions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.previousLayer;
import static _959.server_waypoint.util.ColorUtils.*;
import static _959.server_waypoint.util.WaypointInitials.getDefaultInitials;

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
    protected final TranslucentTextField nameEditBox = new TranslucentTextField(0, 0, 60, Component.translatable("waypoint.edit.screen.name.entry"), font);
    protected final TranslucentTextField initialsEditBox = new TranslucentTextField(0, 0, 30, Component.translatable("waypoint.edit.screen.initials.entry"), font);
    protected final ColorHexCodeField colorEditBox = new ColorHexCodeField(0, 0, Component.translatable("waypoint.edit.screen.color"), font);
    protected final ColorSquareButton colorPickerButton = new ColorSquareButton(0, 0, 9, this::openSwatch);
    // coords label
    ScalableText coordsLabel = new ScalableText(0, 0, Component.translatable("waypoint.edit.screen.coords_yaw"), 0xFFFFFFFF, font);
    protected final ScalableText xLabel = new ScalableText(0, 0, Component.nullToEmpty("X"), RED, font);
    protected final ScalableText yLabel = new ScalableText(0, 0, Component.nullToEmpty("Y"), GREEN, font);
    protected final ScalableText zLabel = new ScalableText(0, 0, Component.nullToEmpty("Z"), BLUE, font);
    protected final CoordinateField xEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("X"), font);
    protected final CoordinateField yEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Y"), font);
    protected final CoordinateField zEditBox = new CoordinateField(0, 0, 44, Component.nullToEmpty("Z"), font);
    protected final IntegerField yawEditBox = new IntegerField(0, 0, 27, Component.nullToEmpty("Yaw"), font);
    protected final ToggleButton globalToggle = new ToggleButton(0, 0, 40, 11, Component.translatable("waypoint.local"), Component.translatable("waypoint.global"), 0x04E500,0x005AE5, (state) -> {});
    protected final SwatchWidget swatchWidget = new SwatchWidget(0, 0, font, (color) -> {this.closeSwatch(); this.colorEditBox.setColor(color); this.colorPickerButton.setColor(color);});
    protected final TranslucentButton cancelButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("server_waypoint.cancel.button"), this::onClose);
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
    protected WaypointPos coordinateDefaultPos;
    private boolean enforcingCoordinateMode;

    protected abstract @NotNull WidgetStack createTitleRow();
    protected abstract @NotNull WidgetStack createButtonRow();
    protected abstract @Unmodifiable List<AbstractWidget> getTitleRowClickableWidgets();
    protected abstract @Unmodifiable List<AbstractWidget> getButtonRowClickableWidgets();

    public AbstractWaypointPropertiesScreen(Screen previousScreen, Component title, String dimensionName, String listName, @Nullable SimpleWaypoint waypoint) {
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
            this.coordinateDefaultPos = new WaypointPos(this.x, this.y, this.z);
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
            this.coordinateDefaultPos = new WaypointPos(this.x, this.y, this.z);
            this.nameEditBox.setValue(this.waypointName);
            this.initialsEditBox.setValue(this.initials);
            int color = 0xFF000000 | this.rgb;
            this.colorEditBox.setColor(color);
            this.colorEditBox.setResponder(text -> this.colorPickerButton.setColor(this.colorEditBox.getColor()));
            this.colorPickerButton.setColor(color);
            this.swatchWidget.setColor(color);
            this.swatchWidget.setPreviousColor(color);
            this.xEditBox.setValue(Integer.toString(this.x));
            this.xEditBox.setDefaultValue(this.x);
            this.yEditBox.setValue(Integer.toString(this.y));
            this.yEditBox.setDefaultValue(this.y);
            this.zEditBox.setValue(Integer.toString(this.z));
            this.zEditBox.setDefaultValue(this.z);
            this.yawEditBox.setValue(Integer.toString(this.yaw));
            this.yawEditBox.setDefaultValue(this.yaw);
            this.globalToggle.setState(this.global);
        }
        this.swatchWidget.visible = false;
        this.configureInitialsAutoUpdate();
        this.configureCoordinateModeEnforcement();
        this.configureCoordinateSuggestions();

        // title row
        this.titleRow = createTitleRow();
        // name & initials row
        WidgetStack nameInitialsRow = new WidgetStack(0, 0, 0);
        ScalableText wpNameLabel = new ScalableText(0, 0, Component.translatable("waypoint.edit.screen.name.entry"), 0xFFFFFFFF, font);
        ScalableText initialsLabel = new ScalableText(0, 0, Component.translatable("waypoint.edit.screen.initials.entry"), 0xFFFFFFFF, font);
        nameInitialsRow.addChild(wpNameLabel, 0);
        nameInitialsRow.addChild(this.nameEditBox);
        nameInitialsRow.addChild(initialsLabel, 10);
        nameInitialsRow.addChild(this.initialsEditBox);

        // color row
        WidgetStack colorRow = new WidgetStack(0, 0, 0);
        ScalableText colorLabel = new ScalableText(0, 0, Component.translatable("waypoint.edit.screen.color"), 0xFFFFFFFF, font);
        colorRow.addChild(colorLabel, 0);
        colorRow.addChild(this.colorEditBox, 6);
        colorRow.addChild(this.colorPickerButton);

        // coords row
        WidgetStack coordsRow = new WidgetStack(0, 0, 5);
        ScalableText yawLabel = new ScalableText(0, 0, Component.nullToEmpty("Yaw"), 0xFFFFFFFF, font);
        this.yawEditBox.setMaxLength(4);
        coordsRow.addChild(this.xLabel, 0);
        coordsRow.addChild(this.xEditBox, 4);
        coordsRow.addChild(this.yLabel, 13);
        coordsRow.addChild(this.yEditBox, 4);
        coordsRow.addChild(this.zLabel, 13);
        coordsRow.addChild(this.zEditBox, 4);
        coordsRow.addChild(yawLabel, 5);
        coordsRow.addChild(this.yawEditBox, 4);

        // visibility row
        WidgetStack visibilityRow = new WidgetStack(0, 0, 0);
        ScalableText visibilityLabel = new ScalableText(0, 0, Component.translatable("waypoint.edit.screen.visibility"), 0xFFFFFFFF, font);
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

    protected WaypointPos resolveCoordinateFields() {
        PlayerCoordinates playerCoordinates = getPlayerCoordinates();
        return CoordinateInputParser.resolve(
                this.xEditBox.getValue(),
                this.yEditBox.getValue(),
                this.zEditBox.getValue(),
                playerCoordinates.pos(),
                this.coordinateDefaultPos,
                playerCoordinates.pitch(),
                playerCoordinates.yaw()
        );
    }

    private void configureInitialsAutoUpdate() {
        this.nameEditBox.setResponder(
                waypointName ->
                    this.initialsEditBox.setValue(getDefaultInitials(waypointName))
        );
    }

    private void configureCoordinateModeEnforcement() {
        this.xEditBox.setValueChangedCallback(this::enforceCoordinateMode);
        this.yEditBox.setValueChangedCallback(this::enforceCoordinateMode);
        this.zEditBox.setValueChangedCallback(this::enforceCoordinateMode);
    }

    private void configureCoordinateSuggestions() {
        this.xEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.X));
        this.yEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Y));
        this.zEditBox.setSuggestionsProvider(() -> getCoordinateSuggestions(CoordinateSuggestions.Axis.Z));
        this.yawEditBox.setSuggestionsProvider(this::getYawSuggestions);
    }

    private List<String> getCoordinateSuggestions(CoordinateSuggestions.Axis axis) {
        return CoordinateSuggestions.forAxis(axis, getLookedAtBlockPos());
    }

    private List<String> getYawSuggestions() {
        return CoordinateSuggestions.forYaw(getPlayerCoordinates().yaw());
    }

    private void enforceCoordinateMode(CoordinateField editedField) {
        if (this.enforcingCoordinateMode) {
            return;
        }
        this.enforcingCoordinateMode = true;
        try {
            if (isLocalCoordinateField(editedField)) {
                setLocalIfNeeded(this.xEditBox);
                setLocalIfNeeded(this.yEditBox);
                setLocalIfNeeded(this.zEditBox);
                setCoordinateLabelsLocal(true);
            } else if (hasLocalCoordinateField()) {
                setDefaultAbsoluteIfLocal(this.xEditBox);
                setDefaultAbsoluteIfLocal(this.yEditBox);
                setDefaultAbsoluteIfLocal(this.zEditBox);
                setCoordinateLabelsLocal(false);
            } else {
                setCoordinateLabelsLocal(false);
            }
        } finally {
            this.enforcingCoordinateMode = false;
        }
    }

    private void setCoordinateLabelsLocal(boolean local) {
        this.xLabel.setText(local ? "R" : "X");
        this.yLabel.setText(local ? "U" : "Y");
        this.zLabel.setText(local ? "F" : "Z");
    }

    private boolean hasLocalCoordinateField() {
        return isLocalCoordinateField(this.xEditBox) || isLocalCoordinateField(this.yEditBox) || isLocalCoordinateField(this.zEditBox);
    }

    private boolean isLocalCoordinateField(CoordinateField field) {
        return CoordinateInputParser.isLocalCoordinateExpression(field.getValue());
    }

    private void setLocalIfNeeded(CoordinateField field) {
        if (!isLocalCoordinateField(field)) {
            field.setValue("^");
        }
    }

    private void setDefaultAbsoluteIfLocal(CoordinateField field) {
        if (isLocalCoordinateField(field)) {
            field.setValue(Integer.toString(getDefaultCoordinate(field)));
        }
    }

    private int getDefaultCoordinate(CoordinateField field) {
        if (field == this.xEditBox) {
            return this.coordinateDefaultPos.x();
        }
        if (field == this.yEditBox) {
            return this.coordinateDefaultPos.y();
        }
        return this.coordinateDefaultPos.z();
    }

    private PlayerCoordinates getPlayerCoordinates() {
        Minecraft minecraftClient = Minecraft.getInstance();
        Entity entity = minecraftClient.player != null ? minecraftClient.player : minecraftClient.getCameraEntity();
        if (entity == null) {
            return new PlayerCoordinates(this.coordinateDefaultPos, 0.0F, 0.0F);
        }
        BlockPos blockPos = entity.blockPosition();
        return new PlayerCoordinates(
                new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                entity.getXRot(),
                entity.getYRot()
        );
    }

    private @Nullable WaypointPos getLookedAtBlockPos() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }

        Vec3 start = mc.player.getEyePosition(1.0F);
        double reach;
        //? if >= 1.20.5 {
        reach = mc.player.blockInteractionRange();
        //?} else {
        /*reach = mc.gameMode == null ? 4.5D : mc.gameMode.getPickRange();
        *///?}
        Vec3 end = start.add(mc.player.getViewVector(1.0F).scale(reach));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                start,
                end,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos blockPos = hit.getBlockPos();
        if (mc.level.getBlockState(blockPos).isAir()) {
            return null;
        }
        return new WaypointPos(blockPos.getX(), blockPos.getY(), blockPos.getZ());
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
        this.setFocused(this.colorPickerButton);
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
            this.addRenderableWidget(child);
        }
        this.addRenderableWidget(this.nameEditBox);
        this.addRenderableWidget(this.initialsEditBox);
        this.addRenderableWidget(this.colorEditBox);
        this.addRenderableWidget(this.colorPickerButton);
        this.addRenderableWidget(this.xEditBox);
        this.addRenderableWidget(this.yEditBox);
        this.addRenderableWidget(this.zEditBox);
        this.addRenderableWidget(this.yawEditBox);
        this.addRenderableWidget(this.globalToggle);
        for (var child : this.getButtonRowClickableWidgets()) {
            this.addRenderableWidget(child);
        }
        this.addRenderableWidget(this.swatchWidget);
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.mouseClickedTextFieldSuggestion(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.mouseClickedTextFieldSuggestion(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.getFocused();
        this.acceptMovementKeys(!(focused instanceof EditBox));
        if (keyCode == 256 && this.swatchWidget.visible) {
            this.closeSwatch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int centeredX = getCenteredX();
        int centeredY = getCenteredY();
        setOffsets(centeredX, centeredY);

        this.drawBackground(context);
        this.mainLayout.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        nextLayer(context);
        this.renderTextFieldSuggestions(context, mouseX, mouseY);
        previousLayer(context);
        nextLayer(context);
        this.swatchWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        previousLayer(context);
    }

    private void drawBackground(GuiGraphicsExtractor context) {
        int bgWidth = (BG_PADDING_X << 1) + CONTENT_WIDTH;
        int bgHeight = (BG_PADDING_Y << 1) + CONTENT_HEIGHT;
        int bgCenteredX = centered(this.width, bgWidth);
        int bgCenteredY = centered(this.height, bgHeight);
        context.fill(bgCenteredX, bgCenteredY, bgCenteredX + bgWidth, bgCenteredY + bgHeight, TRANSPARENT_BG_COLOR);
    }

    private void renderTextFieldSuggestions(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        for (var child : this.getTitleRowClickableWidgets()) {
            if (child instanceof TranslucentTextField textField) {
                textField.renderSuggestions(context, mouseX, mouseY);
            }
        }
        this.nameEditBox.renderSuggestions(context, mouseX, mouseY);
        this.initialsEditBox.renderSuggestions(context, mouseX, mouseY);
        this.xEditBox.renderSuggestions(context, mouseX, mouseY);
        this.yEditBox.renderSuggestions(context, mouseX, mouseY);
        this.zEditBox.renderSuggestions(context, mouseX, mouseY);
        this.yawEditBox.renderSuggestions(context, mouseX, mouseY);
    }

    private boolean mouseClickedTextFieldSuggestion(double mouseX, double mouseY) {
        GuiEventListener focused = this.getFocused();
        return focused instanceof TranslucentTextField textField && textField.mouseClickedSuggestion(mouseX, mouseY);
    }

    @Override
    public void onClose() {
        MinecraftClientHelper.setScreen(this.minecraft, this.previousScreen);
    }

    private record PlayerCoordinates(WaypointPos pos, float pitch, float yaw) {
    }
}
