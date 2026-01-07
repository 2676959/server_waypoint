package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

public abstract class Abstract3ChannelColorPicker<T extends AbstractGradientSlider> extends ShiftableClickableWidget implements IColorPicker, Shiftable {
    protected final ColorPickerCallBack callback;
    private final int slotHeight;
    private final int slotWidth;
    protected final T slider0;
    protected final T slider1;
    protected final T slider2;
    private int focusedIndex = 0;

    protected Abstract3ChannelColorPicker(int x, int y, int slotWidth, int slotHeight, Text message, T slider0, T slider1, T slider2, ColorPickerCallBack callback) {
        super(x, y, slotWidth, slotHeight * 3, message);
        this.slotHeight = slotHeight;
        this.slotWidth = slotWidth;
        this.slider0 = slider0;
        this.slider1 = slider1;
        this.slider2 = slider2;
        this.callback = callback;
    }

    abstract void onChannel0Update();
    abstract void onChannel1Update();
    abstract void onChannel2Update();

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float x = (float) mouseX - getX();
        float y = (float) mouseY - getY();
        int index = (int) (y / slotHeight);
        switch (index) {
            case 0 -> {
                this.slider0.mouseClickedOrDragged(x);
                focusedIndex = 0;
                onChannel0Update();
                return true;
            }
            case 1 -> {
                this.slider1.mouseClickedOrDragged(x);
                focusedIndex = 1;
                onChannel1Update();
                return true;
            }
            case 2 -> {
                this.slider2.mouseClickedOrDragged(x);
                focusedIndex = 2;
                onChannel2Update();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        double x = mouseX - getX();
//        float y = (float) mouseY - getY();
//        int index = (int) (y / slotHeight);
        switch (focusedIndex) {
            case 0 -> {
                this.slider0.mouseClickedOrDragged(x);
                onChannel0Update();
                return true;
            }
            case 1 -> {
                this.slider1.mouseClickedOrDragged(x);
                onChannel1Update();
                return true;
            }
            case 2 -> {
                this.slider2.mouseClickedOrDragged(x);
                onChannel2Update();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float x = (float) mouseX - getX();
        if (x < 0 || x > slotWidth) return false;
        float y = (float) mouseY - getY();
        int index = (int) (y / slotHeight);
        switch (index) {
            case 0 -> {
                this.slider0.mouseScrolled(verticalAmount);
                onChannel0Update();
                return true;
            }
            case 1 -> {
                this.slider1.mouseScrolled(verticalAmount);
                onChannel1Update();
                return true;
            }
            case 2 -> {
                this.slider2.mouseScrolled(verticalAmount);
                onChannel2Update();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        switch (focusedIndex) {
            case 0 -> {
                boolean bl = this.slider0.keyPressed(keyCode);
                onChannel0Update();
                return bl;
            }
            case 1 -> {
                boolean bl = this.slider1.keyPressed(keyCode);
                onChannel1Update();
                return bl;
            }
            case 2 -> {
                boolean bl = this.slider2.keyPressed(keyCode);
                onChannel2Update();
                return bl;
            }
        }
        return false;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        MatrixStack matrixStack = context.getMatrices();
        int y = getY();
        int x = getX();
        matrixStack.translate(x, y, 0);
        this.slider0.render(context, mouseX, mouseY, deltaTicks);
        this.slider1.render(context, mouseX, mouseY, deltaTicks);
        this.slider2.render(context, mouseX, mouseY, deltaTicks);
        matrixStack.translate(-x, -y, 0);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
