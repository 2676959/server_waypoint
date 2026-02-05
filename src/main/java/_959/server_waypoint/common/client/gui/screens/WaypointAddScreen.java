package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.ScalableText;
import _959.server_waypoint.common.client.gui.widgets.TranslucentButton;
import _959.server_waypoint.common.client.gui.widgets.TranslucentTextField;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.util.CommandGenerator.addCmd;

public class WaypointAddScreen extends AbstractWaypointPropertiesScreen {
    private TranslucentTextField listNameField;
    private TranslucentTextField dimensionField;
    private TranslucentButton addButton;

    public WaypointAddScreen(Screen previousScreen, String dimensionName, String listName) {
        super(previousScreen, Text.translatable("waypoint.add.screen.title"), dimensionName, listName, null);
        this.dimensionField.setText(dimensionName);
        this.listNameField.setText(listName);
        this.buttonRow.setXOffset(CONTENT_WIDTH);
        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        BlockPos defaultPos = minecraftClient.gameRenderer.getCamera().getBlockPos();
        if (minecraftClient.cameraEntity != null) {
            defaultPos = minecraftClient.cameraEntity.getBlockPos();
        }
        int x1 = defaultPos.getX();
        int y1 = defaultPos.getY();
        int z1 = defaultPos.getZ();
        this.xEditBox.setDefaultValue(x1);
        this.yEditBox.setDefaultValue(y1);
        this.zEditBox.setDefaultValue(z1);
        this.xEditBox.setText(Integer.toString(x1));
        this.yEditBox.setText(Integer.toString(y1));
        this.zEditBox.setText(Integer.toString(z1));
    }

    @Override
    protected @NonNull WidgetStack createTitleRow() {
        MutableText dimensionLabelText = Text.translatable("waypoint.dimension.info", "");
        MutableText listNameLabelText = Text.translatable("waypoint.list_name.info", "");
        // title row
        WidgetStack titleRow = new WidgetStack(0, 0, 10, true, false);
        ScalableText titleLabel = new ScalableText(0, 0, this.getTitle(), 0xFFFFFFFF, textRenderer);
        WidgetStack dimensionRow = new WidgetStack(0, 0, 0);
        ScalableText dimensionLabel = new ScalableText(0, 0, dimensionLabelText, 0xFFFFFFFF, textRenderer);
        dimensionField = new TranslucentTextField(0, 0, 155, dimensionLabelText, textRenderer);
        dimensionRow.addChild(dimensionLabel, 0);
        dimensionRow.addChild(dimensionField);
        WidgetStack listNameRow = new WidgetStack(0, 0, 0);
        ScalableText listNameLabel = new ScalableText(0, 0, listNameLabelText, 0xFFFFFFFF, textRenderer);
        listNameField = new TranslucentTextField(0, 0, 90, listNameLabelText, textRenderer);
        listNameRow.addChild(listNameLabel, 0);
        listNameRow.addChild(listNameField);

        titleRow.addChild(titleLabel, 0);
        titleRow.addChild(dimensionRow);
        titleRow.addChild(listNameRow);

        return titleRow;
    }

    @Override
    protected @NonNull WidgetStack createButtonRow() {
        // buttons row
        WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);
        this.addButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.add.button"), this::sendAddCommand);

        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.addButton);
        return buttonRow;
    }

    @Override
    protected @Unmodifiable List<ClickableWidget> getTitleRowClickableWidgets() {
        return List.of(dimensionField, listNameField);
    }

    @Override
    protected @Unmodifiable List<ClickableWidget> getButtonRowClickableWidgets() {
        return List.of(addButton, cancelButton);
    }

    private void sendAddCommand() {
        sendCommand(addCmd(this.dimensionName, this.listNameField.getText(),
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
}
