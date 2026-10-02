//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.ClientConfig;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.layout.OpposedExpansionLayout;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import java.util.Map;
import _959.server_waypoint.common.client.gui.layout.WidgetPack;
import _959.server_waypoint.common.client.gui.render.WaypointSortButtonLabel;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.*;
import _959.server_waypoint.common.client.util.ClientDimensionCatalog;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.WaypointClientMod.getNetworkState;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.util.ClientDimensionCatalog.mergeWithCachedDimensions;

public class WaypointManagerScreen extends MovementAllowedScreen {
    private static final float MIDDLE_PART_WIDTH_RATIO = 0.38F;
    private static final float DETAILS_PART_WIDTH_RATIO = 0.32F;
    private static final int MIN_MIDDLE_PART_WIDTH = 180;
    private static final int MAX_MIDDLE_PART_WIDTH = 360;
    private static final int MIN_DETAILS_PART_WIDTH = 150;
    private static final int MAX_DETAILS_PART_WIDTH = 320;
    private static final int WAYPOINT_LIST_HORIZONTAL_PADDING = 8;
    private static final int MIN_CONTENT_HEIGHT = 120;
    private static final int MAX_CONTENT_HEIGHT = 400;
    private static final int SCREEN_MARGIN = 12;
    private static final int PANEL_PADDING = 4;
    private static final int PANEL_GAP = 2;
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
    private static final int MIN_DIMENSION_LIST_HEIGHT = DIMENSION_ICON_SIZE + DIMENSION_VERTICAL_PADDING * 2;
    private static final int MIN_WAYPOINT_LIST_HEIGHT = 28;
    private static final float RELATIVE_HEIGHT = 0.82F;
    private static boolean isRendering = false;
    private static @Nullable WaypointManagerScreen activeScreen;
    private static WaypointListWidget waypointListWidget;
    private static DimensionListWidget dimensionListWidget;
    private final WaypointDetailsWidget waypointDetailsWidget;
    private final IconButton addWaypointButton;
    private final WaypointSearchBarWidget searchField;
    private final IconToggleButton groupModeToggle;
    private final IconToggleButton sortOrderToggle;
    private final IconDropdownMenu sortingModeDropdown;
    private final IconToggleButton allDimensionsToggle;
    private final Screen parentScreen;
    private final IconToggleButton serverScopeToggle;
    private final RemoteWaypointPanel remotePanel;
    private boolean showingRemote;
    private final ServerListWidget serverListWidget;
    private final SeparatorWidget selectorSeparator;
    private final SeparatorWidget serverControlSeparator;
    private String localDimension;
    private Map<RemoteServerId, CatalogReceiver.View> remoteServers = Map.of();
    private boolean requestedAvailableDimensions;
    private List<String> availableDimensionNames = List.of();
    private final WaypointClientMod waypointClientMod;
    private final ScalableText statusMessage;
    private final IconMenuItem distanceSortItem;
    private ManagerViewState builtState = ManagerViewState.LOADING;
    private boolean hasInitialized = false;
    private boolean hasRemoteServers;
    private final WidgetPack middleLayout;
    private ManagerLayoutGeometry layoutGeometry = calculateLayoutGeometry(0, 0);

    public WaypointManagerScreen(WaypointClientMod waypointClientMod, Screen parentScreen) {
        super(Component.translatable("server_waypoint.manager.title"));
        this.parentScreen = parentScreen;
        this.waypointClientMod = waypointClientMod;
        this.remotePanel = new RemoteWaypointPanel(waypointClientMod, this.font);
        this.statusMessage = new ScalableText(0, 0, Component.empty(), WidgetThemeVariable.TEXT_PRIMARY, this.font);
        this.serverScopeToggle = new IconToggleButton(
                Component.translatable("waypoint.remote.gui.local"),
                Component.translatable("waypoint.remote.title"),
                WidgetTextures.HOME_ICON,
                WidgetTextures.LAN_SERVERS_ICON,
                this::setShowingRemote
        );
        this.serverListWidget = new ServerListWidget(DIMENSION_ICON_SIZE, DIMENSION_ICON_GAP, server -> refreshRemoteSelectors(true));
        this.selectorSeparator = new SeparatorWidget(0, 0, LEFT_PART_WIDTH, 1, WidgetThemeVariable.BORDER);
        this.serverControlSeparator = new SeparatorWidget(0, 0, LEFT_PART_WIDTH, 1, WidgetThemeVariable.BORDER);
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
        waypointDetailsWidget = new WaypointDetailsWidget(
                0,
                0,
                MIN_DETAILS_PART_WIDTH,
                200,
                this.font
        );
        waypointListWidget = new WaypointListWidget(
                0,
                0,
                MIN_MIDDLE_PART_WIDTH - WAYPOINT_LIST_HORIZONTAL_PADDING,
                200,
                this,
                new WaypointQueryEngine(getWaypointQuerySource()),
                this.font,
                waypointDetailsWidget::setSelection
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
                Component.translatable("waypoint.dimension.show_selected"),
                Component.translatable("waypoint.dimension.show_all"),
                WidgetTextures.CUBE_ICON,
                WidgetTextures.STACKS_ICON,
                this::setShowAllDimensions
        );
        searchField = new WaypointSearchBarWidget(
                0,
                0,
                MIN_MIDDLE_PART_WIDTH,
                Component.translatable("waypoint.search.entry"),
                this.font,
                query -> {
                    waypointListWidget.setSearchQuery(query);
                    refreshRemoteOptions();
                }
        );
        searchField.setHint(Component.translatable("waypoint.search.hint"));
        groupModeToggle = new IconToggleButton(
                Component.translatable("waypoint.group.flat"),
                Component.translatable("waypoint.group.lists"),
                WidgetTextures.FLAT_LIST_MODE_ICON,
                WidgetTextures.GROUPED_LIST_MODE_ICON,
                this::setGroupMode
        );
        sortOrderToggle = new IconToggleButton(
                Component.translatable("waypoint.sort.ascending"),
                Component.translatable("waypoint.sort.descending"),
                WidgetTextures.SORT_ASCENDING_ICON,
                WidgetTextures.SORT_DESCENDING_ICON,
                this::setSortReversed
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
        this.distanceSortItem = sortingModeDropdown.addIconItem(
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
                MIN_MIDDLE_PART_WIDTH,
                MAX_CONTENT_HEIGHT,
                LayoutFlow.Orientation.VERTICAL
        );
        this.middleLayout.addChild(searchField, LayoutFlow.Direction.FORWARD);
        this.middleLayout.addChild(SpacerElement.height(SEARCH_GAP), LayoutFlow.Direction.FORWARD);
        this.middleLayout.addChild(waypointListWidget, LayoutFlow.Direction.FORWARD);
    }

    public WaypointManagerScreen(WaypointClientMod waypointClientMod) {
        this(waypointClientMod, null);
    }

    /**
     * Resets dimension-rail navigation state after dimension travel while retaining all-dimensions
     * waypoint-list scroll and dimension-node expansion choices for the current server or
     * local-world session.
     */
    public static void resetWidgetStates() {
        if (dimensionListWidget != null) dimensionListWidget.resetSelection();
    }

    /**
     * Resets all session-scoped manager state when joining a server or opening a local world.
     */
    public static void resetSessionWidgetStates() {
        resetWidgetStates();
        WaypointListWidget.resetSessionStates();
    }

    /**
     * Performs a full refresh after the available dimensions or the active player dimension may
     * have changed. The dimension rail is rebuilt first and its selection is preserved when
     * possible. The waypoint list is then refreshed exactly once; changing the selected dimension
     * already performs that refresh, so a second query is skipped in that case.
     */
    public static void updateAllWidgets() {
        if (!canUpdateWidgets()) {
            return;
        }
        if (!updateDimensionWidgetSelection()) {
            waypointListWidget.refreshView();
        }
    }

    /**
     * Refreshes the manager after an operation creates or removes an available dimension. The
     * dimension rail is always rebuilt while the screen is active. The waypoint list is refreshed
     * only when selection fallback changes its dimension, the changed dimension is selected, or
     * all-dimensions mode makes the changed dimension part of the current view.
     *
     * @param changedDimension the dimension added to or removed from the available-dimension set
     */
    public static void updateWidgetsForDimensionListChange(String changedDimension) {
        if (!canUpdateWidgets() || updateDimensionWidgetSelection()) {
            return;
        }
        refreshWaypointWidgetIfAffected(changedDimension);
    }

    /**
     * Refreshes waypoint content after a mutation inside an existing dimension. This path never
     * rebuilds or sorts the dimension rail. In selected-dimension mode, mutations in other
     * dimensions are ignored; in all-dimensions mode, every dimension is relevant.
     *
     * @param changedDimension the dimension whose waypoint content changed
     */
    public static void updateWaypointWidget(String changedDimension) {
        if (!canUpdateWidgets()) {
            return;
        }
        refreshWaypointWidgetIfAffected(changedDimension);
    }

    /**
     * Requeries waypoint rows only when the changed dimension participates in the active view.
     *
     * @param changedDimension the dimension whose waypoint content changed
     */
    private static void refreshWaypointWidgetIfAffected(String changedDimension) {
        if (shouldRefreshDimension(
                waypointListWidget.isShowingAllDimensions(),
                changedDimension,
                dimensionListWidget.getSelectedDimensionName()
        )) {
            waypointListWidget.refreshView();
        }
    }

    private static boolean canUpdateWidgets() {
        return isRendering && dimensionListWidget != null && waypointListWidget != null;
    }

    /**
     * Rebuilds the dimension rail and resolves its selection by name. Selection preference is the
     * previous dimension, then the player's current dimension, then the first available dimension.
     *
     * @return {@code true} when changing the selection already refreshed the waypoint list;
     *         {@code false} when the caller still needs to decide whether waypoint rows changed
     */
    private static boolean updateDimensionWidgetSelection() {
        if (activeScreen != null && activeScreen.showingRemote) return false;
        WaypointClientMod waypointClient = WaypointClientMod.getInstance();
        String previousSelection = dimensionListWidget.getSelectedDimensionName();
        List<String> dimensionNames = activeScreen == null
                ? waypointClient.getDimensionNames()
                : activeScreen.getDisplayedDimensionNames();
        String selectedDimension = resolveSelectedDimension(
                previousSelection,
                getCurrentDimensionName(),
                dimensionNames
        );

        dimensionListWidget.updateDimensionNames(dimensionNames);
        if (selectedDimension != null) {
            dimensionListWidget.setDimensionName(selectedDimension);
        }
        if (activeScreen != null) activeScreen.layoutSidebar();
        if (Objects.equals(previousSelection, selectedDimension)) {
            return false;
        }
        syncSelectedDimension(selectedDimension);
        return true;
    }

    /**
     * Resolves the previous selection, then the current dimension, then the first available dimension.
     *
     * @param selectedDimension the dimension selected before the catalog update
     * @param currentDimension the player's current dimension
     * @param dimensionNames the complete updated dimension catalog
     * @return the dimension to select, or {@code null} when the catalog is empty
     */
    static @Nullable String resolveSelectedDimension(
            String selectedDimension,
            String currentDimension,
            List<String> dimensionNames
    ) {
        if (dimensionNames.contains(selectedDimension)) {
            return selectedDimension;
        }
        if (dimensionNames.contains(currentDimension)) {
            return currentDimension;
        }
        return dimensionNames.isEmpty() ? null : dimensionNames.get(0);
    }

    private List<String> getDisplayedDimensionNames() {
        return mergeWithCachedDimensions(this.availableDimensionNames);
    }

    private void requestAvailableDimensionNames() {
        if (this.requestedAvailableDimensions) {
            return;
        }
        this.requestedAvailableDimensions = true;
        long requestSession = this.remotePanel.session();
        ClientDimensionCatalog.getAvailableDimensionNames().thenAccept(dimensionNames -> {
            // A reply to an earlier session lists the previous server's dimensions.
            if (this.remotePanel.session() != requestSession) {
                return;
            }
            // Keep the reply while a child screen is open; the next init() reads it.
            this.availableDimensionNames = dimensionNames;
            if (activeScreen != this) {
                return;
            }
            updateDimensionWidgetSelection();
            layoutSidebar();
        });
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
        searchField.setVisualWidth(this.layoutGeometry.middlePartWidth());
        waypointListWidget.setVisualWidth(this.layoutGeometry.middlePartWidth());
        waypointListWidget.setVisualHeight(Math.max(MIN_WAYPOINT_LIST_HEIGHT, waypointListHeight));
        this.middleLayout.setDimensions(
                this.layoutGeometry.middlePartWidth(),
                this.layoutGeometry.contentHeight()
        );

        waypointDetailsWidget.visible = true;
        waypointDetailsWidget.active = true;
        waypointDetailsWidget.setDimensions(
                this.layoutGeometry.detailsContentWidth(),
                this.layoutGeometry.contentHeight()
        );
        waypointDetailsWidget.setX(this.layoutGeometry.detailsContentX());
        waypointDetailsWidget.setY(this.layoutGeometry.contentY());

        layoutSidebar();
        updatePanelVisibility();
    }

    private void layoutSidebar() {
        List<ShiftableClickableWidget> controls = visibleSidebarControls();
        SidebarLayout sidebar = calculateSidebarLayout(
                layoutGeometry.contentY(),
                layoutGeometry.contentHeight(),
                controls.size(),
                dimensionListWidget.preferredHeight(),
                serverListWidget.preferredHeight(),
                showingRemote
        );
        for (int index = 0; index < controls.size(); index++) {
            controls.get(index).setPosition(layoutGeometry.controlX(), sidebar.controlY(index));
        }
        setControlVisibility(sidebar.controlsVisible());
        dimensionListWidget.visible = dimensionListWidget.active = sidebar.dimensionVisible();
        dimensionListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, sidebar.dimensionHeight()));
        dimensionListWidget.setPosition(layoutGeometry.leftX(), sidebar.dimensionY());
        serverListWidget.visible = serverListWidget.active = sidebar.serverVisible();
        serverListWidget.setVisualHeight(Math.max(MIN_DIMENSION_LIST_HEIGHT, sidebar.serverHeight()));
        serverListWidget.setPosition(layoutGeometry.leftX(), sidebar.serverY());
        selectorSeparator.setPosition(layoutGeometry.leftX(), sidebar.railSeparatorY());
        selectorSeparator.setVisible(sidebar.dimensionVisible() && sidebar.serverVisible());
        serverControlSeparator.setPosition(layoutGeometry.leftX(), sidebar.controlSeparatorY());
        serverControlSeparator.setVisible(sidebar.serverVisible() && serverScopeToggle.visible);
    }

    /** Sidebar controls in top-to-bottom order; hidden controls are left out and release their slots. */
    private List<ShiftableClickableWidget> visibleSidebarControls() {
        List<ShiftableClickableWidget> controls = new ArrayList<>(6);
        if (isScopeToggleAvailable()) {
            controls.add(serverScopeToggle);
        }
        controls.add(allDimensionsToggle);
        controls.add(groupModeToggle);
        controls.add(sortOrderToggle);
        controls.add(sortingModeDropdown);
        if (!showingRemote) {
            controls.add(addWaypointButton);
        }
        return controls;
    }

    /** The local/remote toggle appears once remote servers are cached, and stays while viewing them. */
    private boolean isScopeToggleAvailable() {
        return hasRemoteServers || showingRemote;
    }

    private void updateRemoteServerAvailability() {
        boolean available = !this.remotePanel.servers().isEmpty();
        if (available == this.hasRemoteServers) {
            return;
        }
        this.hasRemoteServers = available;
        layoutSidebar();
    }

    private void refreshRemoteSelectors(boolean force) {
        var servers = remotePanel.servers();
        if (!force && servers.equals(remoteServers)) return;
        remoteServers = servers;
        serverListWidget.setServers(servers);
        var view = serverListWidget.getSelectedEntry() == null ? null : servers.get(serverListWidget.getSelectedEntry());
        dimensionListWidget.updateDimensionNames(RemoteBrowserModel.dimensionNames(view));
        refreshRemoteScope();
        layoutSidebar();
    }

    private void refreshRemoteScope() {
        remotePanel.setScope(serverListWidget.getSelectedEntry(),
                waypointListWidget.isShowingAllDimensions() ? null : dimensionListWidget.getSelectedEntry());
    }

    @Override
    int getContentWidth() {
        return calculateLayoutGeometry(this.width, this.height).completeWidth();
    }

    @Override
    int getContentHeight() {
        return calculateLayoutGeometry(this.width, this.height).panelHeight();
    }

    @Override
    protected void init() {
        super.init();
        this.builtState = resolveViewState(WaypointServerMod.runsWithClient(), getNetworkState());
        if (this.builtState != ManagerViewState.READY) {
            // rebuildWidgets() doesn't call removed(), so a ready screen that rebuilds as loading
            // must stop receiving refreshes here.
            this.stopReceivingRefreshes();
            this.resetToLocalView();
            this.layoutStatusMessage();
            return;
        }
        isRendering = true;
        activeScreen = this;
        if (this.remotePanel.bindSession()) {
            // A new catalog session belongs to another connection; request its dimensions again.
            this.requestedAvailableDimensions = false;
            this.availableDimensionNames = List.of();
        }
        String currentDimension = getCurrentDimensionName();
        if (WaypointServerMod.runsWithClient()) {
            WaypointServerMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        } else {
            WaypointClientMod.getInstance().getOrCreateWaypointFileManager(currentDimension);
        }
        this.hasRemoteServers = !this.remotePanel.servers().isEmpty();
        updateWidgetDimension();
        this.middleLayout.setPosition(
                this.layoutGeometry.middleX(),
                this.layoutGeometry.contentY()
        );


        if (!showingRemote) dimensionListWidget.updateDimensionNames(this.getDisplayedDimensionNames());
        if (showingRemote) {
            refreshRemoteSelectors(true);
        } else if (hasInitialized) {
            syncSelectedDimension(getSelectedDimension());
        } else {
            dimensionListWidget.setDimensionName(getCurrentDimensionName());
            syncSelectedDimension(getCurrentDimensionName());
            hasInitialized = true;
        }

        this.addRenderableWidget(dimensionListWidget);
        this.addRenderableWidget(serverScopeToggle);
        this.addRenderableWidget(allDimensionsToggle);
        this.addRenderableWidget(groupModeToggle);
        this.addRenderableWidget(sortOrderToggle);
        this.addRenderableWidget(sortingModeDropdown);
        this.addRenderableWidget(addWaypointButton);
        this.middleLayout.visitWidgets(this::addRenderableWidget);
        this.addRenderableWidget(serverListWidget);
        this.addRenderableWidget(this.waypointDetailsWidget);
        remotePanel.register(this::addRenderableWidget);
        updatePanelVisibility();
        layoutSidebar();
        this.requestAvailableDimensionNames();
    }

    /** Ends this manager's static refresh registration (see {@link #canUpdateWidgets()}). */
    private void stopReceivingRefreshes() {
        isRendering = false;
        if (activeScreen == this) {
            activeScreen = null;
        }
    }

    private void layoutStatusMessage() {
        Component message = Component.translatable(Objects.requireNonNull(this.builtState.messageKey()));
        int availableWidth = Math.max(1, this.width - SCREEN_MARGIN * 2);
        this.statusMessage.setText(message);
        this.statusMessage.setMaxWidth(Math.min(this.font.width(message), availableWidth));
        this.statusMessage.setPosition(
                centered(this.width, this.statusMessage.getWidth()),
                centered(this.height, this.statusMessage.getHeight())
        );
    }

    /** Leaving the ready state drops the remote view, so the next ready build starts local. */
    private void resetToLocalView() {
        if (!this.showingRemote) {
            return;
        }
        this.showingRemote = false;
        this.serverScopeToggle.setState(false);
        this.distanceSortItem.visible = this.distanceSortItem.active = true;
        List<String> localDimensions = this.getDisplayedDimensionNames();
        dimensionListWidget.updateDimensionNames(localDimensions);
        dimensionListWidget.setDimensionName(resolveSelectedDimension(
                this.localDimension,
                getCurrentDimensionName(),
                localDimensions
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.builtState == ManagerViewState.READY && this.showingRemote && !this.remotePanel.tick()) {
            // Step 18: a catalog session change in the remote view closed the manager.
            return;
        }
        ManagerViewState state = resolveViewState(WaypointServerMod.runsWithClient(), getNetworkState());
        boolean staleLocalSession = state == ManagerViewState.READY
                && !this.showingRemote
                && !this.remotePanel.isSessionCurrent();
        if (state != this.builtState || staleLocalSession) {
            this.rebuildWidgets();
            return;
        }
        if (state != ManagerViewState.READY) {
            return;
        }
        if (this.showingRemote) {
            refreshRemoteSelectors(false);
        } else {
            waypointListWidget.refreshDistanceSortIfPlayerMoved();
        }
        updateRemoteServerAvailability();
    }

    @Override
    public void removed() {
        super.removed();
        // Child screens and every close path end this manager's static refresh registration.
        this.stopReceivingRefreshes();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        GuiEventListener focused = this.getFocused();
        boolean notTyping = canUseShortcuts(focused);
        this.acceptMovementKeys(notTyping);
        if (notTyping && keyCode == InputConstants.KEY_C) {
            closeOpenDropdownMenus();
            MinecraftClientHelper.setScreen(this.minecraft, new ClientConfigScreen(this));
            return true;
        }
        return notTyping
                && this.builtState == ManagerViewState.READY
                && !showingRemote
                && waypointListWidget.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    static boolean canUseShortcuts(@Nullable GuiEventListener focused) {
        return !(focused instanceof EditBox) && !(focused instanceof ComboBoxWidget);
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
        if (showingRemote) refreshRemoteScope();
        else syncSelectedDimension(dimensionName);
    }

    private void setShowAllDimensions(boolean showAllDimensions) {
        waypointListWidget.setShowAllDimensions(showAllDimensions);
        if (showingRemote) refreshRemoteScope();
        dimensionListWidget.active = resolveDimensionListActive(dimensionListWidget.visible);
        persistManagerState();
    }

    static boolean resolveDimensionListActive(boolean dimensionListVisible) {
        return dimensionListVisible;
    }

    /**
     * Determines whether a mutation in one dimension can affect the currently displayed rows.
     *
     * @param showAllDimensions whether rows from every dimension are displayed
     * @param changedDimension the dimension whose waypoint content changed
     * @param selectedDimension the dimension selected in the dimension rail
     * @return {@code true} when the waypoint list must be requeried
     */
    static boolean shouldRefreshDimension(
            boolean showAllDimensions,
            String changedDimension,
            String selectedDimension
    ) {
        return showAllDimensions
                || Objects.equals(changedDimension, selectedDimension);
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
        if (this.builtState != ManagerViewState.READY) {
            this.statusMessage.
            //$ render_method_swap
            extractRenderState
                    (context, mouseX, mouseY, delta);
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
                this.layoutGeometry.detailsPanelX(),
                this.layoutGeometry.panelY(),
                this.layoutGeometry.detailsPanelWidth(),
                this.layoutGeometry.panelHeight()
        );
        waypointDetailsWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        if (showingRemote) remotePanel.render(context, mouseX, mouseY, delta);
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
        serverListWidget.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        selectorSeparator.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        serverControlSeparator.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, delta);
        serverScopeToggle.
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
        sortOrderToggle.
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
        if (activeScreen == this) {
            activeScreen = null;
        }
        waypointListWidget = null;
        dimensionListWidget = null;
        if (parentScreen == null) super.onClose();
        else MinecraftClientHelper.setScreen(this.parentScreen);
    }

    private void setShowingRemote(boolean remote) {
        closeOpenDropdownMenus();
        if (remote && !showingRemote) localDimension = dimensionListWidget.getSelectedDimensionName();
        showingRemote = remote;
        if (remote) refreshRemoteSelectors(true);
        else {
            dimensionListWidget.updateDimensionNames(getDisplayedDimensionNames());
            dimensionListWidget.setDimensionName(resolveSelectedDimension(localDimension, getCurrentDimensionName(), getDisplayedDimensionNames()));
            syncSelectedDimension(dimensionListWidget.getSelectedDimensionName());
        }
        layoutSidebar();
        setFocused(null);
        // Distance has no shared origin across servers.
        if (remote && waypointListWidget.getSortMode() == WaypointSorting.SortMode.DISTANCE) {
            setSortMode(WaypointSorting.SortMode.NAME);
        }
        this.distanceSortItem.visible = this.distanceSortItem.active = !remote;
        sortingModeDropdown.setPopupXOffset(this.layoutGeometry.dropdownXOffset(
                sortingModeDropdown.getPopupItemCount()
        ));
        updatePanelVisibility();
        refreshRemoteOptions();
    }

    private void refreshRemoteOptions() {
        if (searchField != null && waypointListWidget != null) {
            remotePanel.setOptions(searchField.getValue(), waypointListWidget.isGroupByLists(),
                    waypointListWidget.getSortMode(), waypointListWidget.isSortReversed());
        }
    }

    private void updatePanelVisibility() {
        boolean visible = layoutGeometry.waypointListHeight(searchField.getVisualHeight()) >= MIN_WAYPOINT_LIST_HEIGHT;
        waypointListWidget.visible = waypointListWidget.active = visible && !showingRemote;
        waypointDetailsWidget.visible = waypointDetailsWidget.active = !showingRemote;
        dimensionListWidget.active = dimensionListWidget.visible;
        allDimensionsToggle.active = allDimensionsToggle.visible;
        addWaypointButton.active = addWaypointButton.visible && !showingRemote;
        remotePanel.layout(layoutGeometry.middleX(), layoutGeometry.contentY() + searchField.getVisualHeight() + SEARCH_GAP,
                layoutGeometry.middlePartWidth(), Math.max(1, layoutGeometry.waypointListHeight(searchField.getVisualHeight())),
                layoutGeometry.detailsContentX(), layoutGeometry.contentY(),
                layoutGeometry.detailsContentWidth(), layoutGeometry.contentHeight());
        remotePanel.setVisible(showingRemote && visible);
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
        sortOrderToggle.setState(reversed);
        sortOrderToggle.active = sortOrderToggle.visible
                && activeMode != WaypointSorting.SortMode.DEFAULT;

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
        refreshRemoteOptions();
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

    private void setSortReversed(boolean reversed) {
        WaypointSorting.SortMode activeMode = waypointListWidget.getSortMode();
        if (activeMode != WaypointSorting.SortMode.DEFAULT
                && waypointListWidget.isSortReversed() != reversed) {
            waypointListWidget.toggleSortMode(activeMode);
        }
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
        addWaypointButton.visible = visible && !showingRemote;
        addWaypointButton.active = visible && !showingRemote;
        boolean scopeVisible = visible && isScopeToggleAvailable();
        if (!scopeVisible && getFocused() == serverScopeToggle) {
            setFocused(null);
        }
        serverScopeToggle.visible = serverScopeToggle.active = scopeVisible;
        groupModeToggle.visible = visible;
        groupModeToggle.active = visible;
        sortOrderToggle.visible = visible;
        sortOrderToggle.active = visible
                && waypointListWidget.getSortMode() != WaypointSorting.SortMode.DEFAULT;
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

    /** What the manager shows, chosen from the client connection state. */
    enum ManagerViewState {
        LOADING("server_waypoint.manager.loading"),
        UNSUPPORTED("server_waypoint.no_serverside_support"),
        INCOMPATIBLE("server_waypoint.incompatible_protocol_version"),
        READY(null);

        private final @Nullable String messageKey;

        ManagerViewState(@Nullable String messageKey) {
            this.messageKey = messageKey;
        }

        /** The centered message of a non-ready state, or {@code null} for the full manager. */
        @Nullable String messageKey() {
            return this.messageKey;
        }
    }

    /**
     * Chooses the manager content. An integrated server shares the server's waypoint model and is
     * always ready; a dedicated server is ready once waypoint synchronization finishes.
     */
    static ManagerViewState resolveViewState(
            boolean integratedServer,
            WaypointClientMod.ClientNetworkState networkState
    ) {
        if (integratedServer) {
            return ManagerViewState.READY;
        }
        return switch (networkState) {
            case SYNC_FINISHED -> ManagerViewState.READY;
            case NOT_READY, HANDSHAKE_FINISHED -> ManagerViewState.LOADING;
            case NO_SERVERSIDE_SUPPORT -> ManagerViewState.UNSUPPORTED;
            case INCOMPATIBLE_PROTOCOL -> ManagerViewState.INCOMPATIBLE;
        };
    }

    /**
     * Lays out the sidebar. Visible controls stack upward from the content bottom, so a hidden
     * control releases its slot. The dimension rail grows down from the top; in the remote view the
     * server rail grows up from above the controls, and {@link OpposedExpansionLayout} divides the
     * space. A rail below its one-icon minimum is hidden (zero height).
     */
    static SidebarLayout calculateSidebarLayout(
            int contentY,
            int contentHeight,
            int visibleControlCount,
            int dimensionPreferredHeight,
            int serverPreferredHeight,
            boolean showingRemote
    ) {
        int controlsHeight = visibleControlCount <= 0
                ? 0
                : visibleControlCount * CONTROL_BUTTON_SIZE + (visibleControlCount - 1) * CONTROL_GAP;
        int controlsY = contentY + contentHeight - controlsHeight;
        int available = Math.max(0, controlsY - SECTION_GAP - contentY);
        int dimensionHeight = Math.min(available, dimensionPreferredHeight);
        int serverHeight = 0;
        if (showingRemote) {
            OpposedExpansionLayout.Sizes sizes = OpposedExpansionLayout.allocate(
                    available,
                    MIN_DIMENSION_LIST_HEIGHT,
                    dimensionPreferredHeight,
                    serverPreferredHeight,
                    SECTION_GAP
            );
            dimensionHeight = sizes.top();
            serverHeight = sizes.bottom();
        }
        if (dimensionHeight < MIN_DIMENSION_LIST_HEIGHT) {
            dimensionHeight = 0;
        }
        if (serverHeight < MIN_DIMENSION_LIST_HEIGHT) {
            serverHeight = 0;
        }
        return new SidebarLayout(
                contentHeight >= controlsHeight,
                controlsY,
                contentY,
                dimensionHeight,
                controlsY - SECTION_GAP - serverHeight,
                serverHeight
        );
    }

    record SidebarLayout(
            boolean controlsVisible,
            int controlsY,
            int dimensionY,
            int dimensionHeight,
            int serverY,
            int serverHeight
    ) {
        boolean dimensionVisible() {
            return this.dimensionHeight > 0;
        }

        boolean serverVisible() {
            return this.serverHeight > 0;
        }

        int controlY(int index) {
            return this.controlsY + index * (CONTROL_BUTTON_SIZE + CONTROL_GAP);
        }

        int railSeparatorY() {
            return (this.dimensionY + this.dimensionHeight + this.serverY) / 2;
        }

        int controlSeparatorY() {
            return (this.serverY + this.serverHeight + this.controlsY) / 2;
        }
    }

    static ManagerLayoutGeometry calculateLayoutGeometry(int screenWidth, int screenHeight) {
        int availableContentHeight = Math.max(
                0,
                screenHeight - (SCREEN_MARGIN + PANEL_PADDING) * 2
        );
        int preferredContentHeight = clamp(
                Math.round(screenHeight * RELATIVE_HEIGHT),
                MIN_CONTENT_HEIGHT,
                MAX_CONTENT_HEIGHT
        );
        int contentHeight = Math.min(availableContentHeight, preferredContentHeight);
        int leftPanelWidth = LEFT_PART_WIDTH + PANEL_PADDING * 2;
        int fixedHorizontalWidth = leftPanelWidth + PANEL_GAP * 2 + PANEL_PADDING * 4;
        int desiredMiddlePartWidth = clamp(
                Math.round(screenWidth * MIDDLE_PART_WIDTH_RATIO),
                MIN_MIDDLE_PART_WIDTH,
                MAX_MIDDLE_PART_WIDTH
        );
        int desiredDetailsPartWidth = clamp(
                Math.round(screenWidth * DETAILS_PART_WIDTH_RATIO),
                MIN_DETAILS_PART_WIDTH,
                MAX_DETAILS_PART_WIDTH
        );
        int desiredFlexibleWidth = desiredMiddlePartWidth + desiredDetailsPartWidth;
        int availableCompleteWidth = screenWidth <= 0
                ? fixedHorizontalWidth + desiredFlexibleWidth
                : Math.max(0, screenWidth - SCREEN_MARGIN * 2);
        int availableFlexibleWidth = Math.max(
                2,
                availableCompleteWidth - fixedHorizontalWidth
        );
        int middlePartWidth = desiredMiddlePartWidth;
        int detailsPartWidth = desiredDetailsPartWidth;
        if (desiredFlexibleWidth > availableFlexibleWidth) {
            middlePartWidth = Math.max(
                    1,
                    Math.round((float)availableFlexibleWidth
                            * desiredMiddlePartWidth / desiredFlexibleWidth)
            );
            detailsPartWidth = Math.max(1, availableFlexibleWidth - middlePartWidth);
        }
        int middlePanelWidth = middlePartWidth + PANEL_PADDING * 2;
        int detailsPanelWidth = detailsPartWidth + PANEL_PADDING * 2;
        int completeWidth = leftPanelWidth
                + PANEL_GAP
                + middlePanelWidth
                + PANEL_GAP
                + detailsPanelWidth;
        int leftPanelX = centered(screenWidth, completeWidth);
        int leftX = leftPanelX + PANEL_PADDING;
        int middleX = leftPanelX + leftPanelWidth + PANEL_GAP + PANEL_PADDING;
        int detailsPanelX = leftPanelX + leftPanelWidth + PANEL_GAP
                + middlePanelWidth + PANEL_GAP;
        return new ManagerLayoutGeometry(
                contentHeight,
                middleX,
                leftX,
                centered(screenHeight, contentHeight),
                middlePartWidth,
                detailsPanelX,
                detailsPanelWidth
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    record ManagerLayoutGeometry(
            int contentHeight,
            int middleX,
            int leftX,
            int contentY,
            int middlePartWidth,
            int detailsPanelX,
            int detailsPanelWidth
    ) {
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
            return this.middlePartWidth + PANEL_PADDING * 2;
        }

        int leftPanelX() {
            return this.leftX - PANEL_PADDING;
        }

        int leftPanelWidth() {
            return LEFT_PART_WIDTH + PANEL_PADDING * 2;
        }

        int detailsContentX() {
            return this.detailsPanelX + PANEL_PADDING;
        }

        int detailsContentWidth() {
            return Math.max(0, this.detailsPanelWidth - PANEL_PADDING * 2);
        }

        int completeWidth() {
            return this.detailsPanelX + this.detailsPanelWidth - this.leftPanelX();
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

        private IconMenuItem addIconItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            IconMenuItem menuItem = this.addMenuItem(new IconMenuItem(message, icon, callback));
            this.iconItems.add(menuItem);
            return menuItem;
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

    private static final class IconToggleButton extends ShiftableButtonWidget {
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
        protected void onPress() {
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
                    false,
                    true
            );
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }
    }

}
