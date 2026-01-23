package _959.server_waypoint.common.client.gui.layout;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import net.minecraft.client.gui.widget.Widget;

import java.util.ArrayList;
import java.util.List;

public class ExpandableManager implements Expandable {
    private int width;
    private int height;
    private final List<Entry> children = new ArrayList<>();
    private int totalWidthRatio = 0;
    private int totalHeightRatio = 0;
    private int totalFixedWidth = 0;
    private int totalFixedHeight = 0;

    public ExpandableManager() {
    }

    public ExpandableManager(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public <T extends Widget & Expandable & Padding> void addChild(T child, int widthRatio, int heightRatio) {
        this.children.add(new Entry(child, child, widthRatio, heightRatio));
        if (widthRatio > 0) {
            this.totalWidthRatio += widthRatio;
        } else {
            this.totalFixedWidth += child.getVisualWidth();
        }

        if (heightRatio > 0) {
            this.totalHeightRatio += heightRatio;
        } else {
            this.totalFixedHeight += child.getVisualHeight();
        }

        this.updateChildren();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        this.updateWidths();
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.updateHeights();
    }

    private void updateChildren() {
        updateWidths();
        updateHeights();
    }

    private void updateWidths() {
        if (children.isEmpty()) return;

        int availableSpace = width - totalFixedWidth;
        if (availableSpace < 0) availableSpace = 0;

        int currentRatioSum = 0;
        int allocatedSpace = 0;

        for (Entry entry : children) {
            if (entry.widthRatio > 0) {
                currentRatioSum += entry.widthRatio;
                int targetSpace = totalWidthRatio > 0 ? (int) ((long) availableSpace * currentRatioSum / totalWidthRatio) : 0;
                int size = targetSpace - allocatedSpace;
                allocatedSpace += size;
                entry.expandable.setVisualWidth(size);
            }
        }
    }

    private void updateHeights() {
        if (children.isEmpty()) return;

        int availableSpace = height - totalFixedHeight;
        if (availableSpace < 0) availableSpace = 0;

        int currentRatioSum = 0;
        int allocatedSpace = 0;

        for (Entry entry : children) {
            if (entry.heightRatio > 0) {
                currentRatioSum += entry.heightRatio;
                int targetSpace = totalHeightRatio > 0 ? (int) ((long) availableSpace * currentRatioSum / totalHeightRatio) : 0;
                int size = targetSpace - allocatedSpace;
                allocatedSpace += size;
                entry.expandable.setVisualHeight(size);
            }
        }
    }

    private record Entry(Widget widget, Expandable expandable, int widthRatio, int heightRatio) {
    }
}
