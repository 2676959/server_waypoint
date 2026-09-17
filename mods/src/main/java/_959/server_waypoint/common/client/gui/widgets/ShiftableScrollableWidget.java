//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.util.MathHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.gui.render.WidgetThemeManager.getColor;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB_ACTIVE;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_THUMB_DISABLED;
import static _959.server_waypoint.common.client.gui.render.WidgetThemeVariable.SCROLLBAR_TRACK;

public abstract class ShiftableScrollableWidget extends ShiftableClickableWidget {
    public final int SCROLLBAR_WIDTH = 6;
    private double scrollY;
    private boolean scrolling;

    public ShiftableScrollableWidget(int x, int y, int width, int height, Component text) {
        super(x, y, width, height, text);
    }

    public abstract double getDeltaYPerScroll();

    public abstract int getContentHeight();

    public void setScrollY(double scrollY) {
        this.scrollY = MathHelper.clamp(scrollY, 0.0, this.getMaxScroll());
    }

    public double getScrollY() {
        return this.scrollY;
    }

    public int getMaxScroll() {
        return Math.max(0, this.getContentHeight() - this.height);
    }

    public boolean overflows() {
        return this.getContentHeight() > this.height;
    }

    public boolean checkScrollbarDragged(double mouseX, double mouseY, int button) {
        if (!this.active || button != 0) {
            return false;
        }
        int x = this.getX();
        int y = this.getY();
        int right = x + this.width;
        int bottom = y + this.height;
        return mouseX >= (double)(right - SCROLLBAR_WIDTH) && mouseX <= (double)right && mouseY >= (double)y && mouseY <= (double)bottom;
    }

    public void drawScrollbar(GuiGraphicsExtractor context) {
        if (!this.overflows()) {
            return;
        }
        int x = this.getX();
        int y = this.getY();
        int right = x + this.width;
        int bottom = y + this.height;
        int contentHeight = this.getContentHeight();
        int handleHeight = (int)((float)(this.height * this.height) / (float)contentHeight);
        handleHeight = MathHelper.clamp(handleHeight, 32, this.height - 8);
        int handleY = (int)this.getScrollY() * (this.height - handleHeight) / this.getMaxScroll() + y;
        if (handleY < y) {
            handleY = y;
        }
        
        // Background
        int trackColor = getColor(SCROLLBAR_TRACK);
        context.fill(right - SCROLLBAR_WIDTH, y, right, bottom, trackColor);
        
        // Handle
        int handleColor = getColor(!this.active ? SCROLLBAR_THUMB_DISABLED : this.scrolling ? SCROLLBAR_THUMB_ACTIVE : SCROLLBAR_THUMB);
        context.fill(right - SCROLLBAR_WIDTH, handleY, right, handleY + handleHeight, handleColor);
    }

    public void refreshScroll() {
        this.setScrollY(this.scrollY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!this.active || !this.overflows()) {
            return false;
        }
        this.setScrollY(this.scrollY - verticalAmount * this.getDeltaYPerScroll());
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.overflows()) {
            return false;
        }
        if (this.checkScrollbarDragged(mouseX, mouseY, button)) {
            this.scrolling = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!this.active) {
            this.scrolling = false;
            return false;
        }
        if (this.scrolling) {
            if (mouseY < (double)this.getY()) {
                this.setScrollY(0.0);
            } else if (mouseY > (double)(this.getY() + this.height)) {
                this.setScrollY(this.getMaxScroll());
            } else {
                double maxScroll = Math.max(1, this.getMaxScroll());
                int contentHeight = this.getContentHeight();
                
                int handleHeight = (int)((float)(this.height * this.height) / (float)contentHeight);
                handleHeight = MathHelper.clamp(handleHeight, 32, this.height - 8);
                double scrollFactor = maxScroll / (double)(this.height - handleHeight);
                
                this.setScrollY(this.scrollY + deltaY * scrollFactor);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.scrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
