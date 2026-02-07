package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import _959.server_waypoint.common.client.gui.widgets.WaypointListWidget;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static boolean isRendering = false;
    private static WaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private final WaypointClientMod waypointClientMod;
    private final float relativeHeight = 0.9F;
    private boolean hasInitialized = false;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 0, true, false);

    public WaypointManagerScreen(WaypointClientMod waypointClientMod) {
        super(Text.of("Server Waypoints"));
        this.waypointClientMod = waypointClientMod;
        int widgetWidth = 240;
        dimensionListWidget = new DimensionListWidget(0, 0, widgetWidth, this, this.textRenderer, this::onSelectDimension);
        waypointListWidget = new WaypointListWidget(0, 0, widgetWidth, 200, this, this.textRenderer);
        mainLayout.addPaddedClickable(dimensionListWidget, 0);
        mainLayout.addPaddedClickable(waypointListWidget, 0);
    }

    public static void resetWidgetStates() {
        WaypointListWidget.resetScroll();
        DimensionListWidget.resetStates();
    }

    public static void updateAll() {
        if (isRendering) {
            WaypointClientMod waypointClientMod = WaypointClientMod.getInstance();
            dimensionListWidget.updateDimensionNames(waypointClientMod.getDimensionNames());
            waypointListWidget.updateWaypointLists(waypointClientMod.getCurrentWaypointLists());
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

    @SuppressWarnings("unused")
    public static void refreshWaypointLists(String dimensionName) {
        if (isRendering && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.reCalculateRenderData();
        }
    }

    public static void refreshWaypointLists() {
        if (isRendering) {
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
        this.addDrawableChild(waypointListWidget);
        this.addDrawableChild(dimensionListWidget);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_C) {
            this.client.setScreen(new ClientConfigScreen(this));
            return true;
        }
        return waypointListWidget.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSelectDimension(String dimensionName) {
        waypointListWidget.setHideButtonEnabled(dimensionName.equals(getCurrentDimensionName()));
        waypointListWidget.updateWaypointLists(this.waypointClientMod.getWaypointListsByDimensionName(dimensionName));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        waypointListWidget.renderWidget(context, mouseX, mouseY, delta);
        dimensionListWidget.renderWidget(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        isRendering = false;
        waypointListWidget = null;
        dimensionListWidget = null;
        super.close();
    }
}
