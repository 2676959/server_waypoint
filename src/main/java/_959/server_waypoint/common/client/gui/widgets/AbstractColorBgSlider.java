package _959.server_waypoint.common.client.gui.widgets;

import _959.server_waypoint.common.util.MathHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.function.Consumer;

import static _959.server_waypoint.common.client.gui.DrawContextHelper.vertex;
import static _959.server_waypoint.common.client.gui.DrawContextHelper.withVertexConsumers;

/**
 * A discrete slider with a color gradient background.
 * */
public abstract class AbstractColorBgSlider implements Widget, Drawable, Element {
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
    public final void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.getMatrices().push();
        MatrixStack matrixStack = context.getMatrices();
        matrixStack.translate(this.x, this.y, 0);
        Matrix4f matrix =  matrixStack.peek().getPositionMatrix();
        withVertexConsumers(context, vertexConsumerProvider -> {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getGui());
            // draw the slot background
            drawSlotBackground(vertexConsumer, matrix);
            // draw the slider
            drawSlider(vertexConsumer, matrix);
        });
        context.getMatrices().pop();
    }

    public abstract void drawSlotBackground(VertexConsumer vertexConsumer, Matrix4f matrix);

    protected void drawSolidColor(VertexConsumer vertexConsumer, Matrix4f matrix, int color) {
        vertex(vertexConsumer, matrix, 0, 0, 0, color);
        vertex(vertexConsumer, matrix, 0, slotHeight, 0, color);
        vertex(vertexConsumer, matrix, slotWidth, slotHeight, 0, color);
        vertex(vertexConsumer, matrix, slotWidth, 0, 0, color);
    }

    protected void drawGradient(VertexConsumer vertexConsumer, Matrix4f matrix, int startColor, int endColor) {
        vertex(vertexConsumer, matrix, 0, 0, 0, startColor);
        vertex(vertexConsumer, matrix, 0, slotHeight, 0, startColor);
        vertex(vertexConsumer, matrix, slotWidth, slotHeight, 0, endColor);
        vertex(vertexConsumer, matrix, slotWidth, 0, 0, endColor);
    }

    protected void drawGradient(VertexConsumer vertexConsumer, Matrix4f matrix, float startX, float endX, int startColor, int endColor) {
        vertex(vertexConsumer, matrix, startX, 0, 0, startColor);
        vertex(vertexConsumer, matrix, startX, slotHeight, 0, startColor);
        vertex(vertexConsumer, matrix, endX, slotHeight, 0, endColor);
        vertex(vertexConsumer, matrix, endX, 0, 0, endColor);
    }

    protected void drawSlider(VertexConsumer vertexConsumer, Matrix4f matrix) {
        vertex(vertexConsumer, matrix, this.sliderLeft, 0, 0, 0xFFFFFFFF);
        vertex(vertexConsumer, matrix, this.sliderLeft, slotHeight, 0, 0xFFFFFFFF);
        vertex(vertexConsumer, matrix, this.sliderRight, slotHeight, 0, 0xFFFFFFFF);
        vertex(vertexConsumer, matrix, this.sliderRight, 0, 0, 0xFFFFFFFF);
    }

    @Override
    public ScreenRect getNavigationFocus() {
        return Element.super.getNavigationFocus();
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {}

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return this.focused;
    }
}
