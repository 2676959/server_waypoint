//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.WaypointInitials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.getAvailableDimensionNames;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.mergeDimensionNames;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.util.StringCommandBuilder.addCmd;
import static _959.server_waypoint.text.FormattedTextHelper.MAX_NAME_LENGTH;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;

public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private TranslucentTextField listNameField;
    private ComboBoxWidget dimensionField;
    private TranslucentButton addButton;

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        this(previousScreen, dimensionName, listName, null);
    }

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName, WaypointPos defaultPos) {
        super(previousScreen, Component.translatable("waypoint.add.screen.title"), dimensionName, listName, null);
        this.listNameField.setValue(listName);
        this.listNameField.setMaxLength(MAX_NAME_LENGTH);
        this.configureSuggestions();
        this.buttonRow.setXOffset(CONTENT_WIDTH);
        if (defaultPos == null) {
            defaultPos = getCurrentDefaultPos();
        }
        this.setDefaultPos(defaultPos);
        this.refreshDimensionChoices();
    }

    private WaypointPos getCurrentDefaultPos() {
        Minecraft minecraftClient = Minecraft.getInstance();
        //? if >= 1.21.11 {
        BlockPos defaultPos = MinecraftClientHelper.getMainCamera(minecraftClient).blockPosition();
        //?} else {
        /*BlockPos defaultPos = minecraftClient.gameRenderer.getMainCamera().getBlockPosition();
        *///?}
        if (minecraftClient.getCameraEntity() != null) {
            defaultPos = minecraftClient.getCameraEntity().blockPosition();
        }
        return new WaypointPos(defaultPos.getX(), defaultPos.getY(), defaultPos.getZ());
    }

    private void setDefaultPos(WaypointPos defaultPos) {
        int x = defaultPos.x();
        int y = defaultPos.y();
        int z = defaultPos.z();
        this.coordinateDefaultPos = defaultPos;
        this.xEditBox.setDefaultValue(x);
        this.yEditBox.setDefaultValue(y);
        this.zEditBox.setDefaultValue(z);
        this.xEditBox.setValue(Integer.toString(x));
        this.yEditBox.setValue(Integer.toString(y));
        this.zEditBox.setValue(Integer.toString(z));
    }

    @Override
    protected @NotNull WidgetStack createTitleRow() {
        MutableComponent dimensionLabelText = Component.translatable("waypoint.dimension.info", "");
        MutableComponent listNameLabelText = Component.translatable("waypoint.list_name.info", "");
        // title row
        WidgetStack titleRow = new WidgetStack(0, 0, 10, true, false);
        ScalableText titleLabel = new ScalableText(
                0, 0, this.getTitle(), WidgetThemeVariable.TEXT_PRIMARY, font);
        WidgetStack dimensionRow = new WidgetStack(0, 0, 0);
        ScalableText dimensionLabel = new ScalableText(
                0, 0, dimensionLabelText, WidgetThemeVariable.TEXT_PRIMARY, font);
        List<String> dimensions = mergeDimensionNames(
                WaypointClientMod.getAllAvailableDimensionNames(),
                List.of(this.dimensionName)
        );
        dimensionField = new ComboBoxWidget(0, 0, 155, 13, dimensionLabelText, font,
                dimensions, this.dimensionName, value -> {});
        dimensionField.setRenderPopupSeparately(true);
        dimensionRow.addChild(dimensionLabel, 0);
        dimensionRow.addChild(dimensionField);
        WidgetStack listNameRow = new WidgetStack(0, 0, 0);
        ScalableText listNameLabel = new ScalableText(
                0, 0, listNameLabelText, WidgetThemeVariable.TEXT_PRIMARY, font);
        listNameField = new TranslucentTextField(0, 0, 90, listNameLabelText, font);
        listNameRow.addChild(listNameLabel, 0);
        listNameRow.addChild(listNameField);

        titleRow.addChild(titleLabel, 0);
        titleRow.addChild(dimensionRow);
        titleRow.addChild(listNameRow);

        return titleRow;
    }

    private void refreshDimensionChoices() {
        getAvailableDimensionNames().thenAccept(dimensions -> this.dimensionField.setValues(
                mergeDimensionNames(dimensions, List.of(this.dimensionName))
        ));
    }

    @Override
    protected @NotNull WidgetStack createButtonRow() {
        // buttons row
        WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);
        this.addButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("waypoint.add.button"), this::sendAddCommand);

        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.addButton);
        return buttonRow;
    }

    @Override
    protected @Unmodifiable List<AbstractWidget> getTitleRowClickableWidgets() {
        return List.of(dimensionField, listNameField);
    }

    @Override
    protected @Unmodifiable List<AbstractWidget> getButtonRowClickableWidgets() {
        return List.of(addButton, cancelButton);
    }

    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClicked) {
        if (this.clickDimensionMenu(event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.clickDimensionMenu(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    @Override
    protected void renderTitleRowOverlays(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.dimensionField.renderPopup(context, mouseX, mouseY, delta);
    }

    private boolean clickDimensionMenu(double mouseX, double mouseY, int button) {
        if (this.dimensionField.isExpanded()
                && this.dimensionField.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.dimensionField);
            return true;
        }
        this.dimensionField.closeMenuIfOutside(mouseX, mouseY);
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && this.dimensionField.closeMenuIfOpen()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void sendAddCommand() {
        WaypointPos resolvedPos = this.resolveCoordinateFields();
        sendCommand(addCmd(this.dimensionField.getValue(), this.listNameField.getValue(),
                new SimpleWaypoint(
                        this.nameEditBox.getValue(),
                        this.initialsEditBox.getValue(),
                        resolvedPos,
                        this.colorPickerButton.getColor() & 0xFFFFFF,
                        this.yawEditBox.getIntValue(),
                        this.globalToggle.getState(),
                        List.of(),
                        ""
                ), false));
    }

    private void configureSuggestions() {
        this.listNameField.setSuggestionsProvider(() -> WaypointClientMod.getAllWaypointListNames(this.dimensionField.getValue()));
        this.nameEditBox.setSuggestionsProvider(() -> WaypointClientMod.getAllWaypointNames(this.dimensionField.getValue(), this.listNameField.getValue()));
        this.initialsEditBox.setSuggestionsProvider(this::getWaypointInitialsSuggestions);
    }

    private List<String> getWaypointInitialsSuggestions() {
        return WaypointInitials.getInitialsCandidatesFromName(plainText(this.nameEditBox.getValue()));
    }

}
