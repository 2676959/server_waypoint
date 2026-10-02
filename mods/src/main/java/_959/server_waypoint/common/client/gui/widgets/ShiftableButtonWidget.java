package _959.server_waypoint.common.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * A pressable control. Like vanilla {@code AbstractButton}, a left click and Enter, Space or keypad
 * Enter while the button is focused, active and visible both play the click sound and run
 * {@link #onPress()}.
 */
public abstract class ShiftableButtonWidget extends ShiftableClickableWidget {
    public ShiftableButtonWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    protected abstract void onPress();

    @Override
    public final void onClick(double mouseX, double mouseY) {
        this.onPress();
    }

    /** Whether the key presses this button: Enter, Space or keypad Enter while it is active and visible. */
    public boolean activatesOn(int keyCode) {
        return this.isActive() && (keyCode == InputConstants.KEY_RETURN
                || keyCode == InputConstants.KEY_SPACE
                || keyCode == InputConstants.KEY_NUMPADENTER);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.activatesOn(keyCode)) {
            return false;
        }
        this.playClickSound();
        this.onPress();
        return true;
    }

    /** Skipped when no client is running, as in unit tests. */
    protected void playClickSound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) {
            this.playDownSound(minecraft.getSoundManager());
        }
    }
}
