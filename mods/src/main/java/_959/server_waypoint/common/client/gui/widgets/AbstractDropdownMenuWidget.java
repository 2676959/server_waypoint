//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Expandable;
import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
public abstract class AbstractDropdownMenuWidget extends ShiftableClickableWidget implements Expandable {
    private final List<AbstractMenuItem> menuItems = new ArrayList<>();
    private final LayoutFlow.Orientation expansionOrientation;
    private final LayoutFlow.Direction expansionDirection;
    private final int itemSpacing;
    private boolean expanded;
    private int selectedMenuItemIndex = -1;
    private int highlightedItemIndex = -1;

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
        return this.countDisplayedMenuItems(selectedMenuItemIndex);
    }

    public final LayoutFlow.Orientation getExpansionOrientation() {
        return this.expansionOrientation;
    }

    public final LayoutFlow.Direction getExpansionDirection() {
        return this.expansionDirection;
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
            this.layoutMenuItems();
            this.expanded = true;
            this.setHighlightedItemIndex(-1);
            this.onExpandedChanged(true);
            return;
        }
        this.expanded = false;
        this.setHighlightedItemIndex(-1);
        this.onExpandedChanged(false);
        this.selectedMenuItemIndex = -1;
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
        for (int i = 0; i < this.menuItems.size(); i++) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (this.isMenuItemDisplayed(i) && contains(menuItem, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isActive() || button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
        for (int i = 0; i < this.menuItems.size(); i++) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (!this.isMenuItemDisplayed(i) || !contains(menuItem, mouseX, mouseY)) {
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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.isActive()) {
            return false;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            return this.closeMenuIfOpen();
        }
        int navigationStep = this.navigationStep(keyCode);
        if (this.expanded && navigationStep != 0) {
            return this.moveHighlight(navigationStep);
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER
                || keyCode == GLFW.GLFW_KEY_KP_ENTER
                || keyCode == GLFW.GLFW_KEY_SPACE) {
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
        if (!this.expanded) {
            return;
        }
        nextLayer(context);
        try {
            for (int i = 0; i < this.menuItems.size(); i++) {
                AbstractMenuItem menuItem = this.menuItems.get(i);
                if (!this.isMenuItemDisplayed(i)) {
                    continue;
                }
                menuItem.
                //$ render_method_swap
                extractRenderState
                        (context, mouseX, mouseY, deltaTicks);
            }
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
            positiveKey = GLFW.GLFW_KEY_RIGHT;
            negativeKey = GLFW.GLFW_KEY_LEFT;
        } else {
            positiveKey = GLFW.GLFW_KEY_DOWN;
            negativeKey = GLFW.GLFW_KEY_UP;
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
        }
    }

    private void layoutHorizontalMenuItems() {
        int cursor = this.expansionDirection == LayoutFlow.Direction.FORWARD
                ? this.getX() + this.getWidth()
                : this.getX();
        for (int i = 0; i < this.menuItems.size(); i++) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (!this.isMenuItemDisplayed(i)) {
                continue;
            }
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
        for (int i = 0; i < this.menuItems.size(); i++) {
            AbstractMenuItem menuItem = this.menuItems.get(i);
            if (!this.isMenuItemDisplayed(i)) {
                continue;
            }
            int itemY;
            if (this.expansionDirection == LayoutFlow.Direction.FORWARD) {
                cursor += this.itemSpacing;
                itemY = cursor;
                cursor += menuItem.getHeight();
            } else {
                cursor -= this.itemSpacing + menuItem.getHeight();
                itemY = cursor;
            }
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
