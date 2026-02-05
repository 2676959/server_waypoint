package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.ColorHelper;
import org.jspecify.annotations.NonNull;

import java.util.List;

import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
import static _959.server_waypoint.util.CommandGenerator.editCmd;
import static net.minecraft.util.Colors.LIGHT_GRAY;

public class WaypointEditScreen extends AbstractWaypointPropertiesScreen {
    private TranslucentButton updateButton;
    private TranslucentButton resetButton;

    @Override
    protected @NonNull WidgetStack createTitleRow() {
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
        return titleRow;
    }

    @Override
    protected @NonNull WidgetStack createButtonRow() {
        // buttons row
        WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);
        this.updateButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.update.button"), this::sendEditCommand);
        this.resetButton = new TranslucentButton(0, 0, 50, 11, Text.translatable("waypoint.reset.button"), this::resetProperties);

        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.resetButton);
        buttonRow.addChild(this.updateButton);
        return buttonRow;
    }

    @Override
    protected List<ClickableWidget> getTitleRowClickableWidgets() {
        return List.of();
    }

    @Override
    protected List<ClickableWidget> getButtonRowClickableWidgets() {
        return List.of(updateButton, resetButton, cancelButton);
    }

    public WaypointEditScreen(Screen previousScreen, String dimensionName, String listName, SimpleWaypoint waypoint) {
        super(previousScreen, Text.translatable("waypoint.edit.screen.title", waypoint.name()), dimensionName, listName, waypoint);
        this.buttonRow.setXOffset(CONTENT_WIDTH);
    }

    public void sendEditCommand() {
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
}