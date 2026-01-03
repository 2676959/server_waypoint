package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.screens.WaypointEditScreen;
import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen;
import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.binarySearch;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class NewWaypointListWidget extends ScrollableWidget {
    private final WaypointManagerScreen parentScreen;
    private static double SCROLLED_POSITION = 0.0D;
    private final int itemHeight = 20;
    private final TextRenderer textRenderer;
    private final List<WaypointList> waypointLists;
    private final List<Integer> listPositions = new ArrayList<>();
    private boolean empty = false;
    private int contentHeight = 0;
    private final int btnWidth = itemHeight - 1;
    private int removeBtnXPos = width - btnWidth;
    private int editBtnXPos = removeBtnXPos - btnWidth;

    public NewWaypointListWidget(WaypointManagerScreen parent, int x, int y, int width, int height, TextRenderer textRenderer, Collection<WaypointList> waypointLists) {
        super(x, y, width, height, Text.literal("Waypoint lists"));
        this.parentScreen = parent;
        this.textRenderer = textRenderer;
        this.waypointLists = new ArrayList<>(waypointLists);
        recalculateListPositions();
        recalculateContentHeight();
        setScrollY(SCROLLED_POSITION);
    }

    public static void resetScroll() {
        SCROLLED_POSITION = 0.0D;
    }

    public void updateWaypointLists(Collection<WaypointList> newWaypointLists) {
        if (newWaypointLists.isEmpty()) {
            this.empty = true;
            this.waypointLists.clear();
            this.listPositions.clear();
        } else {
            this.empty = false;
            this.waypointLists.clear();
            this.waypointLists.addAll(newWaypointLists);
        }
        recalculateListPositions();
        recalculateContentHeight();
        setScrollY(0);
        LOGGER.info("list positions: {}", this.listPositions);
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

    private void recalculateContentHeight() {
        if (this.waypointLists == null || empty) {
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
            int pos = (int) scrollDistance / itemHeight;
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
                    return false;
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
                // clicked on waypoint
                // listIndex = insertIndex - 1; insertIndex = -index - 1
                int listIndex = -index - 2;
                int waypointIndex = pos - listPositions.get(listIndex) - 1;
                WaypointList waypointList = waypointLists.get(listIndex);
                List<SimpleWaypoint> simpleWaypoints = waypointList.simpleWaypoints();
                if (waypointIndex >= simpleWaypoints.size()) {
                    return false;
                }
                SimpleWaypoint waypoint = simpleWaypoints.get(waypointIndex);
                int removeBtnPos = removeBtnXPos + x;
                if (mouseX > x + editBtnXPos && mouseX < removeBtnPos) {
                    // clicked on edit button
                    MinecraftClient.getInstance().setScreen(new WaypointEditScreen(this.parentScreen, this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint));
                    LOGGER.info("edit");
                } else if (mouseX > removeBtnPos) {
                    // clicked on remove button
                    LOGGER.info("remove");
                }
                LOGGER.info("waypoint: {}", waypoint);
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ret = false;
        if (keyCode == 84) {
//            MinecraftClient.getInstance().getNetworkHandler().sendCommand(tpCmd(this.parentScreen.getSelectedDimension(), waypointList.name(), waypoint.name()).substring(1));
        }
        boolean ret2 = super.keyPressed(keyCode, scanCode, modifiers);
        return ret || ret2;
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
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        double scrollY = getScrollY();
        int i = 0;
        int x = getX();
        int y = getY();
        
        // offset
        MatrixStack matrixStack = context.getMatrices();
        matrixStack.translate(x, y, 0.0D);

        context.enableScissor(0, 0, width, height);
        context.fill(0, 0, width, height, 0x88000000);

        int listWidth = overflows() ?  width - SCROLLBAR_WIDTH : width;
        
        if (empty) {
            context.drawText(textRenderer, Text.literal("<Empty>").formatted(Formatting.ITALIC), 0, 0, 0xFFFFFFFF, true);
            matrixStack.translate(0.0D, scrollY, 0.0D);
            matrixStack.translate(-x, -y, 0.0D);
            context.disableScissor();
            this.drawScrollbar(context);
            return;
        }

        matrixStack.translate(0.0D, -scrollY, 0.0D);

        // highlight
        int pos = -1;
        if (mouseX < x + listWidth && mouseX > x && mouseY < y + this.contentHeight && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            pos = (int) scrollDistance / itemHeight;
        }

        // x for edit
        removeBtnXPos = listWidth - btnWidth;
        // x for width
        editBtnXPos = removeBtnXPos - btnWidth;

        for (int n = 0; n < this.waypointLists.size(); n++) {
            WaypointList waypointList = this.waypointLists.get(n);
            boolean isExpand = waypointList.isExpand();
            String prefix = isExpand ? "▼" : "▶";
            String listName = prefix + waypointList.name();
            int y1 = i * itemHeight;
            context.drawText(textRenderer, listName, 0, y1, 0xFFFFFFFF, true);
            if (waypointList.isEmpty()) {
                int textWidth = textRenderer.getWidth("空");
                context.drawText(textRenderer, "空", listWidth - textWidth, y1, 0x55FFFFFF, true);
            }
            if (pos == i) {
                context.fill(0, y1, listWidth, y1 + itemHeight, 0x30FFFFFF);
                context.drawBorder(0, y1, listWidth, itemHeight, 0xFFFFFFFF);
            }
            i++;
            if (isExpand) {
                List<SimpleWaypoint> simpleWaypoints = waypointList.simpleWaypoints();
//                if (simpleWaypoints.isEmpty()) {
//                    context.drawText(textRenderer, "empty", 0, i * itemHeight, 0x99FFFFFF, true);
//                    i++;
//                    continue;
//                }

                for (SimpleWaypoint simpleWaypoint : simpleWaypoints) {
                    String name = simpleWaypoint.name();
                    String initials = simpleWaypoint.initials();
                    int rgb = simpleWaypoint.rgb();
                    y1 = i * itemHeight;
                    int y2 = y1 + itemHeight;
                    if (pos == i) {
                        // highlight
                        context.fill(0, y1, editBtnXPos, y2, 0x60000000 + rgb);
                        // edit button
                        context.fill(editBtnXPos, y1, removeBtnXPos, y2, 0x55FFFF00);
                        // remove button
                        context.fill(removeBtnXPos, y1, listWidth, y2, 0x55FF0000);
                        // border
                        context.drawBorder(2, y1, listWidth - 2, itemHeight, 0xFF000000 + rgb);
                    } else {
                        context.fill(0, y1, listWidth, y2, 0x10000000 + rgb);
                    }
                    int finalY = y1;
                    context.draw(drawer -> {
                       textRenderer.draw(initials, 10, finalY, 0xFFFFFFFF, true, matrixStack.peek().getPositionMatrix(), drawer, TextRenderer.TextLayerType.SEE_THROUGH, 0xFF000000 + rgb, 0xFF);
                    });
//                    context.drawText(textRenderer, name, x + 30, y1, 0xFFFFFFFF, true);
                    context.drawCenteredTextWithShadow(textRenderer, name, 30, y1, 0xFFFFFFFF);
                    i++;
                }
                int listLen = simpleWaypoints.size();
                int listY = this.listPositions.get(n) * itemHeight;
                context.fill(0, listY, 2, listY + (listLen + 1) * itemHeight, 0xFFFFFFFF);
            }
        }
        matrixStack.translate(0.0D, scrollY, 0.0D);
        matrixStack.translate(-x, -y, 0.0D);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
