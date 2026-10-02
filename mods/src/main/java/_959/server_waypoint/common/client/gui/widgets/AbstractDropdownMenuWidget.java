//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.api.PopupOwner;
import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.nextLayer;
import static _959.server_waypoint.common.client.gui.render.DrawContextHelper.previousLayer;

/**
 * Base for a clickable control that expands a sequence of custom-rendered menu choices.
 *
 * <p>The expansion axis and direction use {@link LayoutFlow}: horizontal/forward expands right,
 * horizontal/reverse expands left, vertical/forward expands down, and vertical/reverse expands up.
 * The dropdown owns item rendering and click routing, so the owning screen registers only this
 * widget.
 */
public abstract class AbstractDropdownMenuWidget extends ShiftableClickableWidget implements Expandable, PopupOwner {
    private final List<AbstractMenuItem> menuItems = new ArrayList<>();
    private final LayoutFlow.Orientation expansionOrientation;
    private LayoutFlow.Direction expansionDirection;
    private final int itemSpacing;
    private boolean expanded;
    private boolean renderPopupSeparately;
    private int selectedMenuItemIndex = -1;
    private int highlightedItemIndex = -1;
    private int maxPopupHeight = Integer.MAX_VALUE;
    private int scrollOffset;
    private boolean draggingScrollIndicator;

    protected AbstractDropdownMenuWidget(
            int x,
            int y,
            int width,
            int height,
            Component message,
            LayoutFlow.Orientation expansionOrientation,
            LayoutFlow.Direction expansionDirection
    ) {
        this(x, y, width, height, message, expansionOrientation, expansionDirection, 0);
    }

    protected AbstractDropdownMenuWidget(
            int x,
            int y,
            int width,
            int height,
            Component message,
            LayoutFlow.Orientation expansionOrientation,
            LayoutFlow.Direction expansionDirection,
            int itemSpacing
    ) {
        super(x, y, width, height, message);
        this.expansionOrientation = Objects.requireNonNull(expansionOrientation);
        this.expansionDirection = Objects.requireNonNull(expansionDirection);
        if (itemSpacing < 0) {
            throw new IllegalArgumentException("itemSpacing must be non-negative");
        }
        this.itemSpacing = itemSpacing;
        this.setPosition(x, y);
    }

    /**
     * Adds an item in logical menu order and returns it for optional caller configuration.
     */
    protected final <T extends AbstractMenuItem> T addMenuItem(T menuItem) {
        this.menuItems.add(Objects.requireNonNull(menuItem));
        this.layoutMenuItems();
        return menuItem;
    }

    /** Closes the popup and removes every menu item so a subclass can rebuild its choices. */
    protected final void clearMenuItems() {
        this.setExpanded(false);
        this.setHighlightedItemIndex(-1);
        this.menuItems.clear();
        this.selectedMenuItemIndex = -1;
        this.scrollOffset = 0;
    }

    /** Rebuilds the choices and lays them out once. */
    protected final void replaceMenuItems(List<? extends AbstractMenuItem> items) {
        this.clearMenuItems();
        this.menuItems.addAll(items);
        this.layoutMenuItems();
    }

    public final List<AbstractMenuItem> getMenuItems() {
        return List.copyOf(this.menuItems);
    }

    /**
     * Returns the number of visible choices that the expanded popup will show.
     */
    public final int getPopupItemCount() {
        int selectedMenuItemIndex = this.expanded
                ? this.selectedMenuItemIndex
                : this.resolveSelectedMenuItemIndex();
        return this.visibleMenuItemIndexes(selectedMenuItemIndex).size();
    }

    /** Caps the popup's vertical extent; wheel and arrow keys can reach the remaining choices. */
    public final void setMaxPopupHeight(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("Popup height must be positive");
        }
        if (this.maxPopupHeight != height) {
            this.maxPopupHeight = height;
            this.scrollOffset = Math.min(this.scrollOffset, this.maxScrollOffset(this.selectedMenuItemIndex));
            this.layoutMenuItems();
        }
    }

    public final LayoutFlow.Orientation getExpansionOrientation() {
        return this.expansionOrientation;
    }

    public final LayoutFlow.Direction getExpansionDirection() {
        return this.expansionDirection;
    }

    /** Changes which side of the control receives the popup. */
    public final void setExpansionDirection(LayoutFlow.Direction direction) {
        LayoutFlow.Direction resolved = Objects.requireNonNull(direction);
        if (this.expansionDirection != resolved) {
            this.expansionDirection = resolved;
            this.layoutMenuItems();
        }
    }

    public final int getItemSpacing() {
        return this.itemSpacing;
    }

    public final boolean isExpanded() {
        return this.expanded;
    }

    public final int getHighlightedItemIndex() {
        return this.highlightedItemIndex;
    }

    public final void setExpanded(boolean expanded) {
        int selectedMenuItemIndex = expanded ? this.resolveSelectedMenuItemIndex() : -1;
        boolean resolvedExpanded = expanded
                && this.countDisplayedMenuItems(selectedMenuItemIndex) > 0;
        if (this.expanded == resolvedExpanded) {
            return;
        }
        if (resolvedExpanded) {
            this.selectedMenuItemIndex = selectedMenuItemIndex;
            this.scrollOffset = 0;
            this.layoutMenuItems();
            this.expanded = true;
            this.setHighlightedItemIndex(-1);
            this.onExpandedChanged(true);
            return;
        }
        this.expanded = false;
        this.draggingScrollIndicator = false;
        this.setHighlightedItemIndex(-1);
        this.onExpandedChanged(false);
        this.selectedMenuItemIndex = -1;
        this.scrollOffset = 0;
        this.layoutMenuItems();
    }

    public final void toggleMenu() {
        this.setExpanded(!this.expanded);
    }

    public final boolean closeMenuIfOpen() {
        if (!this.expanded) {
            return false;
        }
        this.setExpanded(false);
        return true;
    }

    public final boolean closeMenuIfOutside(double mouseX, double mouseY) {
        if (!this.expanded || this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.setExpanded(false);
        return true;
    }

    @Override
    public boolean closePopupIfOpen() {
        return this.closeMenuIfOpen();
    }

    /** An open menu always belongs to the focused control, where Escape can reach it. */
    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused) {
            this.closeMenuIfOpen();
        }
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.layoutMenuItems();
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.layoutMenuItems();
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        this.layoutMenuItems();
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        this.layoutMenuItems();
    }

    @Override
    public void setWidth(int width) {
        this.width = width;
        this.layoutMenuItems();
    }

    @Override
    public void setHeight(int height) {
        this.height = height;
        this.layoutMenuItems();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.isActive()) {
            return false;
        }
        if (contains(this, mouseX, mouseY)) {
            return true;
        }
        if (!this.expanded) {
            return false;
        }
        if (this.isOverScrollIndicator(mouseX, mouseY)) {
            return true;
        }
        for (int i : this.visibleMenuItemIndexes(this.selectedMenuItemIndex)) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (contains(menuItem, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.draggingScrollIndicator || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        this.scrollIndicatorTo(mouseY);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT && this.draggingScrollIndicator) {
            this.draggingScrollIndicator = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isActive() || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (contains(this, mouseX, mouseY)) {
            this.playClickSound();
            this.toggleMenu();
            return true;
        }
        if (!this.expanded) {
            return false;
        }
        if (this.isOverScrollIndicator(mouseX, mouseY)) {
            this.draggingScrollIndicator = true;
            this.scrollIndicatorTo(mouseY);
            return true;
        }
        for (int i : this.visibleMenuItemIndexes(this.selectedMenuItemIndex)) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (!contains(menuItem, mouseX, mouseY)) {
                continue;
            }
            if (menuItem.isActive()) {
                this.activateMenuItem(menuItem, mouseX, mouseY);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.expanded || !this.isMouseOver(mouseX, mouseY) || verticalAmount == 0) {
            return false;
        }
        int offset = (int) Math.max(1, Math.ceil(Math.abs(verticalAmount)));
        int nextOffset = Math.max(0, Math.min(this.maxScrollOffset(this.selectedMenuItemIndex),
                this.scrollOffset + (verticalAmount < 0 ? offset : -offset)));
        if (nextOffset != this.scrollOffset) {
            this.scrollOffset = nextOffset;
            this.layoutMenuItems();
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isActive()) {
            return false;
        }
        if (keyCode == InputConstants.KEY_ESCAPE) {
            return this.closeMenuIfOpen();
        }
        int navigationStep = this.navigationStep(keyCode);
        if (this.expanded && navigationStep != 0) {
            return this.moveHighlight(navigationStep);
        }
        if (keyCode == InputConstants.KEY_RETURN
                || keyCode == InputConstants.KEY_NUMPADENTER) {
            if (!this.expanded) {
                this.playClickSound();
                this.setExpanded(true);
                if (this.expanded) {
                    this.setHighlightedItemIndex(this.findInitialHighlightedItem());
                }
                return true;
            }
            if (this.highlightedItemIndex < 0) {
                return false;
            }
            AbstractMenuItem highlightedItem = this.menuItems.get(this.highlightedItemIndex);
            return this.activateMenuItem(
                    highlightedItem,
                    highlightedItem.getX() + highlightedItem.getWidth() / 2.0,
                    highlightedItem.getY() + highlightedItem.getHeight() / 2.0
            );
        }
        return false;
    }

    @Override
    public final void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.renderDropdownControl(context, mouseX, mouseY, deltaTicks);
        if (!this.renderPopupSeparately) {
            this.renderPopup(context, mouseX, mouseY, deltaTicks);
        }
    }

    /** Lets an owning screen draw the popup after its other controls. */
    public final void setRenderPopupSeparately(boolean separately) {
        this.renderPopupSeparately = separately;
    }

    public void renderPopup(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (!this.expanded || !this.visible || !this.active) {
            return;
        }
        nextLayer(context);
        try {
            for (int i : this.visibleMenuItemIndexes(this.selectedMenuItemIndex)) {
                AbstractMenuItem menuItem = this.menuItems.get(i);
                menuItem.
                //$ render_method_swap
                extractRenderState
                        (context, mouseX, mouseY, deltaTicks);
            }
            this.renderScrollIndicator(context);
        } finally {
            previousLayer(context);
        }
    }

    protected abstract void renderDropdownControl(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float deltaTicks
    );

    protected void onExpandedChanged(boolean expanded) {
    }

    /**
     * Returns the logical index of the item represented by the collapsed control, or {@code -1}
     * when the dropdown has no selected item. A valid selected item is omitted from the popup.
     */
    protected int getSelectedMenuItemIndex() {
        return -1;
    }

    /**
     * Returns the logical item index where focus should start when the menu is opened from the
     * keyboard. Hidden, inactive, and selected items are skipped.
     */
    protected int getInitialHighlightedItemIndex() {
        return 0;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
        this.defaultButtonNarrationText(builder);
    }

    private void layoutMenuItems() {
        if (this.expansionOrientation == LayoutFlow.Orientation.HORIZONTAL) {
            this.layoutHorizontalMenuItems();
        } else {
            this.layoutVerticalMenuItems();
        }
        if (this.expanded && this.highlightedItemIndex >= 0
                && !this.visibleMenuItemIndexes(this.selectedMenuItemIndex).contains(this.highlightedItemIndex)) {
            this.setHighlightedItemIndex(-1);
        }
    }

    private boolean activateMenuItem(AbstractMenuItem menuItem, double mouseX, double mouseY) {
        if (!menuItem.isActive()) {
            return false;
        }
        this.playClickSound();
        menuItem.onClick(mouseX, mouseY);
        this.setExpanded(false);
        return true;
    }

    private void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            this.playDownSound(minecraft.getSoundManager());
        }
    }

    private int navigationStep(int keyCode) {
        int positiveKey;
        int negativeKey;
        if (this.expansionOrientation == LayoutFlow.Orientation.HORIZONTAL) {
            positiveKey = InputConstants.KEY_RIGHT;
            negativeKey = InputConstants.KEY_LEFT;
        } else {
            positiveKey = InputConstants.KEY_DOWN;
            negativeKey = InputConstants.KEY_UP;
            if (keyCode == positiveKey) {
                return 1;
            }
            if (keyCode == negativeKey) {
                return -1;
            }
            return 0;
        }
        int directionMultiplier = this.expansionDirection == LayoutFlow.Direction.FORWARD ? 1 : -1;
        if (keyCode == positiveKey) {
            return directionMultiplier;
        }
        if (keyCode == negativeKey) {
            return -directionMultiplier;
        }
        return 0;
    }

    private boolean moveHighlight(int step) {
        if (this.menuItems.isEmpty()) {
            return false;
        }
        int startIndex = this.highlightedItemIndex < 0
                ? (step > 0 ? 0 : this.menuItems.size() - 1)
                : Math.floorMod(this.highlightedItemIndex + step, this.menuItems.size());
        int nextIndex = this.findSelectableItem(startIndex, step);
        if (nextIndex < 0) {
            return false;
        }
        this.setHighlightedItemIndex(nextIndex);
        return true;
    }

    private int findSelectableItem(int startIndex, int step) {
        if (this.menuItems.isEmpty()) {
            return -1;
        }
        for (int offset = 0; offset < this.menuItems.size(); offset++) {
            int index = Math.floorMod(startIndex + offset * step, this.menuItems.size());
            AbstractMenuItem menuItem = this.menuItems.get(index);
            if (this.isMenuItemDisplayed(index) && menuItem.active) {
                return index;
            }
        }
        return -1;
    }

    private int findInitialHighlightedItem() {
        int initialIndex = this.getInitialHighlightedItemIndex();
        if (initialIndex < 0 || initialIndex >= this.menuItems.size()) {
            initialIndex = 0;
        }
        return this.findSelectableItem(initialIndex, 1);
    }

    private void setHighlightedItemIndex(int highlightedItemIndex) {
        if (this.highlightedItemIndex >= 0 && this.highlightedItemIndex < this.menuItems.size()) {
            this.menuItems.get(this.highlightedItemIndex).setFocused(false);
        }
        this.highlightedItemIndex = highlightedItemIndex;
        if (this.highlightedItemIndex >= 0) {
            this.menuItems.get(this.highlightedItemIndex).setFocused(true);
            this.scrollToItem(this.highlightedItemIndex);
        }
    }

    private void scrollToItem(int itemIndex) {
        if (!this.expanded || this.expansionOrientation != LayoutFlow.Orientation.VERTICAL) {
            return;
        }
        int ordinal = this.displayedMenuItemIndexes(this.selectedMenuItemIndex).indexOf(itemIndex);
        if (ordinal < 0) {
            return;
        }
        if (ordinal < this.scrollOffset) {
            this.scrollOffset = ordinal;
        }
        while (!this.visibleMenuItemIndexes(this.selectedMenuItemIndex).contains(itemIndex)) {
            this.scrollOffset++;
        }
        this.layoutMenuItems();
    }

    private void layoutHorizontalMenuItems() {
        int cursor = this.expansionDirection == LayoutFlow.Direction.FORWARD
                ? this.getX() + this.getWidth()
                : this.getX();
        for (int i : this.visibleMenuItemIndexes(this.selectedMenuItemIndex)) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            int itemX;
            if (this.expansionDirection == LayoutFlow.Direction.FORWARD) {
                cursor += this.itemSpacing;
                itemX = cursor;
                cursor += menuItem.getWidth();
            } else {
                cursor -= this.itemSpacing + menuItem.getWidth();
                itemX = cursor;
            }
            int itemY = this.getY() + (this.getHeight() - menuItem.getHeight()) / 2;
            menuItem.setPosition(itemX, itemY);
        }
    }

    private void layoutVerticalMenuItems() {
        int cursor = this.expansionDirection == LayoutFlow.Direction.FORWARD
                ? this.getY() + this.getHeight()
                : this.getY();
        List<Integer> visibleIndexes = this.visibleMenuItemIndexes(this.selectedMenuItemIndex);
        if (this.expansionDirection == LayoutFlow.Direction.REVERSE) {
            for (int position = visibleIndexes.size() - 1; position >= 0; position--) {
                AbstractMenuItem menuItem = this.menuItems.get(visibleIndexes.get(position));
                cursor -= this.itemSpacing + menuItem.getHeight();
                int itemX = this.getX() + (this.getWidth() - menuItem.getWidth()) / 2;
                menuItem.setPosition(itemX, cursor);
            }
            return;
        }
        for (int i : visibleIndexes) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            cursor += this.itemSpacing;
            int itemY = cursor;
            cursor += menuItem.getHeight();
            int itemX = this.getX() + (this.getWidth() - menuItem.getWidth()) / 2;
            menuItem.setPosition(itemX, itemY);
        }
    }

    private int resolveSelectedMenuItemIndex() {
        int selectedMenuItemIndex = this.getSelectedMenuItemIndex();
        return selectedMenuItemIndex >= 0 && selectedMenuItemIndex < this.menuItems.size()
                ? selectedMenuItemIndex
                : -1;
    }

    private int countDisplayedMenuItems(int selectedMenuItemIndex) {
        int count = 0;
        for (int i = 0; i < this.menuItems.size(); i++) {
            if (i != selectedMenuItemIndex && this.menuItems.get(i).visible) {
                count++;
            }
        }
        return count;
    }

    private boolean isMenuItemDisplayed(int itemIndex) {
        return itemIndex != this.selectedMenuItemIndex && this.menuItems.get(itemIndex).visible;
    }

    private List<Integer> displayedMenuItemIndexes(int selectedIndex) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < this.menuItems.size(); i++) {
            if (i != selectedIndex && this.menuItems.get(i).visible) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private int maxScrollOffset(int selectedIndex) {
        if (this.expansionOrientation != LayoutFlow.Orientation.VERTICAL) {
            return 0;
        }
        List<Integer> indexes = this.displayedMenuItemIndexes(selectedIndex);
        int height = 0;
        int visible = 0;
        for (int i = indexes.size() - 1; i >= 0; i--) {
            int next = this.menuItems.get(indexes.get(i)).getHeight() + (visible == 0 ? 0 : this.itemSpacing);
            if (visible > 0 && height + next > this.maxPopupHeight) {
                break;
            }
            height += next;
            visible++;
        }
        return indexes.size() - visible;
    }

    private List<Integer> visibleMenuItemIndexes(int selectedIndex) {
        List<Integer> indexes = this.displayedMenuItemIndexes(selectedIndex);
        if (this.expansionOrientation != LayoutFlow.Orientation.VERTICAL) {
            return indexes;
        }
        int from = Math.min(this.scrollOffset, this.maxScrollOffset(selectedIndex));
        int height = 0;
        int to = from;
        while (to < indexes.size()) {
            int next = this.menuItems.get(indexes.get(to)).getHeight() + (to == from ? 0 : this.itemSpacing);
            if (to > from && height + next > this.maxPopupHeight) {
                break;
            }
            height += next;
            to++;
        }
        return indexes.subList(from, to);
    }

    private void renderScrollIndicator(GuiGraphicsExtractor context) {
        if (this.expansionOrientation != LayoutFlow.Orientation.VERTICAL) {
            return;
        }
        List<Integer> visible = this.visibleMenuItemIndexes(this.selectedMenuItemIndex);
        int total = this.countDisplayedMenuItems(this.selectedMenuItemIndex);
        if (visible.isEmpty() || visible.size() == total) {
            return;
        }
        AbstractMenuItem first = this.menuItems.get(visible.get(0));
        AbstractMenuItem last = this.menuItems.get(visible.get(visible.size() - 1));
        int top = Math.min(first.getY(), last.getY());
        int bottom = Math.max(first.getY() + first.getHeight(), last.getY() + last.getHeight());
        int trackHeight = bottom - top;
        int thumbHeight = Math.min(trackHeight, Math.max(4, trackHeight * visible.size() / total));
        int maxOffset = this.maxScrollOffset(this.selectedMenuItemIndex);
        int thumbY = top + (trackHeight - thumbHeight) * this.scrollOffset / Math.max(1, maxOffset);
        int right = this.getX() + this.getWidth() - 1;
        context.fill(right - 3, top, right, bottom,
                WidgetThemeManager.getColor(WidgetThemeVariable.SCROLLBAR_TRACK));
        context.fill(right - 3, thumbY, right, thumbY + thumbHeight,
                WidgetThemeManager.getColor(WidgetThemeVariable.SCROLLBAR_THUMB));
    }

    private boolean isOverScrollIndicator(double mouseX, double mouseY) {
        if (this.expansionOrientation != LayoutFlow.Orientation.VERTICAL
                || this.countDisplayedMenuItems(this.selectedMenuItemIndex) <= this.getPopupItemCount()) {
            return false;
        }
        List<Integer> visible = this.visibleMenuItemIndexes(this.selectedMenuItemIndex);
        if (visible.isEmpty()) {
            return false;
        }
        AbstractMenuItem first = this.menuItems.get(visible.get(0));
        AbstractMenuItem last = this.menuItems.get(visible.get(visible.size() - 1));
        int top = Math.min(first.getY(), last.getY());
        int bottom = Math.max(first.getY() + first.getHeight(), last.getY() + last.getHeight());
        return mouseX >= this.getX() + this.getWidth() - 4
                && mouseX < this.getX() + this.getWidth()
                && mouseY >= top && mouseY < bottom;
    }

    private void scrollIndicatorTo(double mouseY) {
        List<Integer> visible = this.visibleMenuItemIndexes(this.selectedMenuItemIndex);
        if (visible.isEmpty()) {
            return;
        }
        AbstractMenuItem first = this.menuItems.get(visible.get(0));
        AbstractMenuItem last = this.menuItems.get(visible.get(visible.size() - 1));
        int top = Math.min(first.getY(), last.getY());
        int bottom = Math.max(first.getY() + first.getHeight(), last.getY() + last.getHeight());
        double fraction = Math.max(0, Math.min(1, (mouseY - top) / Math.max(1, bottom - top)));
        this.scrollOffset = (int) Math.round(fraction * this.maxScrollOffset(this.selectedMenuItemIndex));
        this.layoutMenuItems();
    }

    private static boolean contains(
            ShiftableClickableWidget widget,
            double mouseX,
            double mouseY
    ) {
        return mouseX >= widget.getX()
                && mouseY >= widget.getY()
                && mouseX < widget.getX() + widget.getWidth()
                && mouseY < widget.getY() + widget.getHeight();
    }

    /**
     * Base menu item whose renderer may draw text, an icon, or any other content.
     */
    public abstract static class AbstractMenuItem extends ShiftableClickableWidget {
        protected AbstractMenuItem(int width, int height, Component message) {
            super(0, 0, width, height, message);
            this.setPosition(0, 0);
        }

        @Override
        public final void onClick(double mouseX, double mouseY) {
            this.onSelected();
        }

        protected abstract void onSelected();

        @Override
        public final void
        //$ render_widget_method_swap
        extractWidgetRenderState
                (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            this.renderMenuItem(context, mouseX, mouseY, deltaTicks);
        }

        protected abstract void renderMenuItem(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        );

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            this.defaultButtonNarrationText(builder);
        }
    }
}
