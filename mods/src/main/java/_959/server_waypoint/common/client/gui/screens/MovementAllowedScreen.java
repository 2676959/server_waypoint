package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.mixin.BoundKeyAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import static _959.server_waypoint.common.client.WaypointClientMod.LOGGER;

public abstract class MovementAllowedScreen extends Screen {
    protected final Font font = Minecraft.getInstance().font;
    private KeyMapping forwardKeyBinding;
    private KeyMapping leftKeyBinding;
    private KeyMapping backKeyBinding;
    private KeyMapping rightKeyBinding;
    private KeyMapping jumpKeyBinding;
    private KeyMapping sneakKeyBinding;
    private KeyMapping sprintKeyBinding;
    private InputConstants.Key forwardKey;
    private InputConstants.Key leftKey;
    private InputConstants.Key backKey;
    private InputConstants.Key rightKey;
    private InputConstants.Key jumpKey;
    private InputConstants.Key sneakKey;
    private InputConstants.Key sprintKey;
    private int forwardKeyCode;
    private int leftKeyCode;
    private int backKeyCode;
    private int rightKeyCode;
    private int jumpKeyCode;
    private int sneakKeyCode;
    private int sprintKeyCode;
    private boolean movementAllowed = true;

    protected MovementAllowedScreen(Component title) {
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
        if (this.minecraft == null) {
            LOGGER.warn("MinecraftClient is null, not support to initialize");
            return;
        }
        forwardKeyBinding = this.minecraft.options.keyUp;
        leftKeyBinding = this.minecraft.options.keyLeft;
        backKeyBinding = this.minecraft.options.keyDown;
        rightKeyBinding = this.minecraft.options.keyRight;
        jumpKeyBinding = this.minecraft.options.keyJump;
        sneakKeyBinding = this.minecraft.options.keyShift;
        sprintKeyBinding = this.minecraft.options.keySprint;

        forwardKey = ((BoundKeyAccessor) forwardKeyBinding).getBoundKey();
        leftKey = ((BoundKeyAccessor) leftKeyBinding).getBoundKey();
        backKey = ((BoundKeyAccessor) backKeyBinding).getBoundKey();
        rightKey = ((BoundKeyAccessor) rightKeyBinding).getBoundKey();
        jumpKey = ((BoundKeyAccessor) jumpKeyBinding).getBoundKey();
        sneakKey = ((BoundKeyAccessor) sneakKeyBinding).getBoundKey();
        sprintKey = ((BoundKeyAccessor) sprintKeyBinding).getBoundKey();

        forwardKeyCode = forwardKey.getValue();
        leftKeyCode = leftKey.getValue();
        backKeyCode = backKey.getValue();
        rightKeyCode = rightKey.getValue();
        jumpKeyCode = jumpKey.getValue();
        sneakKeyCode = sneakKey.getValue();
        sprintKeyCode = sprintKey.getValue();
    }

    private void unpressAllMovementKeys() {
        forwardKeyBinding.setDown(false);
        leftKeyBinding.setDown(false);
        backKeyBinding.setDown(false);
        rightKeyBinding.setDown(false);
        jumpKeyBinding.setDown(false);
        sneakKeyBinding.setDown(false);
        sprintKeyBinding.setDown(false);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            //? if >= 1.21.9 {
            return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
            //?} else {
            /*return super.keyPressed(keyCode, scanCode, modifiers);
            *///?}
        }
        boolean ret = false;
        if (keyCode == forwardKeyCode || scanCode == forwardKeyCode) {
            forwardKeyBinding.setDown(true);
            KeyMapping.click(forwardKey);
            ret = true;
        } else if (keyCode == leftKeyCode || scanCode == leftKeyCode) {
            leftKeyBinding.setDown(true);
            KeyMapping.click(leftKey);
            ret = true;
        } else if (keyCode == backKeyCode || scanCode == backKeyCode) {
            backKeyBinding.setDown(true);
            KeyMapping.click(backKey);
            ret = true;
        } else if (keyCode == rightKeyCode || scanCode == rightKeyCode) {
            rightKeyBinding.setDown(true);
            KeyMapping.click(rightKey);
            ret = true;
        } else if (keyCode == jumpKeyCode || scanCode == jumpKeyCode) {
            jumpKeyBinding.setDown(true);
            KeyMapping.click(jumpKey);
            ret = true;
        } else if (keyCode == sneakKeyCode || scanCode == sneakKeyCode) {
            sneakKeyBinding.setDown(true);
            KeyMapping.click(sneakKey);
            ret = true;
        } else if (keyCode == sprintKeyCode || scanCode == sprintKeyCode) {
            sprintKeyBinding.setDown(true);
            KeyMapping.click(sprintKey);
            ret = true;
        }
        //? if >= 1.21.9 {
        boolean ret2 = super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
        //?} else {
        /*boolean ret2 = super.keyPressed(keyCode, scanCode, modifiers);
        *///?}
        return ret || ret2;
    }

    //? if >= 1.21.9 {
    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        return this.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }
    //?}

    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            //? if >= 1.21.9 {
            return super.keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
            //?} else {
            /*return super.keyReleased(keyCode, scanCode, modifiers);
            *///?}
        }
        boolean ret = false;
        if (keyCode == forwardKeyCode || scanCode == forwardKeyCode) {
            forwardKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == leftKeyCode || scanCode == leftKeyCode) {
            leftKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == backKeyCode || scanCode == backKeyCode) {
            backKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == rightKeyCode || scanCode == rightKeyCode) {
            rightKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == jumpKeyCode || scanCode == jumpKeyCode) {
            jumpKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == sneakKeyCode || scanCode == sneakKeyCode) {
            sneakKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == sprintKeyCode || scanCode == sprintKeyCode) {
            sprintKeyBinding.setDown(false);
            ret = true;
        }
        //? if >= 1.21.9 {
        boolean ret2 = super.keyReleased(new KeyEvent(keyCode, scanCode, modifiers));
        //?} else {
        /*boolean ret2 = super.keyReleased(keyCode, scanCode, modifiers);
        *///?}
        return ret || ret2;
    }

    //? if >= 1.21.9 {
    @Override
    public boolean keyReleased(KeyEvent keyEvent) {
        return this.keyReleased(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
    }
    //?}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
