//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.util.MathHelper;

import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawColorGradient;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.drawHorizontalGradient;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.pop;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.push;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.translate;

/**
 * A discrete slider with a color gradient background.
 * */
public abstract class AbstractColorBgSlider implements LayoutElement, Renderable, GuiEventListener {
    private final float sliderHalfWidth;
    private final float unitLength;
    private final int maxLevel;
    protected final int slotWidth;
    protected final int slotHeight;
    protected int x;
    protected int y;
    protected boolean focused = false;
    protected float sliderCenter = 0F;
    private float sliderLeft;
    private float sliderRight;
    protected int sliderLevel = 0;
    protected int endX;
    protected int endY;
    protected int startColor;
    protected int endColor;

    public AbstractColorBgSlider(int x, int y, int slotWidth, int slotHeight, int sliderWidth, int maxLevel) {
        this.x = x;
        this.y = y;
        this.slotWidth = slotWidth;
        this.slotHeight = slotHeight;
        this.endX = x + slotWidth;
        this.endY = y + slotHeight;
        this.sliderHalfWidth = sliderWidth / 2F;
        this.sliderRight = sliderHalfWidth;
        this.sliderLeft = -sliderHalfWidth;
        this.maxLevel = maxLevel;
        this.unitLength = (float) slotWidth / maxLevel;
    }

    public void setStartColor(int startColor) {
        this.startColor = startColor;
    }

    public void setEndColor(int endColor) {
        this.endColor = endColor;
    }

    public AbstractColorBgSlider(int x, int y, int slotWidth, int slotHeight, int maxLevel) {
        this(x, y, slotWidth, slotHeight, 1, maxLevel);
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.slotWidth;
    }

    @Override
    public int getHeight() {
        return this.slotHeight;
    }

    @Override
    public void setX(int x) {
        this.x = x;
        this.endX = this.x + this.slotWidth;
    }

    @Override
    public void setY(int y) {
        this.y = y;
        this.endY = this.y + this.slotHeight;
    }

    public void mouseClickedOrDragged(double mouseX) {
        updateSliderCenter(MathHelper.clamp((float) mouseX, this.x, this.endX) - this.x);
    }

    public void mouseScrolled(double verticalAmount) {
        updateSliderCenter(MathHelper.clamp((float) verticalAmount + this.sliderCenter, 0, slotWidth));
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == 262) {
            // right key
            this.sliderLevel = Math.min(this.sliderLevel + 1, this.maxLevel);
            setSliderCenter(this.unitLength * this.sliderLevel);
            return true;
        } else if (keyCode == 263) {
            // left key
            this.sliderLevel = Math.max(this.sliderLevel - 1, 0);
            setSliderCenter(this.unitLength * this.sliderLevel);
            return true;
        } else {
            return false;
        }
    }

    private void setSliderCenter(float sliderCenter) {
        this.sliderCenter = sliderCenter;
        this.sliderLeft = sliderCenter - this.sliderHalfWidth;
        this.sliderRight = sliderCenter + this.sliderHalfWidth;
    }

    /**
     * should be only used by mouse interaction
     * */
    public void updateSliderCenter(float sliderCenter) {
        setSliderCenter(sliderCenter);
        this.sliderLevel = (int) (sliderCenter / this.unitLength + 0.5F);
    }

    public void setSliderLevel(int sliderLevel) {
        this.sliderLevel = sliderLevel;
        setSliderCenter(this.unitLength * sliderLevel);
    }

    public int getSliderLevel() {
        return this.sliderLevel;
    }

    @Override
    public final void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        push(context);
        translate(context, this.x, this.y);
        drawSlotBackground(context);
        drawSlider(context);
        pop(context);
    }

    public abstract void drawSlotBackground(GuiGraphicsExtractor context);

    protected void drawSolidColor(GuiGraphicsExtractor context, int color) {
        drawColorGradient(context, 0, 0, this.slotWidth, this.slotHeight, color, color, color, color);
    }

    protected void drawGradient(GuiGraphicsExtractor context, int startColor, int endColor) {
        drawAlphaGradient(context, 0, this.slotWidth, solid(startColor), solid(endColor));
    }

    protected void drawGradient(GuiGraphicsExtractor context, float startX, float endX, int startColor, int endColor) {
        drawAlphaGradient(context, startX, endX, solid(startColor), solid(endColor));
    }

    protected void drawAlphaGradient(GuiGraphicsExtractor context, int startColor, int endColor) {
        drawAlphaGradient(context, 0, this.slotWidth, startColor, endColor);
    }

    protected void drawAlphaGradient(GuiGraphicsExtractor context, float startX, float endX, int startColor, int endColor) {
        drawHorizontalGradient(context, startX, 0, endX, this.slotHeight, startColor, endColor);
    }

    private static int solid(int color) {
        return 0xFF000000 | color;
    }

    protected void drawSlider(GuiGraphicsExtractor context) {
        //? if = 26.1.2 {
        /*int sliderX = MathHelper.clamp((int) this.sliderCenter, 0, this.slotWidth - 1);
        drawSolidColor(context, sliderX, sliderX + 1, 0xFFFFFFFF);
        *///?} else {
        
        drawSolidColor(context, this.sliderLeft, this.sliderRight, 0xFFFFFFFF);
        //?}
    }

    private void drawSolidColor(GuiGraphicsExtractor context, float startX, float endX, int color) {
        drawColorGradient(context, startX, 0, endX, this.slotHeight, color, color, color, color);
    }

    @Override
    public ScreenRectangle getRectangle() {
        return GuiEventListener.super.getRectangle();
    }

    @Override
    public void visitWidgets(Consumer<AbstractWidget> consumer) {}

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }
}
