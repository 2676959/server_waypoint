package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.binarySearch;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class NewWaypointListWidget extends ScrollableWidget {
    private static double SCROLLED_POSITION = 0.0D;
    private final int itemHeight = 20;
    private final TextRenderer textRenderer;
    private final List<WaypointList> waypointLists;
    private final List<Integer> listPositions = new ArrayList<>();
    private boolean empty = false;

    public NewWaypointListWidget(int x, int y, int width, int height, TextRenderer textRenderer, Collection<WaypointList> waypointLists) {
        super(x, y, width, height, Text.literal("Waypoints"));
        this.textRenderer = textRenderer;
        setScrollY(SCROLLED_POSITION);
        this.waypointLists = new ArrayList<>(waypointLists);
        this.listPositions.add(0);
        for (int i = 1; i < this.waypointLists.size(); i++) {
            this.listPositions.add(this.waypointLists.get(i).size() + 1);
        }
    }

    public void updateWaypointLists(Collection<WaypointList> newWaypointLists) {
        if (newWaypointLists.isEmpty()) {
            this.empty = true;
            this.waypointLists.clear();
            this.listPositions.clear();
            return;
        }
        this.waypointLists.clear();
        this.waypointLists.addAll(newWaypointLists);
        this.listPositions.clear();
        this.listPositions.add(0);
        for (int i = 1; i < this.waypointLists.size(); i++) {
            WaypointList waypointList = this.waypointLists.get(i);
            if (waypointList.isExpand()) {
                this.listPositions.add(waypointList.size() + 1);
            } else  {
                this.listPositions.add(listPositions.get(i - 1) + 1);
            }
        }
    }

    public void setEmpty() {
        this.empty = true;
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
            LOGGER.info("pos: {}", pos);

            int index = binarySearch(listPositions, pos);
            if (index >= 0) {
                WaypointList waypointList = waypointLists.get(index);
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
                SCROLLED_POSITION = getScrollY();
                refreshScroll();
                return true;
            } else {
                // listIndex = insertIndex - 1; insertIndex = -index - 1
                int listIndex = -index - 2;
                int waypointIndex = pos - listPositions.get(listIndex) - 1;
                List<SimpleWaypoint> simpleWaypoints = waypointLists.get(listIndex).simpleWaypoints();
                if (waypointIndex >= simpleWaypoints.size()) {
                    return false;
                }
                SimpleWaypoint waypoint = simpleWaypoints.get(waypointIndex);
                LOGGER.info("waypoint: {}", waypoint);
            }
        }
        return false;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        SCROLLED_POSITION = getScrollY();
    }

    @Override
    protected int getContentsHeightWithPadding() {
        if (this.waypointLists == null || empty) {
            return 0;
        }
        WaypointList waypointList = this.waypointLists.getLast();
        int lastSize = waypointList.isExpand() ? waypointList.size() + 1 : 1;
        return (listPositions.getLast() + lastSize) * itemHeight;
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
        int x2 = x + width;
//        int y2 = y + height;

        context.getMatrices().translate(x, y, 0.0D);

        context.enableScissor(0, 0, width, height);
        context.fill(0, 0, width, height, 0x88000000);

        context.getMatrices().translate(0.0D, -scrollY, 0.0D);

        if (empty) {
            context.drawText(textRenderer, "<Empty>", width / 2, 0, 0xFFFFFFFF, true);
            return;
        }

        if (mouseX < x2 && mouseX > x && mouseY < y + getContentsHeightWithPadding() && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            int pos = (int) scrollDistance / itemHeight;
            int borderY1 = pos * itemHeight;
            context.drawBorder(0, borderY1, width, itemHeight, 0xFFFFFFFF);
        }

        int listY = 0;
        for (WaypointList waypointList : waypointLists) {
            boolean isExpand = waypointList.isExpand();
            String prefix = isExpand ? "▼" : "▶";
            String listName = prefix + waypointList.name();
            context.drawText(textRenderer, listName, 0, i * itemHeight, 0xFFFFFFFF, true);
            listY += i * itemHeight;
            i++;
            if (isExpand) {
                List<SimpleWaypoint> simpleWaypoints = waypointList.simpleWaypoints();
                if (simpleWaypoints.isEmpty()) {
                    context.drawText(textRenderer, "empty", 0, i * itemHeight, 0x99FFFFFF, true);
                    i++;
                    continue;
                }
                for (SimpleWaypoint simpleWaypoint : simpleWaypoints) {
                    String name = simpleWaypoint.name();
                    String initials = simpleWaypoint.initials();
                    int y1 = i * itemHeight;
                    Integer rgb = Formatting.byColorIndex(simpleWaypoint.colorIdx()).getColorValue();
                    context.fill(0, y1, width, y1 + itemHeight, 0x10000000 + rgb);
                    context.draw(drawer -> {
                       textRenderer.draw(initials, 10, y1, 0xFFFFFFFF, true, context.getMatrices().peek().getPositionMatrix(), drawer, TextRenderer.TextLayerType.SEE_THROUGH, 0xFF000000 + rgb, 0xFF);
                    });
//                    context.drawText(textRenderer, name, x + 30, y1, 0xFFFFFFFF, true);
                    context.drawCenteredTextWithShadow(textRenderer, name, 30, y1, 0xFFFFFFFF);
                    i++;
                }
                int listLen = simpleWaypoints.size() + 1;
                context.fill(0, listY, 2, listY + listLen * itemHeight, 0xFFFFFFFF);
            }
        }
        context.getMatrices().translate(0.0D, scrollY, 0.0D);
        context.getMatrices().translate(-x, -y, 0.0D);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {

    }
}
