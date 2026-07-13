//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.AnchorMode;
import _959.server_waypoint.common.client.gui.layout.ExpandableManager;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WaypointSortButtonLabel;
import _959.server_waypoint.common.client.gui.render.WaypointTextures;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.INCOMPATIBLE_PROTOCOL;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT;
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static boolean isRendering = false;
    private static WaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private static ScalableText dimensionNameText;
    private static IconButton addWaypointButton;
    private static WaypointSearchBarWidget searchField;
    private static TranslucentButton defaultSortButton;
    private static TranslucentButton nameSortButton;
    private static TranslucentButton distanceSortButton;
    private static TranslucentButton colorSortButton;
    private static ToggleButton groupByListsButton;
    private final Screen parentScreen;
    private final WaypointClientMod waypointClientMod;
    private final float relativeHeight = 0.9F;
    private boolean hasInitialized = false;
    private final ExpandableManager mainLayout;
    private final ExpandableManager sortButtonLayout;
    private final ExpandableManager dimensionHeaderLayout;
    private final ExpandableManager waypointControlLayout;
    private final ExpandableManager waypointLayout;

    public WaypointManagerScreen(WaypointClientMod waypointClientMod, Screen parentScreen) {
        super(Component.nullToEmpty("Server Waypoints"));
        this.parentScreen = parentScreen;
        this.waypointClientMod = waypointClientMod;
        int widgetWidth = 240;
        int dimensionIconSize = 20;
        dimensionListWidget = new DimensionListWidget(
                0,
                0,
                dimensionIconSize,
                dimensionIconSize,
                this,
                this.font,
                this::onSelectDimension,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD
        );
        waypointListWidget = new WaypointListWidget(0, 0, widgetWidth, 200, this, new WaypointQueryEngine(getWaypointQuerySource()), this.font);
        dimensionNameText = new ScalableText(0, 0, Component.empty(), 1.2F, 0xFFFFFFFF, this.font);
        addWaypointButton = new IconButton(
                0,
                0,
                10,
                10,
                Component.translatable("waypoint.add.button"),
                WaypointTextures.ADD_ICON,
                this::openAddWaypointScreen
        );
        searchField = new WaypointSearchBarWidget(0, 0, widgetWidth, Component.translatable("waypoint.search.entry"), this.font, waypointListWidget::setSearchQuery);
        defaultSortButton = new TranslucentButton(0, 0, 60, 11, Component.translatable("waypoint.sort.default"), () -> {
            waypointListWidget.setSortMode(WaypointSorting.SortMode.DEFAULT);
            syncSortButtons();
        }, AnchorMode.OUTLINE);
        nameSortButton = new TranslucentButton(0, 0, 60, 11, Component.translatable("waypoint.sort.name"), () -> {
            toggleSortMode(WaypointSorting.SortMode.NAME);
        }, AnchorMode.OUTLINE);
        distanceSortButton = new TranslucentButton(0, 0, 60, 11, Component.translatable("waypoint.sort.distance"), () -> {
            toggleSortMode(WaypointSorting.SortMode.DISTANCE);
        }, AnchorMode.OUTLINE);
        colorSortButton = new TranslucentButton(0, 0, 60, 11, Component.translatable("waypoint.sort.color"), () -> {
            toggleSortMode(WaypointSorting.SortMode.COLOR);
        }, AnchorMode.OUTLINE);
        groupByListsButton = new ToggleButton(
                0,
                0,
                widgetWidth,
                11,
                Component.translatable("waypoint.group.flat"),
                Component.translatable("waypoint.group.lists"),
                0xFFAA0000,
                0xFF00AA00,
                groupByLists -> {
                    waypointListWidget.setGroupByLists(groupByLists);
                    syncGroupByListsButton();
                },
                AnchorMode.OUTLINE
        );
        groupByListsButton.setState(waypointListWidget.isGroupByLists());
        syncSortButtons();
        this.sortButtonLayout = new ExpandableManager(widgetWidth, defaultSortButton.getVisualHeight(), LayoutFlow.Orientation.HORIZONTAL, LayoutFlow.Direction.FORWARD);
        this.sortButtonLayout.addChild(defaultSortButton, 1, 1);
        this.sortButtonLayout.addChild(nameSortButton, 1, 1);
        this.sortButtonLayout.addChild(distanceSortButton, 1, 1);
        this.sortButtonLayout.addChild(colorSortButton, 1, 1);
        this.waypointControlLayout = new ExpandableManager(widgetWidth, sortButtonLayout.getHeight() + groupByListsButton.getVisualHeight(), LayoutFlow.Orientation.VERTICAL, LayoutFlow.Direction.FORWARD);
        this.waypointControlLayout.addChild(sortButtonLayout, 1, 0);
        this.waypointControlLayout.addChild(groupByListsButton, 1, 0);
        this.dimensionHeaderLayout = new ExpandableManager(
                waypointListWidget.getVisualWidth(),
                Math.max(dimensionNameText.getHeight(), addWaypointButton.getHeight()),
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD
        );
        this.dimensionHeaderLayout.addChild(dimensionNameText, 1, 0);
        this.dimensionHeaderLayout.addChild(addWaypointButton, 0, 0);
        this.waypointLayout = new ExpandableManager(
                waypointListWidget.getVisualWidth(),
                dimensionHeaderLayout.getHeight() + waypointControlLayout.getHeight() + waypointListWidget.getVisualHeight() + searchField.getVisualHeight(),
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD
        );
        this.waypointLayout.addChild(dimensionHeaderLayout, 1, 0);
        this.waypointLayout.addChild(waypointControlLayout, 1, 0);
        this.waypointLayout.addChild(waypointListWidget, 1, 1);
        this.waypointLayout.addChild(searchField, 1, 0);
        this.mainLayout = new ExpandableManager(
                dimensionListWidget.getVisualWidth() + waypointLayout.getWidth(),
                waypointLayout.getHeight(),
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.FORWARD
        );
        this.mainLayout.addChild(dimensionListWidget, 0, 1);
        this.mainLayout.addChild(waypointLayout, 1, 1);
    }

    public WaypointManagerScreen(WaypointClientMod waypointClientMod) {
        this(waypointClientMod, null);
    }

    public static void resetWidgetStates() {
        WaypointListWidget.resetScroll();
        DimensionListWidget.resetStates();
    }

    public static void updateCurrentView() {
        if (isRendering) {
            WaypointClientMod waypointClient = WaypointClientMod.getInstance();
            dimensionListWidget.updateDimensionNames(waypointClient.getDimensionNames());
            syncSelectedDimension(dimensionListWidget.getSelectedDimensionName());
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
                syncSelectedDimension(selectedDimensionName);
            } else if (!dimensionNames.isEmpty()) {
                dimensionListWidget.updateDimensionNames(dimensionNames);
                dimensionListWidget.setDimensionName(WaypointClientMod.getCurrentDimensionName());
                syncSelectedDimension(WaypointClientMod.getCurrentDimensionName());
            } else {
                dimensionListWidget.updateDimensionNames(dimensionNames);
                syncSelectedDimension(null);
            }
        }
    }

    public static void updateCurrentWaypointLists(List<WaypointList> waypointLists) {
        if (isRendering) {
            waypointListWidget.refreshView();
        }
    }

    public static void updateWaypointLists(String dimensionName, List<WaypointList> waypointLists) {
        if (isRendering && dimensionName.equals(dimensionListWidget.getSelectedDimensionName())) {
            waypointListWidget.refreshView();
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
        this.mainLayout.setDimensions(
                dimensionListWidget.getVisualWidth() + this.getContentWidth(),
                this.getContentHeight()
        );
    }

    @Override
    int getContentWidth() {
        return this.waypointLayout.getWidth();
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
        // Keep the waypoint column centered while the dimension rail extends from its left side.
        mainLayout.setPosition(centeredX - dimensionListWidget.getVisualWidth(), centeredY);

        dimensionListWidget.updateDimensionNames(this.waypointClientMod.getDimensionNames());
        if (hasInitialized) {
            syncSelectedDimension(getSelectedDimension());
        } else {
            dimensionListWidget.setDimensionName(getCurrentDimensionName());
            syncSelectedDimension(getCurrentDimensionName());
            hasInitialized = true;
        }

        this.addRenderableWidget(waypointListWidget);
        this.addRenderableWidget(dimensionListWidget);
        this.addRenderableWidget(addWaypointButton);
        this.addRenderableWidget(searchField);
        this.addRenderableWidget(defaultSortButton);
        this.addRenderableWidget(nameSortButton);
        this.addRenderableWidget(distanceSortButton);
        this.addRenderableWidget(colorSortButton);
        this.addRenderableWidget(groupByListsButton);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.getFocused();
        boolean notTyping = !(focused instanceof EditBox);
        this.acceptMovementKeys(notTyping);
        if (notTyping && keyCode == GLFW.GLFW_KEY_C) {
            MinecraftClientHelper.setScreen(this.minecraft, new ClientConfigScreen(this));
            return true;
        }
        return waypointListWidget.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.mouseClickedSearchSuggestion(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.mouseClickedSearchSuggestion(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    private void onSelectDimension(String dimensionName) {
        waypointListWidget.setHideButtonEnabled(dimensionName.equals(getCurrentDimensionName()));
        syncSelectedDimension(dimensionName);
    }

    private void openAddWaypointScreen() {
        MinecraftClientHelper.setScreen(new WaypointAddScreen(this, getSelectedDimension(), ""));
    }

    private static void syncSelectedDimension(String dimensionName) {
        dimensionNameText.setText(dimensionName);
        waypointListWidget.setSelectedDimension(dimensionName);
    }

    private WaypointFilesManagerCore getWaypointQuerySource() {
        if (WaypointServerMod.runsWithClient() && WaypointServerMod.getInstance() != null) {
            return WaypointServerMod.getInstance();
        }
        return this.waypointClientMod;
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
        dimensionNameText.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        addWaypointButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        waypointListWidget.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        defaultSortButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        nameSortButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        distanceSortButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        colorSortButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        groupByListsButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        searchField.
        //$ render_widget_method_swap
        extractWidgetRenderState
                (context, mouseX, mouseY, delta);
        searchField.renderSuggestions(context, mouseX, mouseY);
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
        dimensionNameText = null;
        addWaypointButton = null;
        searchField = null;
        defaultSortButton = null;
        nameSortButton = null;
        distanceSortButton = null;
        colorSortButton = null;
        groupByListsButton = null;
        if (parentScreen == null) super.onClose();
        else MinecraftClientHelper.setScreen(this.parentScreen);
    }

    private static void syncGroupByListsButton() {
        if (waypointListWidget != null && groupByListsButton != null) {
            groupByListsButton.setState(waypointListWidget.isGroupByLists());
        }
    }

    private static void syncSortButtons() {
        syncGroupByListsButton();
        if (waypointListWidget == null) {
            return;
        }
        WaypointSorting.SortMode activeMode = waypointListWidget.getSortMode();
        boolean reversed = waypointListWidget.isSortReversed();
        updateSortButton(defaultSortButton, "waypoint.sort.default", WaypointSorting.SortMode.DEFAULT, activeMode, reversed);
        updateSortButton(nameSortButton, "waypoint.sort.name", WaypointSorting.SortMode.NAME, activeMode, reversed);
        updateSortButton(distanceSortButton, "waypoint.sort.distance", WaypointSorting.SortMode.DISTANCE, activeMode, reversed);
        updateSortButton(colorSortButton, "waypoint.sort.color", WaypointSorting.SortMode.COLOR, activeMode, reversed);
    }

    private static void updateSortButton(
            TranslucentButton button,
            String translationKey,
            WaypointSorting.SortMode buttonMode,
            WaypointSorting.SortMode activeMode,
            boolean reversed
    ) {
        if (button == null) {
            return;
        }
        button.setText(Component.translatable(translationKey)
                .append(WaypointSortButtonLabel.directionSuffix(buttonMode, activeMode, reversed)));
    }

    private static void toggleSortMode(WaypointSorting.SortMode sortMode) {
        waypointListWidget.toggleSortMode(sortMode);
        syncSortButtons();
    }

    private boolean mouseClickedSearchSuggestion(double mouseX, double mouseY) {
        GuiEventListener focused = this.getFocused();
        return focused == searchField && searchField.mouseClickedSuggestion(mouseX, mouseY);
    }
}
