//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.WaypointTextures;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.client.util.MinecraftClientHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawText;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
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
    private boolean hideButtonEnabled = true;
    private String selectedDimensionName = "";
    private String searchQuery = "";
    private WaypointSorting.SortMode sortMode = WaypointSorting.SortMode.DEFAULT;

    public WaypointListWidget(int x, int y, int width, int height, WaypointManagerScreen parent, WaypointQueryEngine queryEngine, Font textRenderer) {
        super(x, y, width, height, itemHeight, Component.literal("Waypoint lists"), 5, 7, 10, 10, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
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

    public void setHideButtonEnabled(boolean hideButtonEnabled) {
        this.hideButtonEnabled = hideButtonEnabled;
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
        applySearchAndSort();
    }

    public WaypointSorting.SortMode getSortMode() {
        return this.sortMode;
    }

    public void sortByName() {
        this.setSortMode(WaypointSorting.SortMode.NAME);
    }

    public void sortByDistance() {
        this.setSortMode(WaypointSorting.SortMode.DISTANCE);
    }

    public void sortByColor() {
        this.setSortMode(WaypointSorting.SortMode.COLOR);
    }

    public List<String> getSearchSuggestions() {
        return this.queryEngine.getSearchSuggestions(this.selectedDimensionName);
    }

    private void applySearchAndSort() {
        WaypointQueryEngine.QueryResult result = this.queryEngine.queryDimension(
                this.selectedDimensionName,
                new WaypointQueryEngine.Query(this.searchQuery, this.sortMode, getPlayerWaypointPos())
        );
        List<WaypointList> displayWaypointLists = createDisplayWaypointLists(result);
        updateRoots(displayWaypointLists.stream().map(ListNode::new).map(RowNode.class::cast).toList());
    }

    private static List<WaypointList> createDisplayWaypointLists(WaypointQueryEngine.QueryResult result) {
        List<WaypointList> displayWaypointLists = new ArrayList<>();
        for (WaypointQueryEngine.DimensionResult dimensionResult : result.dimensions()) {
            for (WaypointQueryEngine.ListResult listResult : dimensionResult.lists()) {
                displayWaypointLists.add(new ViewWaypointList(listResult.sourceList(), listResult.waypoints()));
            }
        }
        return displayWaypointLists;
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
        if (value instanceof ListNode listNode) {
            return listNode.waypointList().simpleWaypoints().stream()
                    .map(waypoint -> new WaypointNode(listNode.waypointList(), waypoint))
                    .map(RowNode.class::cast)
                    .toList();
        }
        return List.of();
    }

    @Override
    protected boolean isExpanded(RowNode value) {
        return value instanceof ListNode listNode && listNode.waypointList().isExpand();
    }

    @Override
    protected void setExpanded(RowNode value, boolean expanded) {
        if (value instanceof ListNode listNode) {
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
            return onListClicked(entry.row(), listNode.waypointList(), contentMouseX);
        }
        if (value instanceof WaypointNode waypointNode) {
            return onWaypointClicked(entry.row(), waypointNode.waypointList(), waypointNode.waypoint(), contentMouseX);
        }
        return false;
    }

    private boolean onListClicked(int row, WaypointList waypointList, double contentMouseX) {
        if (waypointList.isEmpty()) {
            if (contentMouseX > thirdBtnXPos) {
                if (removeClickedPos == row) {
                    if (sendCommand(removeListCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), false))) {
                        this.removeClickedPos = -1;
                        return true;
                    }
                }
                removeClickedPos = row;
                return true;
            } else if (contentMouseX > secondBtnXPos) {
                MinecraftClientHelper.setScreen(new WaypointAddScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name()));
                this.removeClickedPos = -1;
                return true;
            }
        } else {
            if (contentMouseX > thirdBtnXPos) {
                MinecraftClientHelper.setScreen(new WaypointAddScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name()));
                this.removeClickedPos = -1;
                return true;
            } else if (contentMouseX > secondBtnXPos) {
                if (hideButtonEnabled) {
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

    private boolean onWaypointClicked(int row, WaypointList waypointList, SimpleWaypoint waypoint, double contentMouseX) {
        if (contentMouseX > thirdBtnXPos) {
            if (removeClickedPos == row) {
                if (sendCommand(removeCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint, false))) {
                    this.removeClickedPos = -1;
                    return true;
                }
            }
            this.removeClickedPos = row;
            return true;
        } else if (contentMouseX > secondBtnXPos) {
            MinecraftClientHelper.setScreen(new WaypointEditScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint));
            return true;
        } else if (contentMouseX > firstBtnXPos) {
            if (hideButtonEnabled) {
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
                sendCommand(tpCmd(this.parentScreen.getSelectedDimension(), waypointNode.waypointList().name(), waypointNode.waypoint().name(), false));
                ret = true;
            }
        }
        return ret || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        drawText(context, textRenderer, EMPTY_INFO_TEXT, 5, textVertOffset, 0x55FFFFFF, true);
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
        if (value instanceof ListNode listNode) {
            renderWaypointList(context, listNode.waypointList(), hovered, rowY, contentWidth);
        } else if (value instanceof WaypointNode waypointNode) {
            renderWaypoint(context, waypointNode.waypoint(), hovered, rowY, contentWidth);
        }
    }

    private void renderWaypointList(GuiGraphicsExtractor context, WaypointList waypointList, boolean hovered, int rowY, int contentWidth) {
        boolean isListShow = waypointList.isShow();
        int textColor = isListShow ? 0xFFFFFFFF : 0x80FFFFFF;
        if (hovered) {
            context.fill(0, rowY, contentWidth, rowY + itemHeight, 0x30FFFFFF);
            renderOutline(context, 0, rowY, contentWidth, itemHeight, 0xFFFFFFFF);
        }

        int centeredBtnY = rowY + buttonIconVertOffset;
        boolean isListEmpty = waypointList.isEmpty();
        if (hovered) {
            if (isListEmpty) {
                texture(context, WaypointTextures.ADD_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                if (removeClickedPos == getHoveredRow()) {
                    texture(context, WaypointTextures.CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                } else {
                    texture(context, WaypointTextures.REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    removeClickedPos = -1;
                }
            } else {
                if (isListShow) {
                    texture(context, WaypointTextures.SHOW_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                } else {
                    texture(context, WaypointTextures.HIDE_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                }
                texture(context, WaypointTextures.ADD_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            }
        }

        drawText(context, textRenderer, waypointList.name(), 18, rowY + textVertOffset, textColor, true);
        if (isListEmpty) {
            texture(context, WaypointTextures.LIST_EMPTY, 0, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        } else if (waypointList.isExpand()) {
            texture(context, WaypointTextures.LIST_EXPAND_ICON, 0, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        } else {
            texture(context, WaypointTextures.LIST_COLLAPSE_ICON, 0, rowY + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
        }
    }

    private void renderWaypoint(GuiGraphicsExtractor context, SimpleWaypoint waypoint, boolean hovered, int rowY, int contentWidth) {
        String name = waypoint.name();
        String initials = waypoint.initials();
        boolean wpRendered = waypoint.isRendered();
        int bgAlpha = wpRendered ? 0xFF000000 : 0x80000000;
        int textColor = wpRendered ? 0xFFFFFFFF : 0x80FFFFFF;
        int rgb = waypoint.rgb();
        int y2 = rowY + itemHeight;
        if (hovered) {
            context.fill(0, rowY, contentWidth, y2, 0x60000000 + rgb);
            int wpCenteredBtnY = rowY + buttonIconVertOffset;
            if (wpRendered) {
                texture(context, WaypointTextures.SHOW_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            } else {
                texture(context, WaypointTextures.HIDE_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            }
            texture(context, WaypointTextures.EDIT_ICON, secondBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            if (removeClickedPos == getHoveredRow()) {
                texture(context, WaypointTextures.CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
            } else {
                texture(context, WaypointTextures.REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                removeClickedPos = -1;
            }
            renderOutline(context, 0, rowY, contentWidth, itemHeight, 0xFF000000 + rgb);
        } else {
            context.fill(0, rowY, contentWidth, y2, 0x10000000 + rgb);
        }

        final int finalY = rowY + textVertOffset;
        final int backgroundColor = bgAlpha | rgb;
        if (waypoint.global()) {
            drawText(context, textRenderer, "*", 6, finalY, textColor);
        }
        drawInitialsBox(context, initials, 15, finalY - 1, backgroundColor, getInitialsTextColor(rgb, wpRendered));
        drawText(context, textRenderer, name, 55, finalY, textColor);
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

    public sealed interface RowNode permits ListNode, WaypointNode {
    }

    private record ListNode(WaypointList waypointList) implements RowNode {
    }

    private record WaypointNode(WaypointList waypointList, SimpleWaypoint waypoint) implements RowNode {
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
