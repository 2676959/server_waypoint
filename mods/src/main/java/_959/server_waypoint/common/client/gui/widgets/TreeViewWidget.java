//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.Expandable;
import _959.server_waypoint.common.client.gui.Padding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.TRANSPARENT_BG_COLOR;

public abstract class TreeViewWidget<T> extends ShiftableScrollableWidget implements Padding, Expandable {
    private final int rowHeight;
    private final PaddingBackground paddingBackground;
    private List<T> roots = List.of();
    private final List<TreeEntry<T>> visibleEntries = new ArrayList<>();
    private int contentHeight;
    private int hoveredRow = -1;

    protected TreeViewWidget(int x, int y, int width, int height, int rowHeight, Component text) {
        this(x, y, width, height, rowHeight, text, 0, 0, 0, 0, TRANSPARENT_BG_COLOR, TRANSPARENT_BG_COLOR, false);
    }

    protected TreeViewWidget(int x, int y, int width, int height, int rowHeight, Component text,
                             int topPadding, int bottomPadding, int leftPadding, int rightPadding,
                             int backgroundColor, int borderColor, boolean border) {
        super(x, y, width, height, text);
        this.rowHeight = rowHeight;
        this.paddingBackground = new PaddingBackground(this, topPadding, bottomPadding, leftPadding, rightPadding, backgroundColor, borderColor, border);
    }

    public void updateRoots(List<T> roots) {
        this.roots = List.copyOf(roots);
        refreshTreeData();
    }

    public void refreshTreeData() {
        this.visibleEntries.clear();
        for (T root : this.roots) {
            addVisibleEntry(root, 0);
        }
        this.contentHeight = this.visibleEntries.size() * this.rowHeight;
        setScrollY(getScrollY());
        notifyScrollChanged();
    }

    private void addVisibleEntry(T value, int depth) {
        int row = this.visibleEntries.size();
        this.visibleEntries.add(new TreeEntry<>(value, depth, row));
        if (!isExpandable(value) || !isExpanded(value)) {
            return;
        }
        for (T child : getChildren(value)) {
            addVisibleEntry(child, depth + 1);
        }
    }

    protected abstract @NotNull List<T> getChildren(T value);

    protected boolean isExpandable(T value) {
        return !getChildren(value).isEmpty();
    }

    protected abstract boolean isExpanded(T value);

    protected abstract void setExpanded(T value, boolean expanded);

    protected abstract void renderEmpty(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks);

    protected abstract void renderEntry(GuiGraphicsExtractor context, TreeEntry<T> entry, boolean hovered, int rowY, int contentWidth, int mouseX, int mouseY, float deltaTicks);

    protected void beforeRenderEntries(GuiGraphicsExtractor context, int contentWidth, int mouseX, int mouseY, float deltaTicks) {
    }

    protected boolean onEntryClicked(TreeEntry<T> entry, double contentMouseX, double contentMouseY, int button) {
        return false;
    }

    protected void onHoveredEntryChanged(@Nullable TreeEntry<T> oldEntry, @Nullable TreeEntry<T> newEntry) {
    }

    protected void onScrollChanged(double scrollY) {
    }

    protected int getRowHeight() {
        return this.rowHeight;
    }

    protected int visibleEntryCount() {
        return this.visibleEntries.size();
    }

    protected TreeEntry<T> getVisibleEntry(int index) {
        return this.visibleEntries.get(index);
    }

    protected @Nullable TreeEntry<T> getHoveredEntry() {
        return getEntryAtRow(this.hoveredRow);
    }

    protected int getHoveredRow() {
        return this.hoveredRow;
    }

    protected int getContentWidth() {
        return overflows() ? this.width - SCROLLBAR_WIDTH : this.width;
    }

    protected boolean isEmpty() {
        return this.visibleEntries.isEmpty();
    }

    protected void toggleEntryExpanded(TreeEntry<T> entry) {
        T value = entry.value();
        if (!isExpandable(value)) {
            return;
        }
        setExpanded(value, !isExpanded(value));
        refreshTreeData();
    }

    private @Nullable TreeEntry<T> getEntryAtRow(int row) {
        if (row < 0 || row >= this.visibleEntries.size()) {
            return null;
        }
        return this.visibleEntries.get(row);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        if (handled) {
            notifyScrollChanged();
        }
        return handled;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isEmpty()) {
            return false;
        }
        if (overflows() && this.checkScrollbarDragged(mouseX, mouseY, button)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int x = getX();
        int y = getY();
        if (mouseX <= x || mouseX >= x + this.width || mouseY <= y || mouseY >= y + this.height) {
            return false;
        }
        int row = (int)Math.floor((mouseY - y + getScrollY()) / this.rowHeight);
        TreeEntry<T> entry = getEntryAtRow(row);
        if (entry == null) {
            return false;
        }
        double contentMouseX = mouseX - x;
        double contentMouseY = mouseY - y + getScrollY();
        if (onEntryClicked(entry, contentMouseX, contentMouseY, button)) {
            return true;
        }
        if (button == 0 && isExpandable(entry.value())) {
            toggleEntryExpanded(entry);
            return true;
        }
        return false;
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        notifyScrollChanged();
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
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getX();
        int y = getY();

        this.paddingBackground.
        //$ render_method_swap
        extractRenderState
                (context, mouseX, mouseY, deltaTicks);

        context.enableScissor(x, y, x + width, y + height);

        push(context);
        translate(context, x, y);

        int contentWidth = getContentWidth();
        if (isEmpty()) {
            updateHoveredRow(-1);
            renderEmpty(context, mouseX, mouseY, deltaTicks);
            pop(context);
            context.disableScissor();
            this.drawScrollbar(context);
            return;
        }

        double scrollY = getScrollY();
        updateHoveredRow(getRowAt(mouseX, mouseY, contentWidth, scrollY));
        beforeRenderEntries(context, contentWidth, mouseX, mouseY, deltaTicks);

        translate(context, 0.0F, (float)-scrollY);

        VisibleRowRange renderRange = getVisibleRenderRange(scrollY);
        for (int row = renderRange.startRow(); row < renderRange.endRow(); row++) {
            TreeEntry<T> entry = this.visibleEntries.get(row);
            int rowY = entry.row() * this.rowHeight;
            renderEntry(context, entry, this.hoveredRow == entry.row(), rowY, contentWidth, mouseX, mouseY, deltaTicks);
        }

        pop(context);
        context.disableScissor();
        this.drawScrollbar(context);
    }

    protected VisibleRowRange getVisibleRenderRange(double scrollY) {
        if (this.visibleEntries.isEmpty()) {
            return new VisibleRowRange(0, 0);
        }

        double viewportTop = Math.max(0.0D, scrollY);
        double viewportBottom = Math.min(this.contentHeight, viewportTop + Math.max(0, this.height));
        if (viewportBottom <= viewportTop) {
            int clampedStart = Math.min(this.visibleEntries.size(), (int)(viewportTop / this.rowHeight));
            return new VisibleRowRange(clampedStart, clampedStart);
        }

        int startRow = (int)(viewportTop / this.rowHeight);
        int endRow = (int)Math.ceil(viewportBottom / this.rowHeight);
        startRow = Math.min(startRow, this.visibleEntries.size());
        endRow = Math.min(Math.max(endRow, startRow), this.visibleEntries.size());
        return new VisibleRowRange(startRow, endRow);
    }

    private int getRowAt(int mouseX, int mouseY, int contentWidth, double scrollY) {
        int x = getX();
        int y = getY();
        if (mouseX <= x || mouseX >= x + contentWidth || mouseY <= y || mouseY >= y + this.height) {
            return -1;
        }
        int row = (int)((mouseY - y + scrollY) / this.rowHeight);
        return row < this.visibleEntries.size() ? row : -1;
    }

    private void updateHoveredRow(int newHoveredRow) {
        if (this.hoveredRow == newHoveredRow) {
            return;
        }
        TreeEntry<T> oldEntry = getEntryAtRow(this.hoveredRow);
        this.hoveredRow = newHoveredRow;
        onHoveredEntryChanged(oldEntry, getEntryAtRow(newHoveredRow));
    }

    private void notifyScrollChanged() {
        onScrollChanged(getScrollY());
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        setScrollY(getScrollY());
    }

    @Override
    public void setVisualHeight(int height) {
        setHeight(height - this.paddingBackground.getPaddedHeight());
    }

    @Override
    public void setVisualWidth(int width) {
        setWidth(width - this.paddingBackground.getPaddedWidth());
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

    public record TreeEntry<T>(T value, int depth, int row) {
    }

    protected record VisibleRowRange(int startRow, int endRow) {
    }
}
