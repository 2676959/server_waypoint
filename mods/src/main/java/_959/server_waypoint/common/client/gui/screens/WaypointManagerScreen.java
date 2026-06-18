//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import _959.server_waypoint.common.client.gui.widgets.WaypointListWidget;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.waypoint.WaypointList;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.INCOMPATIBLE_PROTOCOL;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT;
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawText;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static boolean isRendering = false;
    private static WaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private final WaypointClientMod waypointClientMod;
    private final float relativeHeight = 0.9F;
    private boolean hasInitialized = false;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 0, true, false);

    public WaypointManagerScreen(WaypointClientMod waypointClientMod) {
        super(Component.nullToEmpty("Server Waypoints"));
        this.waypointClientMod = waypointClientMod;
        int widgetWidth = 240;
        dimensionListWidget = new DimensionListWidget(0, 0, widgetWidth, this, this.font, this::onSelectDimension);
        waypointListWidget = new WaypointListWidget(0, 0, widgetWidth, 200, this, this.font);
        mainLayout.addPaddedClickable(dimensionListWidget, 0);
        mainLayout.addPaddedClickable(waypointListWidget, 0);
    }

    public static void resetWidgetStates() {
        WaypointListWidget.resetScroll();
        DimensionListWidget.resetStates();
    }

    public static void updateCurrentView() {
        if (isRendering) {
            WaypointClientMod waypointClient = WaypointClientMod.getInstance();
            dimensionListWidget.updateDimensionNames(waypointClient.getDimensionNames());
            waypointListWidget.updateWaypointLists(waypointClient.getWaypointListsByDimensionName(dimensionListWidget.getSelectedDimensionName()));
        }
    }

    public static void updateDimensionList() {
        if (isRendering) {
            WaypointClientMod waypointClient = WaypointClientMod.getInstance();
            String selectedDimensionName =  dimensionListWidget.getSelectedDimensionName();
            List<String> dimensionNames = waypointClient.getDimensionNames();
            if (dimensionNames.contains(selectedDimensionName)) {
                dimensionListWidget.updateDimensionNames(dimensionNames);
                dimensionListWidget.setDimensionName(selectedDimensionName);
            } else if (!dimensionNames.isEmpty()) {
                dimensionListWidget.updateDimensionNames(dimensionNames);
                dimensionListWidget.setDimensionName(WaypointClientMod.getCurrentDimensionName());
                waypointListWidget.updateWaypointLists(waypointClient.getCurrentWaypointLists());
            } else {
                dimensionListWidget.updateDimensionNames(dimensionNames);
            }
        }
    }

    public static void updateCurrentWaypointLists(List<WaypointList> waypointLists) {
        if (isRendering) {
            waypointListWidget.updateWaypointLists(waypointLists);
        }
    }

    public static void updateWaypointLists(String dimensionName, List<WaypointList> waypointLists) {
        if (isRendering && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.updateWaypointLists(waypointLists);
        }
    }

    public static void refreshWaypointLists(String dimensionName) {
        if (isRendering && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.reCalculateRenderData();
        }
    }

    public String getSelectedDimension() {
        return dimensionListWidget.getSelectedDimensionName();
    }

    public void updateWidgetDimension() {
        int contentHeight = (int) (this.height * relativeHeight);
        waypointListWidget.setVisualHeight(contentHeight - dimensionListWidget.getVisualHeight());
    }

    @Override
    int getContentWidth() {
        return dimensionListWidget.getVisualWidth();
    }

    @Override
    int getContentHeight() {
        return (int) (this.height * relativeHeight);
    }

    @Override
    protected void init() {
        isRendering = true;
        super.init();
        String currentDimension = WaypointClientMod.getCurrentDimensionName();
        if (WaypointServerMod.runsWithClient()) {
            WaypointServerMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        } else {
            if (WaypointClientMod.getNetworkState() == WaypointClientMod.ClientNetworkState.SYNC_FINISHED) {
                WaypointClientMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
            } else {
                return;
            }
        }
        updateWidgetDimension();
        int centeredX = getCenteredX();
        int centeredY = getCenteredY();
        mainLayout.setOffsets(centeredX, centeredY);

        List<WaypointList> defaultWaypointLists;
        dimensionListWidget.updateDimensionNames(this.waypointClientMod.getDimensionNames());
        if (hasInitialized) {
            defaultWaypointLists = this.waypointClientMod.getWaypointListsByDimensionName(getSelectedDimension());
        } else {
            defaultWaypointLists = this.waypointClientMod.getCurrentWaypointLists();
            dimensionListWidget.setDimensionName(getCurrentDimensionName());
            hasInitialized = true;
        }

        waypointListWidget.updateWaypointLists(defaultWaypointLists);
        this.addRenderableWidget(waypointListWidget);
        this.addRenderableWidget(dimensionListWidget);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_C) {
            MinecraftClientHelper.setScreen(this.minecraft, new ClientConfigScreen(this));
            return true;
        }
        return waypointListWidget.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSelectDimension(String dimensionName) {
        waypointListWidget.setHideButtonEnabled(dimensionName.equals(getCurrentDimensionName()));
        waypointListWidget.updateWaypointLists(this.waypointClientMod.getWaypointListsByDimensionName(dimensionName));
    }

    @Override
    public void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        WaypointClientMod.ClientNetworkState networkState = getNetworkState();
        if (networkState == NO_SERVERSIDE_SUPPORT) {
            Component info = Component.translatable("server_waypoint.no_serverside_support");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2, 0xFFFFFFFF);
            return;
        } else if (networkState == INCOMPATIBLE_PROTOCOL) {
            Component info = Component.translatable("server_waypoint.incompatible_protocol_version");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2, 0xFFFFFFFF);
            return;
        }
        waypointListWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        dimensionListWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        isRendering = false;
        waypointListWidget = null;
        dimensionListWidget = null;
        super.onClose();
    }
}
