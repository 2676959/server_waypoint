package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphics;
//? if >= 1.21.11 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;

public abstract class ShiftableClickableWidget extends AbstractWidget implements Shiftable {
    protected int shiftedX;
    protected int shiftedY;
    protected int xOffset;
    protected int yOffset;

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
        if (!this.isActive() || button != 0 || !this.isMouseOver(mouseX, mouseY)) {
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
        if (button == 0) {
            this.onRelease(mouseX, mouseY);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    //? if >= 1.21.11 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return this.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }

    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        return this.keyReleased(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        return this.charTyped(characterEvent.codepointAsString().charAt(0), characterEvent.modifiers());
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
