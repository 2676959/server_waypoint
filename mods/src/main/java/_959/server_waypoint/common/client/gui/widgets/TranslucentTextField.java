//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.renderOutline;
import static _959.server_waypoint.common.client.gui.WidgetThemeColors.*;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
//? if >= 1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
//?}
import net.minecraft.network.chat.Component;

public class TranslucentTextField extends EditBox implements Shiftable {
    private int shiftedX;
    private int shiftedY;
    private int xOffset;
    private int yOffset;
    protected final int backgroundHeight;

    public TranslucentTextField(int x, int y, int width, Component text, Font textRenderer) {
        super(textRenderer, x, y, width, textRenderer.lineHeight, null, text);
        this.setTextColor(0xFFFFFFFF);
        this.setBordered(false);
        this.backgroundHeight = this.height + 2;
        this.setX(x);
        this.setY(y);
    }

    @Override
    public void
    //$ render_widget_method_swap
    extractWidgetRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int x = getShiftedX() - 2;
        int y = getShiftedY() - 2;
        int right = x - 1 + this.width;
        int bottom = y - 1 + this.backgroundHeight;
        context.fill(x + 1, y + 1, right, bottom, BUTTON_BG_COLOR);
        this.isHovered = mouseX >= x && mouseY >= y && mouseX <= right && mouseY <= bottom;
        int bdColor = isFocused() | isHovered() ? BORDER_FOCUS_COLOR : BORDER_COLOR;
        renderOutline(context, x, y, this.width, this.backgroundHeight, bdColor);
        super.
        //$ render_widget_method_swap
        extractWidgetRenderState
        (context, mouseX, mouseY, deltaTicks);
    }

    public void renderTextField(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.
        //$ render_widget_method_swap
        extractWidgetRenderState
        (context, mouseX, mouseY, deltaTicks);
    }

    public int getVisualHeight() {
        return this.backgroundHeight;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        //? if >= 1.21.9 {
        return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
        //?} else {
        /*return super.keyPressed(keyCode, scanCode, modifiers);
        *///?}
    }

    public boolean charTyped(char chr, int modifiers) {
        //? if >= 1.21.9 {
        return super.charTyped(new CharacterEvent(chr/*? if <26 {*//*, modifiers*//*?}*/));
        //?} else {
        /*return super.charTyped(chr, modifiers);
        *///?}
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //? if >= 1.21.9 {
        return super.mouseClicked(new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false);
        //?} else {
        /*return super.mouseClicked(mouseX, mouseY, button);
        *///?}
    }

    //? if >= 1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return this.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        return this.charTyped(
                characterEvent.codepointAsString().charAt(0),
                /*? if >=26 {*/ 0 /*?} else {*/ /*characterEvent.modifiers() *//*?}*/
        );
    }
    //?}

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
        this.shiftedX = x + this.xOffset;
        super.setX(x);
    }

    @Override
    public void setY(int y) {
        this.shiftedY = y + this.yOffset;
        super.setY(y);
    }

    @Override
    public void setXOffset(int x) {
        this.xOffset = x;
        this.shiftedX = super.getX() + x;
        super.setX(super.getX());
    }

    @Override
    public void setYOffset(int y) {
        this.yOffset = y;
        this.shiftedY = super.getY() + y;
        super.setY(super.getY());
    }

    @Override
    public int getShiftedX() {
        return this.shiftedX;
    }

    @Override
    public int getShiftedY() {
        return this.shiftedY;
    }
}
