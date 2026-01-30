package _959.server_waypoint.common.client.gui.widgets;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.text.Text;

public abstract class Abstract3ChannelColorPicker<T extends AbstractColorBgSlider> extends ShiftableClickableWidget implements Colorable, Shiftable {
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
        this.setX(x);
        this.setY(y);
    }

    @Override
    public void setXOffset(int xOffset) {
        super.setXOffset(xOffset);
        this.slider0.setX(this.getShiftedX());
        this.slider1.setX(this.getShiftedX());
        this.slider2.setX(this.getShiftedX());
    }

    @Override
    public void setYOffset(int yOffset) {
        super.setYOffset(yOffset);
        this.slider0.setY(this.getShiftedY());
        this.slider1.setY(this.getShiftedY() + slotHeight);
        this.slider2.setY(this.getShiftedY() + slotHeight + slotHeight);
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        this.slider0.setX(x);
        this.slider1.setX(x);
        this.slider2.setX(x);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        this.slider0.setY(y);
        this.slider1.setY(y + slotHeight);
        this.slider2.setY(y + slotHeight + slotHeight);
    }

    public abstract void onChannel0Update();
    public abstract void onChannel1Update();
    public abstract void onChannel2Update();

    public void updateSlider0(int level) {
        this.slider0.setSliderLevel(level);
        this.onChannel0Update();
    }

    public void updateSlider1(int level) {
        this.slider1.setSliderLevel(level);
        this.onChannel1Update();
    }

    public void updateSlider2(int level) {
        this.slider2.setSliderLevel(level);
        this.onChannel2Update();
    }

    public final int getSlider0Level() {
        return this.slider0.getSliderLevel();
    }

    public final int getSlider1Level() {
        return this.slider1.getSliderLevel();
    }

    public final int getSlider2Level() {
        return this.slider2.getSliderLevel();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        float x = (float) mouseX - getX();
        int index = (int) Math.floor((mouseY - getY()) / slotHeight);
        switch (index) {
            case 0 -> {
                this.slider0.mouseClickedOrDragged(mouseX);
                focusedIndex = 0;
                onChannel0Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 1 -> {
                this.slider1.mouseClickedOrDragged(mouseX);
                focusedIndex = 1;
                onChannel1Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 2 -> {
                this.slider2.mouseClickedOrDragged(mouseX);
                focusedIndex = 2;
                onChannel2Update();
                this.callback.onColorUpdate(getColor());
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
                this.slider0.mouseClickedOrDragged(mouseX);
                onChannel0Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 1 -> {
                this.slider1.mouseClickedOrDragged(mouseX);
                onChannel1Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 2 -> {
                this.slider2.mouseClickedOrDragged(mouseX);
                onChannel2Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float x = (float) mouseX - getX();
        if (x < 0 || x > slotWidth) return false;
        int index = (int) Math.floor((mouseY - getY()) / slotHeight);
        switch (index) {
            case 0 -> {
                this.slider0.mouseScrolled(verticalAmount);
                onChannel0Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 1 -> {
                this.slider1.mouseScrolled(verticalAmount);
                onChannel1Update();
                this.callback.onColorUpdate(getColor());
                return true;
            }
            case 2 -> {
                this.slider2.mouseScrolled(verticalAmount);
                onChannel2Update();
                this.callback.onColorUpdate(getColor());
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
                this.callback.onColorUpdate(getColor());
                return bl;
            }
            case 1 -> {
                boolean bl = this.slider1.keyPressed(keyCode);
                onChannel1Update();
                this.callback.onColorUpdate(getColor());
                return bl;
            }
            case 2 -> {
                boolean bl = this.slider2.keyPressed(keyCode);
                onChannel2Update();
                this.callback.onColorUpdate(getColor());
                return bl;
            }
        }
        return false;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        this.slider0.render(context, mouseX, mouseY, deltaTicks);
        this.slider1.render(context, mouseX, mouseY, deltaTicks);
        this.slider2.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
