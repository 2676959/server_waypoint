//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.WaypointClientMod.getCurrentDimensionName;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.BORDER;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.FOCUS_RING;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.PANEL_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.ROW_HOVER_BACKGROUND;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_MUTED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.TEXT_PRIMARY;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.util.ColorUtils.getSafeTextColor;
import static _959.server_waypoint.util.CommandGenerator.removeCmd;
import static _959.server_waypoint.util.CommandGenerator.removeListCmd;
import static _959.server_waypoint.util.CommandGenerator.tpCmd;

public class WaypointListWidget extends TreeViewWidget<WaypointListWidget.RowNode> {
    public static int TELEPORT_KEY = 84;
    public static final Component EMPTY_INFO_TEXT = Component.translatable("waypoint.empty_mark");
    private static final int listIconSize = 16;
    private static final int buttonIconSize = 12;
    private static final int itemHeight = 20;
    private static final int treeIndent = 10;
    private static double SCROLLED_POSITION = 0.0D;
    private final WaypointManagerScreen parentScreen;
    private final WaypointQueryEngine queryEngine;
    private final Font textRenderer;
    private final int textVertOffset;
    private final int listIconVertOffset;
    private final int buttonIconVertOffset;
    private final int buttonIconHrzOffset;
    private final int btnWidth = 19;
    private int thirdBtnXPos = width - btnWidth;
    private int secondBtnXPos = thirdBtnXPos - btnWidth;
    private int firstBtnXPos = secondBtnXPos - btnWidth;
    private int removeClickedPos = -1;
    private String selectedDimensionName = "";
    private String searchQuery = "";
    private WaypointSorting.SortMode sortMode = WaypointSorting.SortMode.DEFAULT;
    private boolean sortReversed = false;
    private boolean groupByLists = true;
    private boolean showAllDimensions = false;
    private final Map<String, Boolean> dimensionExpansionStates = new HashMap<>();

    public WaypointListWidget(int x, int y, int width, int height, WaypointManagerScreen parent, WaypointQueryEngine queryEngine, Font textRenderer) {
        super(x, y, width, height, itemHeight, Component.literal("Waypoint lists"), 4, 4, 4, 4,
                PANEL_BACKGROUND, BORDER, true);
        this.parentScreen = parent;
        this.queryEngine = queryEngine;
        this.textRenderer = textRenderer;
        setScrollY(SCROLLED_POSITION);
        textVertOffset = centered(itemHeight, textRenderer.lineHeight) + 1;
        listIconVertOffset = centered(itemHeight, listIconSize);
        buttonIconVertOffset = centered(itemHeight, buttonIconSize);
        buttonIconHrzOffset = centered(btnWidth, buttonIconSize);
    }

    public static void resetScroll() {
        SCROLLED_POSITION = 0.0D;
    }

    /**
     * updates the displayed dimension, if dimensionName is empty only clears the current list
     */
    public void setSelectedDimension(String dimensionName) {
        this.selectedDimensionName = dimensionName == null ? "" : dimensionName;
        refreshView();
    }

    /**
     * only recalculate rendering related data, do not change the content
     */
    public void reCalculateRenderData() {
        refreshView();
    }

    public void refreshView() {
        applySearchAndSort();
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery;
        applySearchAndSort();
    }

    public void setSortMode(WaypointSorting.SortMode sortMode) {
        this.sortMode = sortMode == null ? WaypointSorting.SortMode.DEFAULT : sortMode;
        this.sortReversed = false;
        if (this.sortMode == WaypointSorting.SortMode.DEFAULT) {
            this.groupByLists = true;
        }
        applySearchAndSort();
    }

    public WaypointSorting.SortMode getSortMode() {
        return this.sortMode;
    }

    public boolean isSortReversed() {
        return this.sortReversed;
    }

    public void toggleGroupByLists() {
        setGroupByLists(!isGroupByLists());
    }

    public void setGroupByLists(boolean groupByLists) {
        this.groupByLists = this.sortMode == WaypointSorting.SortMode.DEFAULT || groupByLists;
        applySearchAndSort();
    }

    public boolean isGroupByLists() {
        return this.sortMode == WaypointSorting.SortMode.DEFAULT || this.groupByLists;
    }

    public void setShowAllDimensions(boolean showAllDimensions) {
        this.showAllDimensions = showAllDimensions;
        applySearchAndSort();
    }

    public boolean isShowingAllDimensions() {
        return this.showAllDimensions;
    }

    public void sortByName() {
        this.toggleSortMode(WaypointSorting.SortMode.NAME);
    }

    public void sortByDistance() {
        this.toggleSortMode(WaypointSorting.SortMode.DISTANCE);
    }

    public void sortByColor() {
        this.toggleSortMode(WaypointSorting.SortMode.COLOR);
    }

    public List<String> getSearchSuggestions() {
        return this.queryEngine.getSearchSuggestions(this.selectedDimensionName);
    }

    private void applySearchAndSort() {
        WaypointQueryEngine.Query query = new WaypointQueryEngine.Query(
                this.searchQuery,
                this.sortMode,
                getPlayerWaypointPos(),
                this.sortReversed
        );
        WaypointQueryEngine.QueryResult result = this.showAllDimensions
                ? this.queryEngine.queryAll(query)
                : this.queryEngine.queryDimension(this.selectedDimensionName, query);
        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, isGroupByLists());
        if (display.groupByLists()) {
            if (this.showAllDimensions) {
                updateRoots(display.dimensions().stream()
                        .map(this::createDimensionNode)
                        .map(RowNode.class::cast)
                        .toList());
                return;
            }
            updateRoots(display.lists().stream()
                    .map(this::createListNode)
                    .map(RowNode.class::cast)
                    .toList());
            return;
        }
        updateRoots(display.flatWaypoints().stream()
                .map(waypoint -> new WaypointNode(
                        waypoint.dimensionName(),
                        waypoint.sourceList(),
                        waypoint.waypoint(),
                        true,
                        this.showAllDimensions
                ))
                .map(RowNode.class::cast)
                .toList());
    }

    private DimensionNode createDimensionNode(WaypointListDisplayModel.DisplayDimension dimension) {
        return new DimensionNode(
                dimension.dimensionName(),
                dimension.lists().stream()
                        .map(this::createListNode)
                        .toList()
        );
    }

    private ListNode createListNode(WaypointListDisplayModel.DisplayList list) {
        return new ListNode(
                list.dimensionName(),
                new ViewWaypointList(list.sourceList(), list.waypoints())
        );
    }

    public void toggleSortMode(WaypointSorting.SortMode sortMode) {
        WaypointSorting.SortMode resolvedSortMode = sortMode == null ? WaypointSorting.SortMode.DEFAULT : sortMode;
        if (resolvedSortMode == WaypointSorting.SortMode.DEFAULT) {
            setSortMode(resolvedSortMode);
            return;
        }
        if (this.sortMode == resolvedSortMode) {
            this.sortReversed = !this.sortReversed;
        } else {
            this.sortMode = resolvedSortMode;
            this.sortReversed = false;
        }
        applySearchAndSort();
    }

    private static WaypointPos getPlayerWaypointPos() {
        BlockPos playerPos = getPlayerBlockPos();
        if (playerPos == null) {
            return null;
        }
        return new WaypointPos(playerPos.getX(), playerPos.getY(), playerPos.getZ());
    }

    private static BlockPos getPlayerBlockPos() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getCameraEntity() == null) {
            return null;
        }
        return minecraft.getCameraEntity().blockPosition();
    }

    @Override
    protected @NotNull List<RowNode> getChildren(RowNode value) {
        if (value instanceof DimensionNode dimensionNode) {
            return dimensionNode.lists().stream()
                    .map(RowNode.class::cast)
                    .toList();
        }
        if (value instanceof ListNode listNode) {
            return listNode.waypointList().simpleWaypoints().stream()
                    .map(waypoint -> new WaypointNode(
                            listNode.dimensionName(),
                            listNode.waypointList(),
                            waypoint,
                            false,
                            false
                    ))
                    .map(RowNode.class::cast)
                    .toList();
        }
        return List.of();
    }

    @Override
    protected boolean isExpanded(RowNode value) {
        if (value instanceof DimensionNode dimensionNode) {
            return this.dimensionExpansionStates.getOrDefault(dimensionNode.dimensionName(), true);
        }
        return value instanceof ListNode listNode && listNode.waypointList().isExpand();
    }

    @Override
    protected void setExpanded(RowNode value, boolean expanded) {
        if (value instanceof DimensionNode dimensionNode) {
            this.dimensionExpansionStates.put(dimensionNode.dimensionName(), expanded);
        } else if (value instanceof ListNode listNode) {
            listNode.waypointList().setExpand(expanded);
        }
    }

    @Override
    protected boolean onEntryClicked(TreeEntry<RowNode> entry, double contentMouseX, double contentMouseY, int button) {
        if (button != 0) {
            return false;
        }
        RowNode value = entry.value();
        if (value instanceof ListNode listNode) {
            return onListClicked(
                    entry.row(),
                    listNode.dimensionName(),
                    listNode.waypointList(),
                    contentMouseX
            );
        }
        if (value instanceof WaypointNode waypointNode) {
            return onWaypointClicked(
                    entry.row(),
                    waypointNode.dimensionName(),
                    waypointNode.waypointList(),
                    waypointNode.waypoint(),
                    contentMouseX
            );
        }
        return false;
    }

    private boolean onListClicked(
            int row,
            String dimensionName,
            WaypointList waypointList,
            double contentMouseX
    ) {
        if (waypointList.isEmpty()) {
            if (contentMouseX > thirdBtnXPos) {
                if (removeClickedPos == row) {
                    if (sendCommand(removeListCmd(dimensionName, waypointList.name(), false))) {
                        this.removeClickedPos = -1;
                        return true;
                    }
                }
                removeClickedPos = row;
                return true;
            } else if (contentMouseX > secondBtnXPos) {
                MinecraftClientHelper.setScreen(new WaypointAddScreen(
                        this.parentScreen,
                        dimensionName,
                        waypointList.name()
                ));
                this.removeClickedPos = -1;
                return true;
            }
        } else {
            if (contentMouseX > thirdBtnXPos) {
                MinecraftClientHelper.setScreen(new WaypointAddScreen(
                        this.parentScreen,
                        dimensionName,
                        waypointList.name()
                ));
                this.removeClickedPos = -1;
                return true;
            } else if (contentMouseX > secondBtnXPos) {
                if (canToggleVisibility(dimensionName)) {
                    waypointList.setShow(!waypointList.isShow());
                    List<SimpleWaypoint> list = waypointList.simpleWaypoints();
                    if (waypointList.isShow()) {
                        OptimizedWaypointRenderer.addList(list);
                    } else {
                        OptimizedWaypointRenderer.removeList(list);
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean onWaypointClicked(
            int row,
            String dimensionName,
            WaypointList waypointList,
            SimpleWaypoint waypoint,
            double contentMouseX
    ) {
        if (contentMouseX > thirdBtnXPos) {
            if (removeClickedPos == row) {
                if (sendCommand(removeCmd(dimensionName, waypointList.name(), waypoint, false))) {
                    this.removeClickedPos = -1;
                    return true;
                }
            }
            this.removeClickedPos = row;
            return true;
        } else if (contentMouseX > secondBtnXPos) {
            MinecraftClientHelper.setScreen(new WaypointEditScreen(
                    this.parentScreen,
                    dimensionName,
                    waypointList.name(),
                    waypoint
            ));
            return true;
        } else if (contentMouseX > firstBtnXPos) {
            if (canToggleVisibility(dimensionName)) {
                if (waypoint.isRendered()) {
                    OptimizedWaypointRenderer.remove(waypoint);
                } else {
                    OptimizedWaypointRenderer.add(waypoint);
                }
            }
            return true;
        }
        return false;
    }

    private boolean canToggleVisibility(String dimensionName) {
        return dimensionName.equals(getCurrentDimensionName());
    }

    @Override
    protected void onHoveredEntryChanged(TreeEntry<RowNode> oldEntry, TreeEntry<RowNode> newEntry) {
        if (newEntry == null || newEntry.row() != this.removeClickedPos) {
            this.removeClickedPos = -1;
        }
    }

    @Override
    protected void onScrollChanged(double scrollY) {
        SCROLLED_POSITION = scrollY;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ret = false;
        if (keyCode == TELEPORT_KEY) {
            TreeEntry<RowNode> hoveredEntry = getHoveredEntry();
            if (hoveredEntry != null && hoveredEntry.value() instanceof WaypointNode waypointNode) {
                sendCommand(tpCmd(
                        waypointNode.dimensionName(),
                        waypointNode.waypointList().name(),
                        waypointNode.waypoint().name(),
                        false
                ));
                ret = true;
            }
        }
        return ret || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        drawText(context, textRenderer, EMPTY_INFO_TEXT, 5, textVertOffset, getColor(TEXT_DISABLED), true);
    }

    @Override
    protected void beforeRenderEntries(GuiGraphicsExtractor context, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
        thirdBtnXPos = contentWidth - btnWidth;
        secondBtnXPos = thirdBtnXPos - btnWidth;
        firstBtnXPos = secondBtnXPos - btnWidth;
    }

    @Override
    protected void renderEntry(GuiGraphicsExtractor context, TreeEntry<RowNode> entry, boolean hovered, int rowY, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
        RowNode value = entry.value();
        int indent = this.showAllDimensions ? entry.depth() * treeIndent : 0;
        if (value instanceof DimensionNode dimensionNode) {
            renderDimension(context, dimensionNode, hovered, rowY, contentWidth);
        } else if (value instanceof ListNode listNode) {
            renderWaypointList(
                    context,
                    listNode.dimensionName(),
                    listNode.waypointList(),
                    hovered,
                    rowY,
                    contentWidth,
                    indent
            );
        } else if (value instanceof WaypointNode waypointNode) {
            renderWaypoint(context, waypointNode, hovered, rowY, contentWidth, indent);
        }
    }

    private void renderDimension(
            GuiGraphicsExtractor context,
            DimensionNode dimensionNode,
            boolean hovered,
            int rowY,
            int contentWidth
    ) {
        if (hovered) {
            context.fill(0, rowY, contentWidth, rowY + itemHeight, getColor(ROW_HOVER_BACKGROUND));
            renderOutline(context, 0, rowY, contentWidth, itemHeight, getColor(FOCUS_RING));
        }
        drawText(
                context,
                textRenderer,
                dimensionNode.dimensionName(),
                18,
                rowY + textVertOffset,
                getColor(TEXT_PRIMARY),
                true
        );
        texture(
                context,
                isExpanded(dimensionNode)
                        ? WidgetTextures.LIST_EXPAND_ICON
                        : WidgetTextures.LIST_COLLAPSE_ICON,
                0,
                rowY + listIconVertOffset,
                0,
                0,
                listIconSize,
                listIconSize,
                listIconSize,
                listIconSize
        );
    }

    private void renderWaypointList(
            GuiGraphicsExtractor context,
            String dimensionName,
            WaypointList waypointList,
            boolean hovered,
            int rowY,
            int contentWidth,
            int indent
    ) {
        boolean isListShow = waypointList.isShow();
        int textColor = getColor(isListShow ? TEXT_PRIMARY : TEXT_DISABLED);
        if (hovered) {
            context.fill(0, rowY, contentWidth, rowY + itemHeight, getColor(ROW_HOVER_BACKGROUND));
            renderOutline(context, 0, rowY, contentWidth, itemHeight, getColor(FOCUS_RING));
        }

        int centeredBtnY = rowY + buttonIconVertOffset;
        boolean isListEmpty = waypointList.isEmpty();
        if (hovered) {
            if (isListEmpty) {
                texture(context, WidgetTextures.ADD_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                if (removeClickedPos == getHoveredRow()) {
                    texture(context, WidgetTextures.CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                } else {
                    texture(context, WidgetTextures.REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    removeClickedPos = -1;
                }
            } else {
                if (canToggleVisibility(dimensionName)) {
                    if (isListShow) {
                        texture(context, WidgetTextures.SHOW_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        texture(context, WidgetTextures.HIDE_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    }
                }
                texture(context, WidgetTextures.ADD_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            }
        }

        drawText(context, textRenderer, waypointList.name(), indent + 18, rowY + textVertOffset, textColor, true);
        if (isListEmpty) {
            texture(context, WidgetTextures.LIST_EMPTY, indent, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        } else if (waypointList.isExpand()) {
            texture(context, WidgetTextures.LIST_EXPAND_ICON, indent, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        } else {
            texture(context, WidgetTextures.LIST_COLLAPSE_ICON, indent, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        }
    }

    private void renderWaypoint(
            GuiGraphicsExtractor context,
            WaypointNode waypointNode,
            boolean hovered,
            int rowY,
            int contentWidth,
            int indent
    ) {
        SimpleWaypoint waypoint = waypointNode.waypoint();
        String name = waypoint.name();
        String initials = waypoint.initials();
        boolean wpRendered = waypoint.isRendered();
        int bgAlpha = wpRendered ? 0xFF000000 : 0x80000000;
        int textColor = getColor(wpRendered ? TEXT_PRIMARY : TEXT_DISABLED);
        int rgb = waypoint.rgb();
        int y2 = rowY + itemHeight;
        if (hovered) {
            context.fill(0, rowY, contentWidth, y2, 0x60000000 + rgb);
            int wpCenteredBtnY = rowY + buttonIconVertOffset;
            if (canToggleVisibility(waypointNode.dimensionName())) {
                if (wpRendered) {
                    texture(context, WidgetTextures.SHOW_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                } else {
                    texture(context, WidgetTextures.HIDE_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                }
            }
            texture(context, WidgetTextures.EDIT_ICON, secondBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            if (removeClickedPos == getHoveredRow()) {
                texture(context, WidgetTextures.CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            } else {
                texture(context, WidgetTextures.REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                removeClickedPos = -1;
            }
            renderOutline(context, 0, rowY, contentWidth, itemHeight, 0xFF000000 + rgb);
        } else {
            context.fill(0, rowY, contentWidth, y2, 0x10000000 + rgb);
        }

        final int finalY = rowY + textVertOffset;
        final int backgroundColor = bgAlpha | rgb;
        if (waypoint.global()) {
            drawText(context, textRenderer, "*", indent + 6, finalY, textColor);
        }
        drawInitialsBox(context, initials, indent + 15, finalY - 1, backgroundColor, getInitialsTextColor(rgb, wpRendered));
        if (waypointNode.showListName()) {
            String listPrefix = waypointNode.showDimensionName()
                    ? waypointNode.dimensionName() + " / " + waypointNode.waypointList().name() + " / "
                    : waypointNode.waypointList().name() + " / ";
            drawText(context, textRenderer, listPrefix, indent + 55, finalY, getColor(TEXT_MUTED));
            drawText(context, textRenderer, name, indent + 55 + textRenderer.width(listPrefix), finalY, textColor);
        } else {
            drawText(context, textRenderer, name, indent + 55, finalY, textColor);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }

    private void drawInitialsBox(GuiGraphicsExtractor context, String initials, int x, int y, int backgroundColor, int textColor) {
        int textWidth = textRenderer.width(initials);
        int bgWidth = Math.max(textWidth + 2, textRenderer.lineHeight);
        int textX = (bgWidth - Math.max(0, textWidth - 1)) / 2;

        context.fill(x, y, x + bgWidth, y + textRenderer.lineHeight, backgroundColor);
        drawText(context, textRenderer, initials, x + textX, y + 1, textColor, true);
    }

    private static int getInitialsTextColor(int rgb, boolean rendered) {
        int color = getSafeTextColor(rgb);
        if (rendered) {
            return color;
        }
        return (color & 0x00FFFFFF) | 0x80000000;
    }

    public sealed interface RowNode permits DimensionNode, ListNode, WaypointNode {
    }

    private record DimensionNode(String dimensionName, List<ListNode> lists) implements RowNode {
    }

    private record ListNode(String dimensionName, WaypointList waypointList) implements RowNode {
    }

    private record WaypointNode(
            String dimensionName,
            WaypointList waypointList,
            SimpleWaypoint waypoint,
            boolean showListName,
            boolean showDimensionName
    ) implements RowNode {
    }

    private static class ViewWaypointList extends WaypointList {
        private final WaypointList source;
        private final List<SimpleWaypoint> waypoints;

        private ViewWaypointList(WaypointList source, List<SimpleWaypoint> waypoints) {
            super(source.name(), source.getSyncNum(), waypoints);
            this.source = source;
            this.waypoints = waypoints;
        }

        @Override
        public boolean isShow() {
            return this.source.isShow();
        }

        @Override
        public void setShow(boolean show) {
            this.source.setShow(show);
        }

        @Override
        public boolean isExpand() {
            return this.source.isExpand();
        }

        @Override
        public void setExpand(boolean expand) {
            this.source.setExpand(expand);
        }

        @Override
        public int size() {
            return this.waypoints.size();
        }

        @Override
        public boolean isEmpty() {
            return this.waypoints.isEmpty();
        }

        @Override
        public List<SimpleWaypoint> simpleWaypoints() {
            return Collections.unmodifiableList(new ArrayList<>(this.waypoints));
        }
    }
}
