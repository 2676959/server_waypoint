//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WaypointSortButtonLabel;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
//? if >= 1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.INCOMPATIBLE_PROTOCOL;
import static _959.server_waypoint.common.client.WaypointClientMod.ClientNetworkState.NO_SERVERSIDE_SUPPORT;
import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.texture;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static final int MIDDLE_PART_WIDTH = 220;
    private static final int WAYPOINT_LIST_HORIZONTAL_PADDING = 8;
    private static final int MIN_CONTENT_HEIGHT = 120;
    private static final int MAX_CONTENT_HEIGHT = 260;
    private static final int SCREEN_MARGIN = 12;
    private static final int PANEL_PADDING = 4;
    private static final int PANEL_GAP = 4;
    private static final int SECTION_GAP = 6;
    private static final int CONTROL_GAP = 4;
    private static final int SEARCH_GAP = 4;
    private static final int DROPDOWN_ITEM_GAP = 2;
    private static final int DIMENSION_ICON_SIZE = 16;
    private static final int DIMENSION_ICON_GAP = 2;
    private static final int DIMENSION_VERTICAL_PADDING = 0;
    private static final int DIMENSION_HORIZONTAL_PADDING = 0;
    private static final int LEFT_PART_WIDTH = DIMENSION_ICON_SIZE + DIMENSION_HORIZONTAL_PADDING * 2;
    private static final int CONTROL_BUTTON_SIZE = 16;
    private static final int CONTROL_ICON_PADDING = 2;
    private static final int CONTROL_COLUMN_X_OFFSET =
            (LEFT_PART_WIDTH - CONTROL_BUTTON_SIZE) / 2;
    private static final int CONTROL_COLUMN_HEIGHT = CONTROL_BUTTON_SIZE * 4 + CONTROL_GAP * 3;
    private static final int MIN_DIMENSION_LIST_HEIGHT = DIMENSION_ICON_SIZE + DIMENSION_VERTICAL_PADDING * 2;
    private static final int MIN_WAYPOINT_LIST_HEIGHT = 28;
    private static final float RELATIVE_HEIGHT = 0.82F;
    private static boolean isRendering = false;
    private static WaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private final IconButton addWaypointButton;
    private final WaypointSearchBarWidget searchField;
    private final IconToggleButton groupModeToggle;
    private final IconDropdownMenu sortingModeDropdown;
    private final IconToggleButton allDimensionsToggle;
    private final Screen parentScreen;
    private final WaypointClientMod waypointClientMod;
    private boolean hasInitialized = false;
    private final WidgetPack leftLayout;
    private final WidgetPack controlAnchor;
    private final WidgetPack middleLayout;
    private ManagerLayoutGeometry layoutGeometry = calculateLayoutGeometry(0, 0);

    public WaypointManagerScreen(WaypointClientMod waypointClientMod, Screen parentScreen) {
        super(Component.nullToEmpty("Server Waypoints"));
        this.parentScreen = parentScreen;
        this.waypointClientMod = waypointClientMod;
        dimensionListWidget = new DimensionListWidget(
                0,
                0,
                DIMENSION_ICON_SIZE,
                100,
                DIMENSION_ICON_SIZE,
                this,
                this.font,
                this::onSelectDimension,
                LayoutFlow.Orientation.VERTICAL,
                LayoutFlow.Direction.FORWARD,
                DIMENSION_ICON_GAP,
                DIMENSION_VERTICAL_PADDING,
                DIMENSION_HORIZONTAL_PADDING
        );
        waypointListWidget = new WaypointListWidget(
                0,
                0,
                MIDDLE_PART_WIDTH - WAYPOINT_LIST_HORIZONTAL_PADDING,
                200,
                this,
                new WaypointQueryEngine(getWaypointQuerySource()),
                this.font
        );
        addWaypointButton = new IconButton(
                0,
                0,
                16,
                16,
                Component.translatable("waypoint.add.button"),
                WidgetTextures.ADD_ICON,
                this::openAddWaypointScreen
        );
        allDimensionsToggle = new IconToggleButton(
                Component.translatable("waypoint.dimension.show_all"),
                Component.translatable("waypoint.dimension.show_all"),
                WidgetTextures.CUBE_ICON,
                WidgetTextures.STACKS_ICON,
                this::setShowAllDimensions
        );
        searchField = new WaypointSearchBarWidget(
                0,
                0,
                MIDDLE_PART_WIDTH,
                Component.translatable("waypoint.search.entry"),
                this.font,
                waypointListWidget::setSearchQuery
        );
        searchField.setHint(Component.translatable("waypoint.search.hint"));
        groupModeToggle = new IconToggleButton(
                Component.translatable("waypoint.group.flat"),
                Component.translatable("waypoint.group.lists"),
                WidgetTextures.FLAT_LIST_MODE_ICON,
                WidgetTextures.GROUPED_LIST_MODE_ICON,
                this::setGroupMode
        );
        sortingModeDropdown = new IconDropdownMenu(
                Component.translatable("waypoint.sort.default")
        );
        sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.default"),
                WidgetTextures.SORT_DEFAULT_ICON,
                () -> setSortMode(WaypointSorting.SortMode.DEFAULT)
        );
        sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.name"),
                WidgetTextures.SORT_NAME_ICON,
                () -> toggleSortMode(WaypointSorting.SortMode.NAME)
        );
        sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.distance"),
                WidgetTextures.SORT_DISTANCE_ICON,
                () -> toggleSortMode(WaypointSorting.SortMode.DISTANCE)
        );
        sortingModeDropdown.addIconItem(
                Component.translatable("waypoint.sort.color"),
                WidgetTextures.SORT_COLOR_ICON,
                () -> toggleSortMode(WaypointSorting.SortMode.COLOR)
        );
        restorePersistentState();
        syncControlStates();

        this.middleLayout = new WidgetPack(
                MIDDLE_PART_WIDTH,
                MAX_CONTENT_HEIGHT,
                LayoutFlow.Orientation.VERTICAL
        );
        this.middleLayout.addChild(searchField, LayoutFlow.Direction.FORWARD);
        this.middleLayout.addChild(SpacerElement.height(SEARCH_GAP), LayoutFlow.Direction.FORWARD);
        this.middleLayout.addChild(waypointListWidget, LayoutFlow.Direction.FORWARD);

        WidgetPack controlColumn = new WidgetPack(
                CONTROL_BUTTON_SIZE,
                CONTROL_COLUMN_HEIGHT,
                LayoutFlow.Orientation.VERTICAL
        );
        controlColumn.addChild(allDimensionsToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(groupModeToggle, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(sortingModeDropdown, LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(SpacerElement.height(CONTROL_GAP), LayoutFlow.Direction.FORWARD);
        controlColumn.addChild(addWaypointButton, LayoutFlow.Direction.FORWARD);
        this.controlAnchor = new WidgetPack(
                LEFT_PART_WIDTH,
                CONTROL_COLUMN_HEIGHT,
                LayoutFlow.Orientation.HORIZONTAL
        );
        this.controlAnchor.addChild(controlColumn, LayoutFlow.Direction.FORWARD);
        this.leftLayout = new WidgetPack(
                LEFT_PART_WIDTH,
                MAX_CONTENT_HEIGHT,
                LayoutFlow.Orientation.VERTICAL
        );
        this.leftLayout.addChild(dimensionListWidget, LayoutFlow.Direction.FORWARD);
        this.leftLayout.addChild(this.controlAnchor, LayoutFlow.Direction.REVERSE);
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
        if (isRendering && shouldRefreshDimension(
                waypointListWidget.isShowingAllDimensions(),
                dimensionName,
                dimensionListWidget.getSelectedDimensionName()
        )) {
            waypointListWidget.refreshView();
        }
    }

    public static void refreshWaypointLists(String dimensionName) {
        if (isRendering && shouldRefreshDimension(
                waypointListWidget.isShowingAllDimensions(),
                dimensionName,
                dimensionListWidget.getSelectedDimensionName()
        )) {
            waypointListWidget.reCalculateRenderData();
        }
    }

    public String getSelectedDimension() {
        return dimensionListWidget.getSelectedDimensionName();
    }

    public void updateWidgetDimension() {
        closeOpenDropdownMenus();
        this.layoutGeometry = calculateLayoutGeometry(this.width, this.height);
        sortingModeDropdown.setPopupXOffset(this.layoutGeometry.dropdownXOffset(
                sortingModeDropdown.getPopupItemCount()
        ));

        int waypointListHeight = this.layoutGeometry.waypointListHeight(searchField.getVisualHeight());
        boolean middleVisible = waypointListHeight >= MIN_WAYPOINT_LIST_HEIGHT;
        searchField.visible = middleVisible;
        searchField.active = middleVisible;
        waypointListWidget.visible = middleVisible;
        waypointListWidget.active = middleVisible;
        waypointListWidget.setVisualHeight(Math.max(MIN_WAYPOINT_LIST_HEIGHT, waypointListHeight));
        this.middleLayout.setDimensions(
                MIDDLE_PART_WIDTH,
                this.layoutGeometry.contentHeight()
        );

        int dimensionListHeight = this.layoutGeometry.dimensionListHeight();
        boolean dimensionListVisible = dimensionListHeight >= MIN_DIMENSION_LIST_HEIGHT;
        dimensionListWidget.visible = dimensionListVisible;
        dimensionListWidget.active = resolveDimensionListActive(dimensionListVisible);
        dimensionListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, dimensionListHeight));

        boolean controlsVisible = this.layoutGeometry.contentHeight() >= CONTROL_COLUMN_HEIGHT;
        setControlVisibility(controlsVisible);
        this.leftLayout.setDimensions(LEFT_PART_WIDTH, this.layoutGeometry.contentHeight());
    }

    @Override
    int getContentWidth() {
        return MIDDLE_PART_WIDTH + PANEL_PADDING * 2;
    }

    @Override
    int getContentHeight() {
        return calculateLayoutGeometry(this.width, this.height).panelHeight();
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
        this.middleLayout.setPosition(
                this.layoutGeometry.middleX(),
                this.layoutGeometry.contentY()
        );
        this.leftLayout.setPosition(
                this.layoutGeometry.leftX(),
                this.layoutGeometry.contentY()
        );

        dimensionListWidget.updateDimensionNames(this.waypointClientMod.getDimensionNames());
        if (hasInitialized) {
            syncSelectedDimension(getSelectedDimension());
        } else {
            dimensionListWidget.setDimensionName(getCurrentDimensionName());
            syncSelectedDimension(getCurrentDimensionName());
            hasInitialized = true;
        }

        this.leftLayout.visitWidgets(this::addRenderableWidget);
        this.middleLayout.visitWidgets(this::addRenderableWidget);
    }

    @Override
    public void tick() {
        super.tick();
        waypointListWidget.refreshDistanceSortIfPlayerMoved();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && closeOpenDropdownMenus()) {
            return true;
        }
        GuiEventListener focused = this.getFocused();
        boolean notTyping = !(focused instanceof EditBox);
        this.acceptMovementKeys(notTyping);
        if (notTyping && keyCode == GLFW.GLFW_KEY_C) {
            closeOpenDropdownMenus();
            MinecraftClientHelper.setScreen(this.minecraft, new ClientConfigScreen(this));
            return true;
        }
        return waypointListWidget.keyPressed(keyCode, scanCode, modifiers) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (this.mouseClickedOpenDropdown(
                mouseButtonEvent.x(),
                mouseButtonEvent.y(),
                mouseButtonEvent.button()
        )) {
            return true;
        }
        this.closeDropdownsOutside(mouseButtonEvent.x(), mouseButtonEvent.y());
        if (this.mouseClickedSearchSuggestion(mouseButtonEvent.x(), mouseButtonEvent.y())) {
            return true;
        }
        return super.mouseClicked(mouseButtonEvent, doubleClicked);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.mouseClickedOpenDropdown(mouseX, mouseY, button)) {
            return true;
        }
        this.closeDropdownsOutside(mouseX, mouseY);
        if (this.mouseClickedSearchSuggestion(mouseX, mouseY)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    *///?}

    private void onSelectDimension(String dimensionName) {
        syncSelectedDimension(dimensionName);
    }

    private void setShowAllDimensions(boolean showAllDimensions) {
        waypointListWidget.setShowAllDimensions(showAllDimensions);
        dimensionListWidget.active = resolveDimensionListActive(dimensionListWidget.visible);
        persistManagerState();
    }

    static boolean resolveDimensionListActive(boolean dimensionListVisible) {
        return dimensionListVisible;
    }

    static boolean shouldRefreshDimension(
            boolean showAllDimensions,
            String changedDimension,
            String selectedDimension
    ) {
        return showAllDimensions || changedDimension.equals(selectedDimension);
    }

    private void openAddWaypointScreen() {
        MinecraftClientHelper.setScreen(new WaypointAddScreen(this, getSelectedDimension(), ""));
    }

    private static void syncSelectedDimension(String dimensionName) {
        waypointListWidget.setSelectedDimension(dimensionName);
    }

    private WaypointFilesManagerCore getWaypointQuerySource() {
        if (WaypointServerMod.runsWithClient() && WaypointServerMod.getInstance() != null) {
            return WaypointServerMod.getInstance();
        }
        return this.waypointClientMod;
    }

    @Override
    protected void renderScreenContents
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        WaypointClientMod.ClientNetworkState networkState = getNetworkState();
        if (networkState == NO_SERVERSIDE_SUPPORT) {
            Component info = Component.translatable("server_waypoint.no_serverside_support");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2,
                    WidgetThemeManager.getColor(WidgetThemeVariable.TEXT_PRIMARY));
            return;
        } else if (networkState == INCOMPATIBLE_PROTOCOL) {
            Component info = Component.translatable("server_waypoint.incompatible_protocol_version");
            int infoWidth = font.width(info);
            drawText(context, this.font, info, centered(this.width, infoWidth), this.height / 2,
                    WidgetThemeManager.getColor(WidgetThemeVariable.TEXT_PRIMARY));
            return;
        }
        this.renderPanel(
                context,
                this.layoutGeometry.middlePanelX(),
                this.layoutGeometry.panelY(),
                this.layoutGeometry.middlePanelWidth(),
                this.layoutGeometry.panelHeight()
        );
        searchField.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        waypointListWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        this.renderPanel(
                context,
                this.layoutGeometry.leftPanelX(),
                this.layoutGeometry.panelY(),
                this.layoutGeometry.leftPanelWidth(),
                this.layoutGeometry.panelHeight()
        );
        dimensionListWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        addWaypointButton.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        groupModeToggle.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        sortingModeDropdown.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        allDimensionsToggle.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        nextLayer(context);
        searchField.renderSuggestions(context, mouseX, mouseY);
        previousLayer(context);
    }

    @Override
    public void onClose() {
        isRendering = false;
        waypointListWidget = null;
        dimensionListWidget = null;
        if (parentScreen == null) super.onClose();
        else MinecraftClientHelper.setScreen(this.parentScreen);
    }

    private void syncControlStates() {
        if (waypointListWidget == null) {
            return;
        }
        WaypointSorting.SortMode activeMode = waypointListWidget.getSortMode();
        boolean reversed = waypointListWidget.isSortReversed();
        boolean groupByLists = waypointListWidget.isGroupByLists();
        allDimensionsToggle.setState(waypointListWidget.isShowingAllDimensions());
        groupModeToggle.setState(groupByLists);

        int sortIndex = switch (activeMode) {
            case DEFAULT -> 0;
            case NAME -> 1;
            case DISTANCE -> 2;
            case COLOR -> 3;
        };
        String sortTranslationKey = switch (activeMode) {
            case DEFAULT -> "waypoint.sort.default";
            case NAME -> "waypoint.sort.name";
            case DISTANCE -> "waypoint.sort.distance";
            case COLOR -> "waypoint.sort.color";
        };
        sortingModeDropdown.setSelectedIndex(sortIndex);
        sortingModeDropdown.setMessage(Component.translatable(sortTranslationKey)
                .append(WaypointSortButtonLabel.directionSuffix(activeMode, activeMode, reversed)));
    }

    private void setGroupMode(boolean groupByLists) {
        WaypointSorting.SortMode currentSortMode = waypointListWidget.getSortMode();
        WaypointSorting.SortMode resolvedSortMode = resolveSortModeForGroupMode(
                currentSortMode,
                groupByLists
        );
        if (resolvedSortMode != currentSortMode) {
            waypointListWidget.setSortMode(resolvedSortMode);
        }
        waypointListWidget.setGroupByLists(groupByLists);
        persistManagerState();
        syncControlStates();
    }

    static WaypointSorting.SortMode resolveSortModeForGroupMode(
            WaypointSorting.SortMode currentSortMode,
            boolean groupByLists
    ) {
        return !groupByLists && currentSortMode == WaypointSorting.SortMode.DEFAULT
                ? WaypointSorting.SortMode.NAME
                : currentSortMode;
    }

    private void toggleSortMode(WaypointSorting.SortMode sortMode) {
        waypointListWidget.toggleSortMode(sortMode);
        persistManagerState();
        syncControlStates();
    }

    private void setSortMode(WaypointSorting.SortMode sortMode) {
        waypointListWidget.setSortMode(sortMode);
        persistManagerState();
        syncControlStates();
    }

    private void restorePersistentState() {
        ClientConfig config = WaypointClientMod.getClientConfig();
        WaypointSorting.SortMode sortMode = config.getWaypointManagerSortMode();
        waypointListWidget.setSortMode(sortMode);
        if (config.isWaypointManagerSortReversed()) {
            waypointListWidget.toggleSortMode(sortMode);
        }
        waypointListWidget.setGroupByLists(config.isWaypointManagerGroupByLists());
        waypointListWidget.setShowAllDimensions(config.isWaypointManagerShowAllDimensions());
    }

    private void persistManagerState() {
        ClientConfig config = WaypointClientMod.getClientConfig();
        config.setWaypointManagerSortMode(waypointListWidget.getSortMode());
        config.setWaypointManagerSortReversed(waypointListWidget.isSortReversed());
        config.setWaypointManagerGroupByLists(waypointListWidget.isGroupByLists());
        config.setWaypointManagerShowAllDimensions(waypointListWidget.isShowingAllDimensions());
        this.waypointClientMod.saveConfig();
    }

    private boolean mouseClickedSearchSuggestion(double mouseX, double mouseY) {
        GuiEventListener focused = this.getFocused();
        return focused == searchField && searchField.mouseClickedSuggestion(mouseX, mouseY);
    }

    private boolean mouseClickedOpenDropdown(double mouseX, double mouseY, int button) {
        return sortingModeDropdown.isExpanded()
                && sortingModeDropdown.mouseClicked(mouseX, mouseY, button);
    }

    private void closeDropdownsOutside(double mouseX, double mouseY) {
        sortingModeDropdown.closeMenuIfOutside(mouseX, mouseY);
    }

    private boolean closeOpenDropdownMenus() {
        return sortingModeDropdown.closeMenuIfOpen();
    }

    private void setControlVisibility(boolean visible) {
        addWaypointButton.visible = visible;
        addWaypointButton.active = visible;
        groupModeToggle.visible = visible;
        groupModeToggle.active = visible;
        sortingModeDropdown.visible = visible;
        sortingModeDropdown.active = visible;
        allDimensionsToggle.visible = visible;
        allDimensionsToggle.active = visible;
        if (!visible) {
            closeOpenDropdownMenus();
        }
    }

    private void renderPanel(GuiGraphicsExtractor context, int x, int y, int width, int height) {
        context.fill(
                x,
                y,
                x + width,
                y + height,
                WidgetThemeManager.getColor(WidgetThemeVariable.PANEL_BACKGROUND)
        );
        renderOutline(
                context,
                x,
                y,
                width,
                height,
                WidgetThemeManager.getColor(WidgetThemeVariable.BORDER)
        );
    }

    static ManagerLayoutGeometry calculateLayoutGeometry(int screenWidth, int screenHeight) {
        int availableContentHeight = Math.max(
                0,
                screenHeight - (SCREEN_MARGIN + PANEL_PADDING) * 2
        );
        int preferredContentHeight = Math.max(
                MIN_CONTENT_HEIGHT,
                Math.round(screenHeight * RELATIVE_HEIGHT)
        );
        int contentHeight = Math.min(
                availableContentHeight,
                Math.min(MAX_CONTENT_HEIGHT, preferredContentHeight)
        );
        int middleX = centered(screenWidth, MIDDLE_PART_WIDTH);
        int preferredLeftX = middleX
                - PANEL_PADDING
                - LEFT_PART_WIDTH
                - PANEL_PADDING
                - PANEL_GAP;
        int leftX = Math.max(
                SCREEN_MARGIN + PANEL_PADDING,
                preferredLeftX
        );
        return new ManagerLayoutGeometry(
                contentHeight,
                middleX,
                leftX,
                centered(screenHeight, contentHeight)
        );
    }

    record ManagerLayoutGeometry(int contentHeight, int middleX, int leftX, int contentY) {
        int panelY() {
            return this.contentY - PANEL_PADDING;
        }

        int panelHeight() {
            return this.contentHeight + PANEL_PADDING * 2;
        }

        int middlePanelX() {
            return this.middleX - PANEL_PADDING;
        }

        int middlePanelWidth() {
            return MIDDLE_PART_WIDTH + PANEL_PADDING * 2;
        }

        int leftPanelX() {
            return this.leftX - PANEL_PADDING;
        }

        int leftPanelWidth() {
            return LEFT_PART_WIDTH + PANEL_PADDING * 2;
        }

        int dimensionListHeight() {
            return Math.max(0, this.contentHeight - CONTROL_COLUMN_HEIGHT - SECTION_GAP);
        }

        int waypointListHeight(int searchBarHeight) {
            return Math.max(0, this.contentHeight - searchBarHeight - SEARCH_GAP);
        }

        int panelGap() {
            return this.middlePanelX() - (this.leftPanelX() + this.leftPanelWidth());
        }

        int leftDropdownEdge(int itemCount) {
            return this.controlX() - itemCount * (CONTROL_BUTTON_SIZE + DROPDOWN_ITEM_GAP);
        }

        int controlX() {
            return this.leftX + CONTROL_COLUMN_X_OFFSET;
        }

        int dropdownXOffset(int itemCount) {
            return Math.max(0, SCREEN_MARGIN - this.leftDropdownEdge(itemCount));
        }

    }

    private static void renderIconControl(
            GuiGraphicsExtractor context,
            ShiftableClickableWidget widget,
            //$ resource_location_type_swap
            Identifier
            icon,
            boolean selected,
            boolean focusVisible
    ) {
        int backgroundColor;
        if (!widget.active) {
            backgroundColor = WidgetThemeManager.getColor(WidgetThemeVariable.CONTROL_DISABLED_BACKGROUND);
        } else if (selected) {
            backgroundColor = WidgetThemeManager.getColor(WidgetThemeVariable.SELECTION_BACKGROUND);
        } else {
            backgroundColor = WidgetThemeManager.getColor(widget.isHovered()
                    ? WidgetThemeVariable.CONTROL_HOVER_BACKGROUND
                    : WidgetThemeVariable.CONTROL_BACKGROUND);
        }
        int borderColor = WidgetThemeManager.getColor(resolveIconControlBorder(
                widget.active,
                widget.isFocused(),
                widget.isHovered(),
                focusVisible
        ));
        int x = widget.getX();
        int y = widget.getY();
        context.fill(x, y, x + widget.getWidth(), y + widget.getHeight(), backgroundColor);
        renderOutline(context, x, y, widget.getWidth(), widget.getHeight(), borderColor);
        int iconWidth = Math.max(0, widget.getWidth() - CONTROL_ICON_PADDING * 2);
        int iconHeight = Math.max(0, widget.getHeight() - CONTROL_ICON_PADDING * 2);
        if (iconWidth > 0 && iconHeight > 0) {
            texture(
                    context,
                    icon,
                    x + CONTROL_ICON_PADDING,
                    y + CONTROL_ICON_PADDING,
                    0,
                    0,
                    iconWidth,
                    iconHeight,
                    iconWidth,
                    iconHeight
            );
        }
    }

    static WidgetThemeVariable resolveIconControlBorder(
            boolean active,
            boolean focused,
            boolean hovered,
            boolean focusVisible
    ) {
        return active && (hovered || (focused && focusVisible))
                ? WidgetThemeVariable.FOCUS_RING
                : WidgetThemeVariable.BORDER;
    }

    private static final class IconDropdownMenu extends AbstractDropdownMenuWidget {
        private final List<IconMenuItem> iconItems = new ArrayList<>();
        private int selectedIndex;
        private int popupXOffset;
        private int appliedPopupXOffset;
        private int appliedPopupYOffset;

        private IconDropdownMenu(Component message) {
            super(
                    0,
                    0,
                    CONTROL_BUTTON_SIZE,
                    CONTROL_BUTTON_SIZE,
                    message,
                    LayoutFlow.Orientation.HORIZONTAL,
                    LayoutFlow.Direction.REVERSE,
                    DROPDOWN_ITEM_GAP
            );
            this.setTooltip(Tooltip.create(message));
        }

        @Override
        public void setMessage(Component message) {
            super.setMessage(message);
            this.setTooltip(Tooltip.create(message));
        }

        private void addIconItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            IconMenuItem menuItem = this.addMenuItem(new IconMenuItem(message, icon, callback));
            this.iconItems.add(menuItem);
        }

        private void setSelectedIndex(int selectedIndex) {
            this.selectedIndex = selectedIndex;
            for (int i = 0; i < this.iconItems.size(); i++) {
                this.iconItems.get(i).selected = i == selectedIndex;
            }
        }

        private void setPopupXOffset(int popupXOffset) {
            this.popupXOffset = Math.max(0, popupXOffset);
        }

        @Override
        protected void renderDropdownControl(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            renderIconControl(context, this, this.getSelectedIcon(), false, this.isExpanded());
        }

        private
        //$ resource_location_type_swap
        Identifier
        getSelectedIcon() {
            return this.selectedIndex >= 0 && this.selectedIndex < this.iconItems.size()
                    ? this.iconItems.get(this.selectedIndex).icon
                    : WidgetTextures.PLACEHOLDER_ICON;
        }

        @Override
        protected int getSelectedMenuItemIndex() {
            return this.selectedIndex;
        }

        @Override
        protected void onExpandedChanged(boolean expanded) {
            this.removeAppliedPopupOffset();
            if (!expanded) {
                return;
            }
            if (this.popupXOffset == 0) {
                return;
            }
            this.appliedPopupXOffset = this.popupXOffset;
            this.appliedPopupYOffset = this.popupXOffset > DROPDOWN_ITEM_GAP
                    ? -(CONTROL_BUTTON_SIZE + DROPDOWN_ITEM_GAP)
                    : 0;
            this.offsetMenuItems(this.appliedPopupXOffset, this.appliedPopupYOffset);
        }

        private void removeAppliedPopupOffset() {
            if (this.appliedPopupXOffset == 0 && this.appliedPopupYOffset == 0) {
                return;
            }
            this.offsetMenuItems(-this.appliedPopupXOffset, -this.appliedPopupYOffset);
            this.appliedPopupXOffset = 0;
            this.appliedPopupYOffset = 0;
        }

        private void offsetMenuItems(int xOffset, int yOffset) {
            for (IconMenuItem iconItem : this.iconItems) {
                iconItem.setPosition(iconItem.getX() + xOffset, iconItem.getY() + yOffset);
            }
        }
    }

    private static final class IconMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem {
        private final
        //$ resource_location_type_swap
        Identifier
        icon;
        private final Runnable callback;
        private boolean selected;

        private IconMenuItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            super(CONTROL_BUTTON_SIZE, CONTROL_BUTTON_SIZE, message);
            this.icon = icon;
            this.callback = callback;
            this.setTooltip(Tooltip.create(message));
        }

        @Override
        protected void onSelected() {
            this.callback.run();
        }

        @Override
        protected void renderMenuItem(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            renderIconControl(context, this, this.icon, this.selected, true);
        }
    }

    private static final class IconToggleButton extends ShiftableClickableWidget {
        private final
        //$ resource_location_type_swap
        Identifier
        state0Icon;
        private final
        //$ resource_location_type_swap
        Identifier
        state1Icon;
        private final Component state0Message;
        private final Component state1Message;
        private final Consumer<Boolean> callback;
        private boolean state;

        private IconToggleButton(
                Component state0Message,
                Component state1Message,
                //$ resource_location_type_swap
                Identifier
                state0Icon,
                //$ resource_location_type_swap
                Identifier
                state1Icon,
                Consumer<Boolean> callback
        ) {
            super(0, 0, CONTROL_BUTTON_SIZE, CONTROL_BUTTON_SIZE, state0Message);
            this.state0Message = state0Message;
            this.state1Message = state1Message;
            this.state0Icon = state0Icon;
            this.state1Icon = state1Icon;
            this.callback = callback;
            this.updatePresentation();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            this.setState(!this.state);
            this.callback.accept(this.state);
        }

        private void setState(boolean state) {
            this.state = state;
            this.updatePresentation();
        }

        private void updatePresentation() {
            Component message = this.state ? this.state1Message : this.state0Message;
            this.setMessage(message);
            this.setTooltip(Tooltip.create(message));
        }

        @Override
        public void
        //$ render_widget_method_swap
        extractWidgetRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            renderIconControl(
                    context,
                    this,
                    this.state ? this.state1Icon : this.state0Icon,
                    this.state,
                    true
            );
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

}
