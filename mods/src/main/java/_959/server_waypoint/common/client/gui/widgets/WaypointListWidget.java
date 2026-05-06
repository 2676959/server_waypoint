package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import _959.server_waypoint.common.client.gui.screens.WaypointAddScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.client.render.OptimizedWaypointRenderer;
import _959.server_waypoint.common.util.MathHelper;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
//? if >= 1.21.11
import net.minecraft.resources.Identifier;
//? if < 1.21.11
/*import net.minecraft.resources.ResourceLocation;*/

import static _959.server_waypoint.ModInfo.MOD_ID;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.texture;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;
import static _959.server_waypoint.common.client.gui.screens.MovementAllowedScreen.centered;
import static _959.server_waypoint.common.client.util.ClientCommandUtils.sendCommand;
import static _959.server_waypoint.util.CommandGenerator.*;
import static _959.server_waypoint.util.ListMapUtils.getLastElement;
import static java.util.Collections.binarySearch;

public class WaypointListWidget extends ShiftableScrollableWidget implements Padding, Expandable {
    public static int TELEPORT_KEY = 84;
    public static final Component EMPTY_INFO_TEXT = Component.translatable("waypoint.empty_mark");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ SHOW_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/show.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ HIDE_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/hide.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ ADD_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/add.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ EDIT_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/edit.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ REMOVE_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/delete.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ CONFIRM_REMOVE_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/confirm_delete.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ LIST_EMPTY = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/list_empty.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ LIST_EXPAND_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/list_expand.png");
    public static final /*? if < 1.21.11 {*//*ResourceLocation*//*?} else {*/ Identifier /*?}*/ LIST_COLLAPSE_ICON = _959.server_waypoint.common.util.ResourceLocationHelper.id(MOD_ID, "textures/gui/list_collapse.png");
    private static final int listIconSize = 16;
    private static final int buttonIconSize = 12;
    private static double SCROLLED_POSITION = 0.0D;
    private final WaypointManagerScreen parentScreen;
    private final Font textRenderer;
    private final PaddingBackground paddingBackground = new PaddingBackground(this, 5, 7, 10, 10, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
    private volatile @Unmodifiable List<WaypointList> waypointLists = new ArrayList<>();
    private final List<Integer> listPositions = new ArrayList<>();
    private final int itemHeight = 20;
    private final int textVertOffset;
    private final int listIconVertOffset;
    private final int buttonIconVertOffset;
    private final int buttonIconHrzOffset;
    private final int btnWidth = 19;
    private int thirdBtnXPos = width - btnWidth;
    private int secondBtnXPos = thirdBtnXPos - btnWidth;
    private int firstBtnXPos = secondBtnXPos - btnWidth;
    private boolean empty = true;
    private int contentHeight = 0;
    private int removeClickedPos = -1;
    private int hoverPos = -2;
    private boolean hideButtonEnabled = true;

    public WaypointListWidget(int x, int y, int width, int height, WaypointManagerScreen parent, Font textRenderer) {
        super(x, y, width, height, Component.literal("Waypoint lists"));
        this.parentScreen = parent;
        this.textRenderer = textRenderer;
        recalculateListPositions();
        recalculateContentHeight();
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
     * updates the reference of {@link #waypointLists}, if newWaypointLists is empty only clears the current list
     * */
    public void updateWaypointLists(@Unmodifiable List<WaypointList> newWaypointLists) {
        if (newWaypointLists.isEmpty()) {
            this.empty = true;
            this.listPositions.clear();
        } else {
            this.empty = false;
        }
        this.waypointLists = newWaypointLists;
        recalculateListPositions();
        recalculateContentHeight();
        setScrollY(MathHelper.clamp(SCROLLED_POSITION, 0, getContentHeight()));
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
            WaypointList waypointList = getLastElement(this.waypointLists);
            int lastSize = waypointList.isExpand() ? waypointList.size() + 1 : 1;
            this.contentHeight = (getLastElement(listPositions) + lastSize) * itemHeight;
        }
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
        return bl;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (empty) {
            return false;
        }
        if (this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = getX();
        int y = getY();
        int x1 = x + this.width;
        int y1 = y + this.height;
        if (mouseX < x1 && mouseX > x && mouseY < y1 && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            int pos = (int) Math.floor(scrollDistance / itemHeight);
            WaypointList lastWaypointList = getLastElement(this.waypointLists);
            int lastSize = lastWaypointList.isExpand() ? lastWaypointList.size() : 0;
            int lastPos = getLastElement(listPositions) + lastSize;
            if (pos > lastPos) {
                return false;
            }

            int index = binarySearch(listPositions, pos);
            int firstBtn = x + firstBtnXPos;
            int secondBtn = x + secondBtnXPos;
            int thirdBtn = x + thirdBtnXPos;
            if (index >= 0) {
                // clicked on list
                WaypointList waypointList = waypointLists.get(index);
                if (waypointList.isEmpty()) {
                    if (mouseX > thirdBtn) {
                        // clicked on remove button
                        if (removeClickedPos == pos) {
                            if (sendCommand(removeListCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), false))) {
                                this.removeClickedPos = -1;
                                return true;
                            }
                        }
                        removeClickedPos = pos;
                        return true;
                    } else if (mouseX > secondBtn) {
                        // clicked on add button
                        Minecraft.getInstance().setScreen(new WaypointAddScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name()));
                        this.removeClickedPos = -1;
                        return true;
                    }
                } else {
                    if (mouseX > thirdBtn) {
                        // clicked on add button
                        Minecraft.getInstance().setScreen(new WaypointAddScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name()));
                        this.removeClickedPos = -1;
                        return true;
                    } else if (mouseX > secondBtn) {
                        // clicked on hide button
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
                if (mouseX > thirdBtn) {
                    // clicked on remove button
                    if (removeClickedPos == pos) {
                        if (sendCommand(removeCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint, false))) {
                            this.removeClickedPos = -1;
                            return true;
                        }
                    }
                    this.removeClickedPos = pos;
                } else if (mouseX > secondBtn) {
                    // clicked on edit button
                    Minecraft.getInstance().setScreen(new WaypointEditScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint));
                    return true;
                } else if (mouseX > firstBtn) {
                    // clicked on show button
                    if (hideButtonEnabled) {
                        if (waypoint.isRendered()) {
                            OptimizedWaypointRenderer.remove(waypoint);
                        } else {
                            OptimizedWaypointRenderer.add(waypoint);
                        }
                    }
                    return true;
                }
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
    public int getContentHeight() {
        return this.contentHeight;
    }

    @Override
    public double getDeltaYPerScroll() {
        return 5;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        double scrollY = getScrollY();
        int i = 0;
        int x = getX();
        int y = getY();

        this.paddingBackground.render(context, mouseX, mouseY, deltaTicks);

        context.enableScissor(x, y, x + width, y + height);

        // offset
        push(context);
        translate(context, x, y);

        int listWidth = overflows() ?  width - SCROLLBAR_WIDTH : width;
        
        if (empty) {
            context.drawString(textRenderer, EMPTY_INFO_TEXT, 5, textVertOffset, 0x55FFFFFF, true);
            pop(context);
            context.disableScissor();
            this.drawScrollbar(context);
            return;
        }

        translate(context, 0.0F, (float) -scrollY);

        // highlight
        hoverPos = -2;
        if (mouseX < x + listWidth && mouseX > x && mouseY < y + this.contentHeight && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            hoverPos = (int) scrollDistance / itemHeight;
        } else {
            removeClickedPos = -1;
        }

        // x for edit
        thirdBtnXPos = listWidth - btnWidth;
        // x for width
        secondBtnXPos = thirdBtnXPos - btnWidth;
        // x for show
        firstBtnXPos = secondBtnXPos - btnWidth;
        // waypoint text background alpha
        int bgAlpha;

        for (WaypointList waypointList : this.waypointLists) {
            int y1 = i * itemHeight;
            boolean isListShow = waypointList.isShow();
            int textColor = 0x80FFFFFF;
            if (isListShow) {
                textColor = 0xFFFFFFFF;
            }
            // list highlight
            boolean hoverOnList = hoverPos == i;
            if (hoverOnList) {
                context.fill(0, y1, listWidth, y1 + itemHeight, 0x30FFFFFF);
                context.renderOutline(0, y1, listWidth, itemHeight, 0xFFFFFFFF);
            }
            // plus one for list name row
            i++;
            int centeredBtnY = y1 + buttonIconVertOffset;
            boolean isListEmpty = waypointList.isEmpty();
            // render hover buttons on list
            if (hoverOnList) {
                if (isListEmpty) {
                    texture(context, ADD_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    if (removeClickedPos == hoverPos) {
                        texture(context, CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        texture(context, REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                        removeClickedPos = -1;
                    }
                } else {
                    if (isListShow) {
                        texture(context, SHOW_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        texture(context, HIDE_ICON, secondBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    }
                    texture(context, ADD_ICON, thirdBtnXPos + buttonIconHrzOffset, centeredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                }
            }
            // waypoint list name
            context.drawString(textRenderer, waypointList.name(), 18, y1 + textVertOffset, textColor, true);
            // render list expand icon
            if (isListEmpty) {
                texture(context, LIST_EMPTY, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
                continue;
            }
            if (waypointList.isExpand()) {
                texture(context, LIST_EXPAND_ICON, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
            } else {
                texture(context, LIST_COLLAPSE_ICON, 0, y1 + listIconVertOffset, 0, 0, listIconSize, listIconSize, listIconSize, listIconSize);
                continue;
            }
            List<SimpleWaypoint> waypoints = waypointList.simpleWaypoints();
            for (SimpleWaypoint waypoint : waypoints) {
                String name = waypoint.name();
                String initials = waypoint.initials();
                boolean wpRendered = waypoint.isRendered();
                bgAlpha = 0x80000000;
                textColor = 0x80FFFFFF;
                if (wpRendered) {
                    bgAlpha = 0xFF000000;
                    textColor = 0xFFFFFFFF;
                }
                int rgb = waypoint.rgb();
                y1 = i * itemHeight;
                int y2 = y1 + itemHeight;
                if (hoverPos == i) {
                    // highlight
                    context.fill(0, y1, listWidth, y2, 0x60000000 + rgb);
                    int wpCenteredBtnY = y1 + buttonIconVertOffset;
                    // show button
                    if (wpRendered) {
                        texture(context, SHOW_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        texture(context, HIDE_ICON, firstBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    }
                    // edit button
                    texture(context, EDIT_ICON, secondBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    // remove button
                    if (removeClickedPos == hoverPos) {
                        texture(context, CONFIRM_REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                    } else {
                        texture(context, REMOVE_ICON, thirdBtnXPos + buttonIconHrzOffset, wpCenteredBtnY, 0, 0, buttonIconSize, buttonIconSize, buttonIconSize, buttonIconSize);
                        removeClickedPos = -1;
                    }
                    // border
                    context.renderOutline(0, y1, listWidth, itemHeight, 0xFF000000 + rgb);
                } else {
                    context.fill(0, y1, listWidth, y2, 0x10000000 + rgb);
                }
                final int finalY = y1 + textVertOffset;
                final int finalTextColor = textColor;
                final int backgroundColor = bgAlpha | rgb;
                if (waypoint.global()) {
                    context.drawString(textRenderer, "*", 6, finalY, textColor);
                }
                context.fill(15, finalY - 1, 15 + textRenderer.width(initials), finalY + textRenderer.lineHeight, backgroundColor);
                context.drawString(textRenderer, initials, 15, finalY, finalTextColor, true);
                context.drawString(textRenderer, name, 55, finalY, textColor);
                i++;
            }
        }
        pop(context);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {

    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
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
