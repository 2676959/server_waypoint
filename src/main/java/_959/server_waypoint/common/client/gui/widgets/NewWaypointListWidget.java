package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ScrollableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.binarySearch;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class NewWaypointListWidget extends ScrollableWidget {
    public static final List<WaypointList> WAYPOINT_LISTS = new ArrayList<>();
    private static final List<Integer> LIST_POSITIONS = new ArrayList<>();
    private static double SCROLLED_POSITION = 0.0D;
    private final TextRenderer textRenderer;
    private final int itemHeight = 20;

    static {
        WaypointList list1 = WaypointList.build("test1");
        list1.add(new SimpleWaypoint("a", "a", new WaypointPos(0, 0, 0), 0 ,0, true));
        list1.add(new SimpleWaypoint("ab", "a", new WaypointPos(0, 0, 0), 1 ,0, true));
        list1.add(new SimpleWaypoint("abc", "a", new WaypointPos(0, 0, 0), 2 ,0, true));
        list1.add(new SimpleWaypoint("da", "d", new WaypointPos(0, 0, 0), 3 ,0, true));
        list1.add(new SimpleWaypoint("dab", "d", new WaypointPos(0, 0, 0), 4 ,0, true));
        list1.add(new SimpleWaypoint("dabc", "d", new WaypointPos(0, 0, 0), 5 ,0, true));
        list1.add(new SimpleWaypoint("da", "d", new WaypointPos(0, 0, 0), 3 ,0, true));
        list1.add(new SimpleWaypoint("dab", "d", new WaypointPos(0, 0, 0), 4 ,0, true));
        list1.add(new SimpleWaypoint("dabc", "d", new WaypointPos(0, 0, 0), 5 ,0, true));
        list1.add(new SimpleWaypoint("a", "a", new WaypointPos(0, 0, 0), 0 ,0, true));
        list1.add(new SimpleWaypoint("ab", "a", new WaypointPos(0, 0, 0), 1 ,0, true));
        list1.add(new SimpleWaypoint("abc", "a", new WaypointPos(0, 0, 0), 2 ,0, true));
        WaypointList list2 = WaypointList.build("test2");
        list2.add(new SimpleWaypoint("da", "d", new WaypointPos(0, 0, 0), 3 ,0, true));
        list2.add(new SimpleWaypoint("dab", "d", new WaypointPos(0, 0, 0), 4 ,0, true));
        list2.add(new SimpleWaypoint("dabc", "d", new WaypointPos(0, 0, 0), 5 ,0, true));
        list2.add(new SimpleWaypoint("a", "a", new WaypointPos(0, 0, 0), 0 ,0, true));
        list2.add(new SimpleWaypoint("ab", "a", new WaypointPos(0, 0, 0), 1 ,0, true));
        list2.add(new SimpleWaypoint("abc", "a", new WaypointPos(0, 0, 0), 2 ,0, true));
        list2.add(new SimpleWaypoint("a", "a", new WaypointPos(0, 0, 0), 0 ,0, true));
        list2.add(new SimpleWaypoint("ab", "a", new WaypointPos(0, 0, 0), 1 ,0, true));
        list2.add(new SimpleWaypoint("abc", "a", new WaypointPos(0, 0, 0), 2 ,0, true));
        list2.add(new SimpleWaypoint("da", "d", new WaypointPos(0, 0, 0), 3 ,0, true));
        list2.add(new SimpleWaypoint("dab", "d", new WaypointPos(0, 0, 0), 4 ,0, true));
        list2.add(new SimpleWaypoint("dabc", "d", new WaypointPos(0, 0, 0), 5 ,0, true));
        WAYPOINT_LISTS.add(list1);
        WAYPOINT_LISTS.add(list2);
        LIST_POSITIONS.add(0);
        LIST_POSITIONS.add(list1.size() + 1);
    }

    public NewWaypointListWidget(int x, int y, int width, int height, TextRenderer textRenderer) {
        super(x, y, width, height, Text.literal("Waypoints"));
        this.textRenderer = textRenderer;
        setScrollY(SCROLLED_POSITION);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean bl = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        SCROLLED_POSITION = getScrollY();
        return bl;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            LOGGER.info("pos: {}", pos);
            int index = binarySearch(LIST_POSITIONS, pos);
            if (index >= 0) {
                WaypointList waypointList = WAYPOINT_LISTS.get(index);
                int size = waypointList.size();
                if (waypointList.isExpand()) {
                    for (int i = index + 1; i < LIST_POSITIONS.size(); i++) {
                        LIST_POSITIONS.set(i, LIST_POSITIONS.get(i) - size);
                    }
                    waypointList.setExpand(false);
                } else {
                    for (int i = index + 1; i < LIST_POSITIONS.size(); i++) {
                        LIST_POSITIONS.set(i, LIST_POSITIONS.get(i) + size);
                    }
                    waypointList.setExpand(true);
                }
                SCROLLED_POSITION = getScrollY();
                refreshScroll();
                return true;
            } else {
                // listIndex = insertIndex - 1; insertIndex = -index - 1
                int listIndex = -index - 2;
                int waypointIndex = pos - LIST_POSITIONS.get(listIndex) - 1;
                SimpleWaypoint waypoint = WAYPOINT_LISTS.get(listIndex).simpleWaypoints().get(waypointIndex);
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
        WaypointList waypointList = WAYPOINT_LISTS.getLast();
        int lastSize = waypointList.isExpand() ? waypointList.size() + 1 : 1;
        return (LIST_POSITIONS.getLast() + lastSize) * itemHeight;
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

        if (mouseX < x2 && mouseX > x && mouseY < y + getContentsHeightWithPadding() && mouseY > y) {
            double scrollDistance = mouseY - y + getScrollY();
            int pos = (int) scrollDistance / itemHeight;
            int borderY1 = pos * itemHeight;
            context.drawBorder(0, borderY1, width, itemHeight, 0xFFFFFFFF);
        }

        int listY = 0;
        for (WaypointList waypointList : WAYPOINT_LISTS) {
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
