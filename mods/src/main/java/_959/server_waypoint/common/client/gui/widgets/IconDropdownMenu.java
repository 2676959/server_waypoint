//~ gui_graphics_26
//~ resource_location_import
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.LayoutFlow;
import _959.server_waypoint.common.client.gui.render.IconControlRenderer;
import _959.server_waypoint.common.client.gui.render.WidgetTextures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** An icon selector with 16-pixel choices that expand to the left. */
public final class IconDropdownMenu extends AbstractDropdownMenuWidget {
    private static final int CONTROL_BUTTON_SIZE = 16;
    private static final int DROPDOWN_ITEM_GAP = 2;
    private final List<IconMenuItem> iconItems = new ArrayList<>();
    private int selectedIndex;
    private int popupXOffset;
    private int appliedPopupXOffset;
    private int appliedPopupYOffset;

    public IconDropdownMenu(Component message) {
        super(
                0,
                0,
                CONTROL_BUTTON_SIZE,
                CONTROL_BUTTON_SIZE,
                message,
                LayoutFlow.Orientation.HORIZONTAL,
                LayoutFlow.Direction.REVERSE,
                DROPDOWN_ITEM_GAP
        );
        this.setTooltip(message);
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.setTooltip(message);
    }

    public AbstractMenuItem addIconItem(
            Component message,
            //$ resource_location_type_swap
            Identifier
            icon,
            Runnable callback
    ) {
        IconMenuItem menuItem = this.addMenuItem(new IconMenuItem(message, icon, callback));
        this.iconItems.add(menuItem);
        return menuItem;
    }

    public void setSelectedIndex(int selectedIndex) {
        this.selectedIndex = selectedIndex;
        for (int i = 0; i < this.iconItems.size(); i++) {
            this.iconItems.get(i).selected = i == selectedIndex;
        }
    }

    public void setPopupXOffset(int popupXOffset) {
        this.popupXOffset = Math.max(0, popupXOffset);
    }

    @Override
    protected void renderDropdownControl(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float deltaTicks
    ) {
        IconControlRenderer.render(context, this, this.getSelectedIcon(), false, this.isExpanded(), false);
    }

    private
    //$ resource_location_type_swap
    Identifier
    getSelectedIcon() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.iconItems.size()
                ? this.iconItems.get(this.selectedIndex).icon
                : WidgetTextures.PLACEHOLDER_ICON;
    }

    @Override
    protected int getSelectedMenuItemIndex() {
        return this.selectedIndex;
    }

    @Override
    protected void onExpandedChanged(boolean expanded) {
        this.removeAppliedPopupOffset();
        if (!expanded) {
            return;
        }
        if (this.popupXOffset == 0) {
            return;
        }
        this.appliedPopupXOffset = this.popupXOffset;
        this.appliedPopupYOffset = this.popupXOffset > DROPDOWN_ITEM_GAP
                ? -(CONTROL_BUTTON_SIZE + DROPDOWN_ITEM_GAP)
                : 0;
        this.offsetMenuItems(this.appliedPopupXOffset, this.appliedPopupYOffset);
    }

    private void removeAppliedPopupOffset() {
        if (this.appliedPopupXOffset == 0 && this.appliedPopupYOffset == 0) {
            return;
        }
        this.offsetMenuItems(-this.appliedPopupXOffset, -this.appliedPopupYOffset);
        this.appliedPopupXOffset = 0;
        this.appliedPopupYOffset = 0;
    }

    private void offsetMenuItems(int xOffset, int yOffset) {
        for (IconMenuItem iconItem : this.iconItems) {
            iconItem.setPosition(iconItem.getX() + xOffset, iconItem.getY() + yOffset);
        }
    }

    private static final class IconMenuItem extends AbstractDropdownMenuWidget.AbstractMenuItem {
        private final
        //$ resource_location_type_swap
        Identifier
        icon;
        private final Runnable callback;
        private boolean selected;

        private IconMenuItem(
                Component message,
                //$ resource_location_type_swap
                Identifier
                icon,
                Runnable callback
        ) {
            super(CONTROL_BUTTON_SIZE, CONTROL_BUTTON_SIZE, message);
            this.icon = icon;
            this.callback = callback;
            this.setTooltip(message);
        }

        @Override
        protected void onSelected() {
            this.callback.run();
        }

        @Override
        protected void renderMenuItem(
                GuiGraphicsExtractor context,
                int mouseX,
                int mouseY,
                float deltaTicks
        ) {
            IconControlRenderer.render(context, this, this.icon, this.selected, true, true);
        }
    }
}
