package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.mixin.BoundKeyAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public abstract class MovementAllowedScreen extends Screen {
    protected final TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
    private KeyBinding forwardKeyBinding;
    private KeyBinding leftKeyBinding;
    private KeyBinding backKeyBinding;
    private KeyBinding rightKeyBinding;
    private KeyBinding jumpKeyBinding;
    private KeyBinding sneakKeyBinding;
    private KeyBinding sprintKeyBinding;
    private InputUtil.Key forwardKey;
    private InputUtil.Key leftKey;
    private InputUtil.Key backKey;
    private InputUtil.Key rightKey;
    private InputUtil.Key jumpKey;
    private InputUtil.Key sneakKey;
    private InputUtil.Key sprintKey;
    private int forwardKeyCode;
    private int leftKeyCode;
    private int backKeyCode;
    private int rightKeyCode;
    private int jumpKeyCode;
    private int sneakKeyCode;
    private int sprintKeyCode;
    private boolean movementAllowed = true;

    protected MovementAllowedScreen(Text title) {
        super(title);
    }

    abstract int getContentWidth();
    abstract int getContentHeight();

    protected int getCenteredX() {
        return (this.width >> 1) - (getContentWidth() >> 1);
    }

    protected int getCenteredY() {
        return (this.height >> 1) - (getContentHeight() >> 1);
    }

    public static int centered(int containerSize, int contentSize) {
        return (containerSize - contentSize) >> 1;
    }

    public void acceptMovementKeys(boolean bool) {
        this.movementAllowed = bool;
    }

    @Override
    protected void init() {
        if (this.client == null) {
            LOGGER.warn("MinecraftClient is null, not support to initialize");
            return;
        }
        forwardKeyBinding = this.client.options.forwardKey;
        leftKeyBinding = this.client.options.leftKey;
        backKeyBinding = this.client.options.backKey;
        rightKeyBinding = this.client.options.rightKey;
        jumpKeyBinding = this.client.options.jumpKey;
        sneakKeyBinding = this.client.options.sneakKey;
        sprintKeyBinding = this.client.options.sprintKey;

        forwardKey = ((BoundKeyAccessor) forwardKeyBinding).getBoundKey();
        leftKey = ((BoundKeyAccessor) leftKeyBinding).getBoundKey();
        backKey = ((BoundKeyAccessor) backKeyBinding).getBoundKey();
        rightKey = ((BoundKeyAccessor) rightKeyBinding).getBoundKey();
        jumpKey = ((BoundKeyAccessor) jumpKeyBinding).getBoundKey();
        sneakKey = ((BoundKeyAccessor) sneakKeyBinding).getBoundKey();
        sprintKey = ((BoundKeyAccessor) sprintKeyBinding).getBoundKey();

        forwardKeyCode = forwardKey.getCode();
        leftKeyCode = leftKey.getCode();
        backKeyCode = backKey.getCode();
        rightKeyCode = rightKey.getCode();
        jumpKeyCode = jumpKey.getCode();
        sneakKeyCode = sneakKey.getCode();
        sprintKeyCode = sprintKey.getCode();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!movementAllowed) return super.keyPressed(keyCode, scanCode, modifiers);
        boolean ret = false;
        if (keyCode == forwardKeyCode || scanCode == forwardKeyCode) {
            forwardKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(forwardKey);
            ret = true;
        } else if (keyCode == leftKeyCode || scanCode == leftKeyCode) {
            leftKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(leftKey);
            ret = true;
        } else if (keyCode == backKeyCode || scanCode == backKeyCode) {
            backKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(backKey);
            ret = true;
        } else if (keyCode == rightKeyCode || scanCode == rightKeyCode) {
            rightKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(rightKey);
            ret = true;
        } else if (keyCode == jumpKeyCode || scanCode == jumpKeyCode) {
            jumpKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(jumpKey);
            ret = true;
        } else if (keyCode == sneakKeyCode || scanCode == sneakKeyCode) {
            sneakKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(sneakKey);
            ret = true;
        } else if (keyCode == sprintKeyCode || scanCode == sprintKeyCode) {
            sprintKeyBinding.setPressed(true);
            KeyBinding.onKeyPressed(sprintKey);
            ret = true;
        }
        boolean ret2 = super.keyPressed(keyCode, scanCode, modifiers);
        return ret || ret2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
