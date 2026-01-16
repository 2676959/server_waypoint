package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.ExpandableManager;
import _959.server_waypoint.common.client.gui.layout.WidgetStack;
import _959.server_waypoint.common.client.gui.widgets.DimensionListWidget;
import _959.server_waypoint.common.client.gui.widgets.NewWaypointListWidget;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.*;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static ScreenState STATE = ScreenState.CLOSED;
    private static NewWaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private final WaypointClientMod waypointClientMod;
    private final float relativeHeight = 0.9F;
    private final int widgetWidth = 240;
    private boolean hasInitialized = false;
    private final WidgetStack mainLayout = new WidgetStack(0, 0, 0, true, false);
//    private final ExpandableManager sizeManager = new ExpandableManager(widgetWidth, 200);

    public WaypointManagerScreen(WaypointClientMod waypointClientMod) {
        super(Text.of("Server Waypoints"));
        this.waypointClientMod = waypointClientMod;
        STATE = ScreenState.OPENED;
        dimensionListWidget = new DimensionListWidget(0, 0, widgetWidth, this.textRenderer, this::onSelectDimension);
        waypointListWidget = new NewWaypointListWidget(0, 0, widgetWidth, 200, this, this.textRenderer);
        mainLayout.addPaddedClickable(dimensionListWidget, 0);
        mainLayout.addPaddedClickable(waypointListWidget, 0);
//        sizeManager.addChild(dimensionListWidget, 0, 0);
//        sizeManager.addChild(waypointListWidget, 0, 1);
    }

    public static void resetWidgetStates() {
        NewWaypointListWidget.resetScroll();
        DimensionListWidget.resetStates();
    }

    public static void updateAll() {
        if (STATE != ScreenState.CLOSED) {
            WaypointClientMod waypointClientMod = WaypointClientMod.getInstance();
            dimensionListWidget.updateDimensionNames(waypointClientMod.getDimensionNames());
            waypointListWidget.updateWaypointLists(waypointClientMod.getDefaultWaypointLists());
        }
    }

    public static void updateWaypointLists(String dimensionName, List<WaypointList> waypointLists) {
        if (STATE != ScreenState.CLOSED && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.updateWaypointLists(waypointLists);
        }
    }

    public static void refreshWaypointLists(String dimensionName) {
        if (STATE != ScreenState.CLOSED && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.reCalculateRenderData();
        }
    }

    public String getSelectedDimension() {
        return dimensionListWidget.getSelectedDimensionName();
    }

    public void updateWidgetDimension() {
        int contentHeight = (int) (this.height * relativeHeight);
        waypointListWidget.setVisualHeight(contentHeight - dimensionListWidget.getVisualHeight());
//        this.sizeManager.setHeight();
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
        super.init();
        LOGGER.info("gui init");
        LOGGER.info("height: {}, window height: {}", getContentHeight(), this.height);
        updateWidgetDimension();
        LOGGER.info("height: {}", getContentHeight());
        int centeredX = getCenteredX();
        int centeredY = getCenteredY();
        mainLayout.setOffsets(centeredX, centeredY);

        List<WaypointList> defaultWaypointLists;
        dimensionListWidget.updateDimensionNames(this.waypointClientMod.getDimensionNames());
        if (hasInitialized) {
            defaultWaypointLists = this.waypointClientMod.getWaypointListsByDimensionName(getSelectedDimension());
        } else {
            defaultWaypointLists = this.waypointClientMod.getDefaultWaypointLists();
            dimensionListWidget.setDimensionName(getCurrentDimensionName());
            hasInitialized = true;
        }

        waypointListWidget.updateWaypointLists(defaultWaypointLists);
        this.addDrawableChild(waypointListWidget);
        this.addDrawableChild(dimensionListWidget);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return waypointListWidget.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void onSelectDimension(String dimensionName) {
        waypointListWidget.updateWaypointLists(this.waypointClientMod.getWaypointListsByDimensionName(dimensionName));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        switch (STATE) {
            case OPENED: {
                waypointListWidget.renderWidget(context, mouseX, mouseY, delta);
                dimensionListWidget.renderWidget(context, mouseX, mouseY, delta);
                break;
            }
            case NO_SERVER: {
                context.fill(0, 0, width, height, 0xAA000000);
                context.drawText(textRenderer, "Unsupported server", this.width/2, this.height/2, 0xFFFFFFFF, true);
                break;
            }
        }
    }

    @Override
    public void close() {
        STATE = ScreenState.CLOSED;
        waypointListWidget = null;
        dimensionListWidget = null;
        super.close();
    }
    public enum ScreenState {
        CLOSED,
        OPENED,
        NO_SERVER
    }
}
