package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.util.CommandGenerator.addCmd;

public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private TranslucentTextField listNameField;
    private TranslucentTextField dimensionField;
    private TranslucentButton addButton;

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        super(previousScreen, Component.translatable("waypoint.add.screen.title"), dimensionName, listName, null);
        this.dimensionField.setValue(dimensionName);
        this.listNameField.setValue(listName);
        this.buttonRow.setXOffset(CONTENT_WIDTH);
        Minecraft minecraftClient = Minecraft.getInstance();
        BlockPos defaultPos = minecraftClient.gameRenderer.getMainCamera().blockPosition();
        if (minecraftClient.getCameraEntity() != null) {
            defaultPos = minecraftClient.getCameraEntity().blockPosition();
        }
        int x1 = defaultPos.getX();
        int y1 = defaultPos.getY();
        int z1 = defaultPos.getZ();
        this.xEditBox.setDefaultValue(x1);
        this.yEditBox.setDefaultValue(y1);
        this.zEditBox.setDefaultValue(z1);
        this.xEditBox.setValue(Integer.toString(x1));
        this.yEditBox.setValue(Integer.toString(y1));
        this.zEditBox.setValue(Integer.toString(z1));
    }

    @Override
    protected @NotNull WidgetStack createTitleRow() {
        MutableComponent dimensionLabelText = Component.translatable("waypoint.dimension.info", "");
        MutableComponent listNameLabelText = Component.translatable("waypoint.list_name.info", "");
        // title row
        WidgetStack titleRow = new WidgetStack(0, 0, 10, true, false);
        ScalableText titleLabel = new ScalableText(0, 0, this.getTitle(), 0xFFFFFFFF, font);
        WidgetStack dimensionRow = new WidgetStack(0, 0, 0);
        ScalableText dimensionLabel = new ScalableText(0, 0, dimensionLabelText, 0xFFFFFFFF, font);
        dimensionField = new TranslucentTextField(0, 0, 155, dimensionLabelText, font);
        dimensionRow.addChild(dimensionLabel, 0);
        dimensionRow.addChild(dimensionField);
        WidgetStack listNameRow = new WidgetStack(0, 0, 0);
        ScalableText listNameLabel = new ScalableText(0, 0, listNameLabelText, 0xFFFFFFFF, font);
        listNameField = new TranslucentTextField(0, 0, 90, listNameLabelText, font);
        listNameRow.addChild(listNameLabel, 0);
        listNameRow.addChild(listNameField);

        titleRow.addChild(titleLabel, 0);
        titleRow.addChild(dimensionRow);
        titleRow.addChild(listNameRow);

        return titleRow;
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

    private void sendAddCommand() {
        sendCommand(addCmd(this.dimensionName, this.listNameField.getValue(),
                new SimpleWaypoint(
                        this.nameEditBox.getValue(),
                        this.initialsEditBox.getValue(),
                        this.xEditBox.getIntValue(),
                        this.yEditBox.getIntValue(),
                        this.zEditBox.getIntValue(),
                        this.colorPickerButton.getColor() & 0xFFFFFF,
                        this.yawEditBox.getIntValue(),
                        this.globalToggle.getState()
                ), false));
    }
}
