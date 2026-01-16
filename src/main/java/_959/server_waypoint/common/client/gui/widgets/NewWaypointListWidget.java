package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.util.Pair;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.util.CommandGenerator.*;
import static java.util.Collections.binarySearch;

public class NewWaypointListWidget extends ShiftableScrollableWidget implements Padding, Expandable {
    public static int TELEPORT_KEY = 84;
    public static final Text EMPTY_MARK = Text.translatable("waypoint.empty_mark");
    public static final Identifier EDIT_ICON = Identifier.of("server_waypoint", "textures/gui/edit.png");
    public static final Identifier REMOVE_ICON = Identifier.of("server_waypoint", "textures/gui/delete.png");
    public static final Identifier CONFIRM_REMOVE_ICON = Identifier.of("server_waypoint", "textures/gui/confirm_delete.png");
    public static final Identifier LIST_EMPTY = Identifier.of("server_waypoint", "textures/gui/list_empty.png");
    public static final Identifier LIST_EXPAND_ICON = Identifier.of("server_waypoint", "textures/gui/list_expand.png");
    public static final Identifier LIST_COLLAPSE_ICON = Identifier.of("server_waypoint", "textures/gui/list_collapse.png");
    private static final int listIconSize = 16;
    private static final int buttonIconSize = 11;
    private static double SCROLLED_POSITION = 0.0D;
    private final WaypointManagerScreen parentScreen;
    private final TextRenderer textRenderer;
    private final PaddingBackground paddingBackground = new PaddingBackground(this, 5, 10, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
    private volatile List<WaypointList> waypointLists = new ArrayList<>();
    private final List<Integer> listPositions = new ArrayList<>();
    private final int itemHeight = 20;
    private final int textVertOffset;
    private final int listIconVertOffset;
    private final int buttonIconVertOffset;
    private final int buttonIconHrzOffset;
    private final int btnWidth = 19;
    private int removeBtnXPos = width - btnWidth;
    private int editBtnXPos = removeBtnXPos - btnWidth;
    private boolean empty = true;
    private int contentHeight = 0;
    private int removeClickedPos = -1;
    private int hoverPos = -2;

    public NewWaypointListWidget(int x, int y, int width, int height, WaypointManagerScreen parent, TextRenderer textRenderer) {
        super(x, y, width, height, Text.literal("Waypoint lists"));
        this.parentScreen = parent;
        this.textRenderer = textRenderer;
        recalculateListPositions();
        recalculateContentHeight();
        setScrollY(SCROLLED_POSITION);
        textVertOffset = centered(itemHeight, textRenderer.fontHeight);
        listIconVertOffset = centered(itemHeight, listIconSize);
        buttonIconVertOffset = centered(itemHeight, buttonIconSize);
        buttonIconHrzOffset = centered(btnWidth, buttonIconSize);
    }

    public static void resetScroll() {
        SCROLLED_POSITION = 0.0D;
    }

    /**
     * updates the reference of {@link #waypointLists}, if newWaypointLists is empty only clears the current list
     * */
    public void updateWaypointLists(List<WaypointList> newWaypointLists) {
        if (newWaypointLists.isEmpty()) {
            this.empty = true;
            this.waypointLists.clear();
            this.listPositions.clear();
        } else {
            this.empty = false;
            this.waypointLists.clear();
            LOGGER.info("new waypoint Lists :{}", newWaypointLists);
            this.waypointLists = newWaypointLists;
        }
        recalculateListPositions();
        recalculateContentHeight();
        setScrollY(Math.clamp(SCROLLED_POSITION, 0, getContentsHeightWithPadding()));
        LOGGER.info("list positions: {}", this.listPositions);
    }

    /**
     * only recalculate rendering related data, do not change the content
     */
    public void reCalculateRenderData() {
        recalculateListPositions();
        recalculateContentHeight();
    }

    private void recalculateListPositions() {
        this.listPositions.clear();
        if (this.waypointLists.isEmpty()) return;
        this.listPositions.add(0);
        for (int i = 1; i < this.waypointLists.size(); i++) {
            int prev = i - 1;
            WaypointList waypointList = this.waypointLists.get(prev);
            int prevPosition = this.listPositions.get(prev);
            if (waypointList.isExpand()) {
                this.listPositions.add(prevPosition + waypointList.size() + 1);
            } else {
                this.listPositions.add(prevPosition + 1);
            }
        }
    }

    /**
     * must be called after {@link #recalculateListPositions()} to set the correct content height
     */
    private void recalculateContentHeight() {
        if (this.empty) {
            this.contentHeight = 0;
        } else {
            WaypointList waypointList = this.waypointLists.getLast();
            int lastSize = waypointList.isExpand() ? waypointList.size() + 1 : 1;
            this.contentHeight = (listPositions.getLast() + lastSize) * itemHeight;
        }
    }

    public void setEmpty() {
        this.empty = true;
        this.listPositions.clear();
        this.contentHeight = 0;
        SCROLLED_POSITION = 0.0D;
    }

    /**
     * get waypoint and its list by the result of the binary search on {@link #listPositions} </br>
     * @param index must strictly less than 0
     * */
    private Pair<@NotNull WaypointList, @NotNull SimpleWaypoint> getWaypointByIndex(int pos, int index) {
        // listIndex = insertIndex - 1; insertIndex = -index - 1
        if (index == -1) return null;
        int listIndex = -index - 2;
        int waypointIndex = pos - listPositions.get(listIndex) - 1;
        WaypointList waypointList = waypointLists.get(listIndex);
        List<SimpleWaypoint> simpleWaypoints = waypointList.simpleWaypoints();
        if (waypointIndex >= simpleWaypoints.size()) {
            return null;
        }
        return new Pair<>(waypointList, simpleWaypoints.get(waypointIndex));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean bl = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        SCROLLED_POSITION = getScrollY();
        WaypointServerMod.LOGGER.info("scrolled: {}", SCROLLED_POSITION);
        return bl;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (empty) {
            return false;
        }
        if (this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return true;
        };
        int x = getX();
        int y = getY();
        int x1 = x + this.width;
        int y1 = y + this.height;
        if (mouseX < x1 && mouseX > x && mouseY < y1 && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            int pos = (int) Math.floor(scrollDistance / itemHeight);
            WaypointList lastWaypointList = this.waypointLists.getLast();
            int lastSize = lastWaypointList.isExpand() ? lastWaypointList.size() : 0;
            int lastPos = listPositions.getLast() + lastSize;
            if (pos > lastPos) {
                return false;
            }

            int index = binarySearch(listPositions, pos);
            LOGGER.info("pos: {}, index: {}", pos, index);
            if (index >= 0) {
                // clicked on list
                WaypointList waypointList = waypointLists.get(index);
                if (waypointList.isEmpty()) {
                    int removeBtnPos = removeBtnXPos + x;
                    if (mouseX > removeBtnPos) {
                        if (removeClickedPos == pos) {
                            ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                            if (networkHandler != null) {
                                networkHandler.sendCommand(removeListCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), false));
                                this.removeClickedPos = -1;
                                return true;
                            }
                        }
                        removeClickedPos = pos;
                        return true;
                    }
                }
                int size = waypointList.size();
                if (waypointList.isExpand()) {
                    for (int i = index + 1; i < listPositions.size(); i++) {
                        listPositions.set(i, listPositions.get(i) - size);
                    }
                    waypointList.setExpand(false);
                } else {
                    for (int i = index + 1; i < listPositions.size(); i++) {
                        listPositions.set(i, listPositions.get(i) + size);
                    }
                    waypointList.setExpand(true);
                }
                recalculateContentHeight();
                SCROLLED_POSITION = getScrollY();
                refreshScroll();
                return true;
            } else {
                Pair<WaypointList, SimpleWaypoint> result = getWaypointByIndex(pos, index);
                if (result == null) {
                    return false;
                }
                WaypointList waypointList = result.left();
                SimpleWaypoint waypoint = result.right();
                int removeBtnPos = removeBtnXPos + x;
                if (mouseX > x + editBtnXPos && mouseX < removeBtnPos) {
                    // clicked on edit button
                    MinecraftClient.getInstance().setScreen(new WaypointEditScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint));

                } else if (mouseX > removeBtnPos) {
                    // clicked on remove button
                    if (removeClickedPos == pos) {
                        ClientPlayNetworkHandler networkHandler = MinecraftClient.getInstance().getNetworkHandler();
                        if (networkHandler != null) {
                            networkHandler.sendCommand(removeCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint, false));
                            this.removeClickedPos = -1;
                            return true;
                        }
                    }
                    this.removeClickedPos = pos;
                }
                LOGGER.info("waypoint: {}", waypoint);
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ret = false;
        if (keyCode == TELEPORT_KEY) {
            int index = binarySearch(listPositions, hoverPos);
            // hover on a waypoint
            if (index < 0) {
                Pair<WaypointList, SimpleWaypoint> result = getWaypointByIndex(hoverPos, index);
                if (result != null) {
                    WaypointList waypointList = result.left();
                    SimpleWaypoint waypoint = result.right();
                    sendCommand(tpCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint.name(), false));
                    ret = true;
                }
            }
        }
        return ret || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        SCROLLED_POSITION = getScrollY();
    }

    @Override
    protected int getContentsHeightWithPadding() {
        return this.contentHeight;
    }

    @Override
    protected double getDeltaYPerScroll() {
        return 5;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        double scrollY = getScrollY();
        int i = 0;
        int x = getX();
        int y = getY();

        this.paddingBackground.render(context, mouseX, mouseY, deltaTicks);
        
        // offset
        MatrixStack matrixStack = context.getMatrices();
        matrixStack.translate(x, y, 0.0D);

        context.enableScissor(0, 0, width, height);
        int listWidth = overflows() ?  width - SCROLLBAR_WIDTH : width;
        
        if (empty) {
            context.drawText(textRenderer, EMPTY_MARK, 5, textVertOffset, 0x55FFFFFF, true);
            matrixStack.translate(0.0D, scrollY, 0.0D);
            matrixStack.translate(-x, -y, 0.0D);
            context.disableScissor();
            this.drawScrollbar(context);
            return;
        }

        matrixStack.translate(0.0D, -scrollY, 0.0D);

        // highlight
        hoverPos = -2;
        if (mouseX < x + listWidth && mouseX > x && mouseY < y + this.contentHeight && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            hoverPos = (int) scrollDistance / itemHeight;
        } else {
            removeClickedPos = -1;
        }

        // x for edit
        removeBtnXPos = listWidth - btnWidth;
        // x for width
        editBtnXPos = removeBtnXPos - btnWidth;

        for (WaypointList waypointList : this.waypointLists) {
            int y1 = i * itemHeight;
            // list highlight
            boolean hoverOnList = hoverPos == i;
            if (hoverOnList) {
                context.fill(0, y1, listWidth, y1 + itemHeight, 0x30FFFFFF);
                context.drawBorder(0, y1, listWidth, itemHeight, 0xFFFFFFFF);
            }
            // plus one for list name row
            i++;
            // waypoint list name
            context.drawText(textRenderer, waypointList.name(), 18, y1 + textVertOffset, 0xFFFFFFFF, true);
            // render empty mark
            if (waypointList.isEmpty()) {
                int textWidth = textRenderer.getWidth(EMPTY_MARK);
                context.drawTextWithShadow(textRenderer, EMPTY_MARK, listWidth - textWidth - buttonIconSize - 8, y1 + textVertOffset, 0x55FFFFFF);
                context.drawTexture(RenderLayer::getGuiTextured, LIST_EMPTY, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
                int y3 = y1 + buttonIconVertOffset;
                if (hoverOnList) {
                    if (removeClickedPos == hoverPos) {
                        context.drawTexture(RenderLayer::getGuiTextured, CONFIRM_REMOVE_ICON, removeBtnXPos + buttonIconHrzOffset, y3, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        context.drawTexture(RenderLayer::getGuiTextured, REMOVE_ICON, removeBtnXPos + buttonIconHrzOffset, y3, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                        removeClickedPos = -1;
                    }
                }
                continue;
            }
            // render list expand icon
            if (waypointList.isExpand()) {
                context.drawTexture(RenderLayer::getGuiTextured, LIST_EXPAND_ICON, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
            } else {
                context.drawTexture(RenderLayer::getGuiTextured, LIST_COLLAPSE_ICON, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
                continue;
            }
            List<SimpleWaypoint> simpleWaypoints = waypointList.simpleWaypoints();
            for (SimpleWaypoint simpleWaypoint : simpleWaypoints) {
                String name = simpleWaypoint.name();
                String initials = simpleWaypoint.initials();
                int rgb = simpleWaypoint.rgb();
                y1 = i * itemHeight;
                int y2 = y1 + itemHeight;
                if (hoverPos == i) {
                    // highlight
                    context.fill(0, y1, listWidth, y2, 0x60000000 + rgb);
                    // edit button
                    int y3 = y1 + buttonIconVertOffset;
                    context.drawTexture(RenderLayer::getGuiTextured, EDIT_ICON, editBtnXPos + buttonIconHrzOffset, y3, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    // remove button
                    if (removeClickedPos == hoverPos) {
                        context.drawTexture(RenderLayer::getGuiTextured, CONFIRM_REMOVE_ICON, removeBtnXPos + buttonIconHrzOffset, y3, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        context.drawTexture(RenderLayer::getGuiTextured, REMOVE_ICON, removeBtnXPos + buttonIconHrzOffset, y3, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                        removeClickedPos = -1;
                    }
                    // border
                    context.drawBorder(0, y1, listWidth, itemHeight, 0xFF000000 + rgb);
                } else {
                    context.fill(0, y1, listWidth, y2, 0x10000000 + rgb);
                }
                final int finalY = y1 + textVertOffset;
                context.draw(drawer -> {
                    textRenderer.draw(initials, 10, finalY, 0xFFFFFFFF, true, matrixStack.peek().getPositionMatrix(), drawer, TextRenderer.TextLayerType.SEE_THROUGH, 0xFF000000 + rgb, 0xFF);
                });
                context.drawTextWithShadow(textRenderer, name, 30, finalY, 0xFFFFFFFF);
                i++;
            }
//                int listLen = simpleWaypoints.size();
//                int listY = this.listPositions.get(n) * itemHeight;
//                context.fill(0, listY, 2, listY + (listLen + 1) * itemHeight, 0xFFFFFFFF);
        }
        matrixStack.translate(0.0D, scrollY, 0.0D);
        matrixStack.translate(-x, -y, 0.0D);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }

    @Override
    public void setVisualHeight(int height) {
        setHeight(height - (this.paddingBackground.getVisualHeight() - getHeight()));
    }

    @Override
    public void setVisualWidth(int width) {
        setWidth(width - (this.paddingBackground.getVisualWidth() - getWidth()));
    }

    @Override
    public int getVisualHeight() {
        return this.paddingBackground.getVisualHeight();
    }

    @Override
    public int getVisualWidth() {
        return this.paddingBackground.getVisualWidth();
    }

    @Override
    public int getVisualX() {
        return this.paddingBackground.getVisualX();
    }

    @Override
    public int getVisualY() {
        return this.paddingBackground.getVisualY();
    }

    @Override
    public void setPaddedX(int x) {
        this.paddingBackground.setPaddedX(x);
    }

    @Override
    public void setPaddedY(int y) {
        this.paddingBackground.setPaddedY(y);
    }
}
