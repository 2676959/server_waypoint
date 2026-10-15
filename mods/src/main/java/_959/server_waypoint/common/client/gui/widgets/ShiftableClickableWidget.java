//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.client.gui.layout.Shiftable;
import _959.server_waypoint.common.client.gui.layout.VisualPositioning;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
//? if >= 1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public abstract class ShiftableClickableWidget extends AbstractWidget implements Shiftable {
    protected int shiftedX;
    protected int shiftedY;
    protected int xOffset;
    protected int yOffset;
    private @Nullable Component tooltip;

    public ShiftableClickableWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    //? if <= 1.20.1 {
    /*public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return this.mouseScrolled(mouseX, mouseY, 0, verticalAmount);
    }
    *///?}

    @Override
    public int getX() {
        return this.shiftedX;
    }

    @Override
    public int getY() {
        return this.shiftedY;
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.shiftedX = x + this.xOffset;
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.shiftedY = y + this.yOffset;
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        this.shiftedX = super.getX() + x;
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        this.shiftedY = super.getY() + y;
    }

    @Override
    public int getShiftedX() {
        return this.shiftedX;
    }

    @Override
    public int getShiftedY() {
        return this.shiftedY;
    }

    /**
     * Stores the control's tooltip. Remove it with {@code setTooltip((Component) null)}: a bare
     * {@code null} is ambiguous next to vanilla's {@code setTooltip(Tooltip)}, which would bring back
     * vanilla's box.
     * <p>
     * Only a class that passes the tooltip on shows it: {@link TranslucentButton}, {@link IconButton},
     * {@link ColorSquareButton} and {@link RandomColorSquareButton}, {@link AbstractDropdownMenuWidget}
     * with its {@code AbstractMenuItem}s, and the manager screen's {@code IconToggleButton}. A class
     * does so by calling {@link #scheduleTooltip} at the end of its renderer and {@link #narrateTooltip}
     * at the end of {@code updateWidgetNarration}. Other classes ignore the tooltip without an error.
     */
    public void setTooltip(@Nullable Component tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * The last step of a renderer that supports tooltips. It requests this control's tooltip, anchored to
     * its visual bounds, from {@link TooltipLayer}. A control without a tooltip returns at once, so it
     * never reads the game's input state.
     */
    protected final void scheduleTooltip(int mouseX, int mouseY) {
        if (this.tooltip == null) {
            return;
        }
        ScreenRectangle visualBounds = new ScreenRectangle(
                VisualPositioning.getVisualX(this),
                VisualPositioning.getVisualY(this),
                VisualPositioning.getVisualWidth(this),
                VisualPositioning.getVisualHeight(this));
        TooltipLayer.scheduleForControl(this.tooltip, visualBounds, this.isHovered(), this.isFocused(),
                this.isKeyboardNavigating(), mouseX, mouseY);
    }

    /**
     * The last step of {@code updateWidgetNarration} in a class that supports tooltips. It adds the
     * tooltip as a hint, the element vanilla's tooltip adds, and does nothing without one.
     */
    protected final void narrateTooltip(NarrationElementOutput output) {
        if (this.tooltip != null) {
            output.add(NarratedElementType.HINT, this.tooltip);
        }
    }

    /** Whether the player last used the keyboard rather than the mouse. Tests override it. */
    protected boolean isKeyboardNavigating() {
        return Minecraft.getInstance().getLastInputType().isKeyboard();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    public boolean charTyped(char chr, int modifiers) {
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isActive() || button != InputConstants.MOUSE_BUTTON_LEFT || !this.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        this.playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());
        this.onClick(mouseX, mouseY);
        return true;
    }

    public void onClick(double mouseX, double mouseY) {
    }

    public void onRelease(double mouseX, double mouseY) {
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            this.onRelease(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    //? if >= 1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return this.keyPressed(keyEvent.key(), /*? if <26.3 {*/ keyEvent.scancode() /*?} else {*//* keyEvent.keycode() *//*?}*/, keyEvent.modifiers());
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        return this.keyReleased(keyEvent.key(), /*? if <26.3 {*/ keyEvent.scancode() /*?} else {*//* keyEvent.keycode() *//*?}*/, keyEvent.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        return this.charTyped(
                characterEvent.codepointAsString().charAt(0),
                /*? if >=26 {*/ 0 /*?} else {*/ /*characterEvent.modifiers() *//*?}*/
        );
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        return this.mouseClicked(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        return this.mouseReleased(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouseButtonEvent, double deltaX, double deltaY) {
        return this.mouseDragged(mouseButtonEvent.x(), mouseButtonEvent.y(), mouseButtonEvent.button(), deltaX, deltaY);
    }
    //?}
}
