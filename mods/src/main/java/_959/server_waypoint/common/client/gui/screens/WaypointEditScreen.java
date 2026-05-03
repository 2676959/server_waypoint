package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.ColorHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.MUTED_FONT_COLOR;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
import static _959.server_waypoint.util.CommandGenerator.editCmd;

public class WaypointEditScreen extends AbstractWaypointPropertiesScreen {
    private TranslucentButton updateButton;
    private TranslucentButton resetButton;

    @Override
    protected @NotNull WidgetStack createTitleRow() {
        ScalableText titleLabel = new ScalableText(0, 0, this.getTitle(), 0xFFFFFFFF, font);
        WidgetStack infoRow = new WidgetStack(0, 0, 5);
        ScalableText dimensionLabel = new ScalableText(0, 0, Component.translatable("waypoint.dimension.info", ""), 0.8F, MUTED_FONT_COLOR, font);
        int dimensionColor = ColorHelper.scaleRgb(getDimensionColor(this.dimensionName).value(), 0.8F);
        ScalableText dimensionNameLabel = new ScalableText(0, 0, Component.nullToEmpty(this.dimensionName), 0.8F, dimensionColor, font);
        ScalableText listNameLabel = new ScalableText(0, 0, Component.translatable("waypoint.list_name.info", this.listName), 0.8F, MUTED_FONT_COLOR, font);
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
    protected @NotNull WidgetStack createButtonRow() {
        // buttons row
        WidgetStack buttonRow = new WidgetStack(0, 0, 10, false);
        this.updateButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("waypoint.update.button"), this::sendEditCommand);
        this.resetButton = new TranslucentButton(0, 0, 50, 11, Component.translatable("waypoint.reset.button"), this::resetProperties);

        buttonRow.addChild(this.cancelButton, 2);
        buttonRow.addChild(this.resetButton);
        buttonRow.addChild(this.updateButton);
        return buttonRow;
    }

    @Override
    protected List<AbstractWidget> getTitleRowClickableWidgets() {
        return List.of();
    }

    @Override
    protected List<AbstractWidget> getButtonRowClickableWidgets() {
        return List.of(updateButton, resetButton, cancelButton);
    }

    public WaypointEditScreen(Screen previousScreen, String dimensionName, String listName, SimpleWaypoint waypoint) {
        super(previousScreen, Component.translatable("waypoint.edit.screen.title", waypoint.name()), dimensionName, listName, waypoint);
        this.buttonRow.setXOffset(CONTENT_WIDTH);
    }

    public void sendEditCommand() {
        sendCommand(editCmd(this.dimensionName, this.listName, this.waypointName,
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

    public void resetProperties() {
        this.nameEditBox.setValue(this.waypointName);
        this.initialsEditBox.setValue(this.initials);
        int color = 0xFF000000 | this.rgb;
        this.colorEditBox.setColor(color);
        this.colorPickerButton.setColor(color);
        this.swatchWidget.setColor(color);
        this.swatchWidget.setPreviousColor(color);
        this.swatchWidget.visible = false;
        this.xEditBox.setValue(Integer.toString(this.x));
        this.yEditBox.setValue(Integer.toString(this.y));
        this.zEditBox.setValue(Integer.toString(this.z));
        this.yawEditBox.setValue(Integer.toString(this.yaw));
        this.globalToggle.setState(this.global);
    }
}
