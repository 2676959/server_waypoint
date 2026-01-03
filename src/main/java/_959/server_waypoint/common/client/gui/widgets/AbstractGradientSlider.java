package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.function.Consumer;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

/**
 * A discrete slider with a color gradient background.
 * */
public abstract class AbstractGradientSlider implements Widget, Drawable {
    private final int sliderWidth;
    private final float unitLength;
    private final int maxLevel;
    protected final int slotWidth;
    protected final int slotHeight;
    protected int x;
    protected int y;
    protected float sliderCenter = 0F;
    private float sliderLeft;
    private float sliderRight;
    protected int sliderLevel = 0;
    protected int endX;
    protected int endY;
    protected int startColor;
    protected int endColor;

    public AbstractGradientSlider(int x, int y, int slotWidth, int slotHeight, int sliderWidth, int maxLevel) {
        this.x = x;
        this.y = y;
        this.slotWidth = slotWidth;
        this.slotHeight = slotHeight;
        this.endX = x + slotWidth;
        this.endY = y + slotHeight;
        this.sliderWidth = sliderWidth;
        this.sliderRight = sliderWidth / 2F;
        this.sliderLeft = -sliderWidth / 2F;
        this.maxLevel = maxLevel;
        this.unitLength = (float) slotWidth / maxLevel;
    }

    public void setStartColor(int startColor) {
        this.startColor = startColor;
    }

    public void setEndColor(int endColor) {
        this.endColor = endColor;
    }

    public AbstractGradientSlider(int x, int y, int slotWidth, int slotHeight, int maxLevel) {
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
        this.endX = x + this.slotWidth;
    }

    @Override
    public void setY(int y) {
        this.y = y;
        this.endY = y + this.slotHeight;
    }

    public void mouseClickedOrDragged(double x) {
        updateSliderCenter(Math.clamp((float) x, this.x, this.endX));
    }

    public void mouseScrolled(double verticalAmount) {
        updateSliderCenter(Math.clamp((float) verticalAmount + this.sliderCenter, this.x, this.endX));
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
        this.sliderLeft = sliderCenter - this.sliderWidth / 2F;
        this.sliderRight = sliderCenter + this.sliderWidth / 2F;
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
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        MatrixStack matrixStack = context.getMatrices();
        Matrix4f matrix =  matrixStack.peek().getPositionMatrix();
        context.draw(vertexConsumerProvider -> {
            VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getGui());
            // draw the gradient slot
            drawGradient(vertexConsumer, matrix, this.x, this.endX, startColor, endColor);
            // draw the slider
            drawSlider(vertexConsumer, matrix);
        });
    }

    public void drawGradient(VertexConsumer vertexConsumer, Matrix4f matrix, float startX, float endX, int startColor, int endColor) {
        vertexConsumer.vertex(matrix, startX, this.y, 0).color(startColor);
        vertexConsumer.vertex(matrix, startX, this.endY, 0).color(startColor);
        vertexConsumer.vertex(matrix, endX, this.endY, 0).color(endColor);
        vertexConsumer.vertex(matrix, endX, this.y, 0).color(endColor);
    }

    public void drawSlider(VertexConsumer vertexConsumer, Matrix4f matrix) {
        vertexConsumer.vertex(matrix, this.sliderLeft, this.y, 0).color(0xFFFFFFFF);
        vertexConsumer.vertex(matrix, this.sliderLeft, this.endY, 0).color(0xFFFFFFFF);
        vertexConsumer.vertex(matrix, this.sliderRight, this.endY, 0).color(0xFFFFFFFF);
        vertexConsumer.vertex(matrix, this.sliderRight, this.y, 0).color(0xFFFFFFFF);
    }

    @Override
    public void forEachChild(Consumer<ClickableWidget> consumer) {}
}
