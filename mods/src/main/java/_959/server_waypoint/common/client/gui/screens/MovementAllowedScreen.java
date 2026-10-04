//~ gui_graphics_26
package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.api.PopupOwner;
import _959.server_waypoint.common.client.gui.render.WidgetThemeManager;
import _959.server_waypoint.common.client.gui.render.WidgetThemeVariable;
import _959.server_waypoint.common.client.gui.widgets.ShiftableButtonWidget;
import _959.server_waypoint.common.client.gui.widgets.ComboBoxWidget;
import _959.server_waypoint.common.client.gui.widgets.IntegerSlider;
import _959.server_waypoint.mixin.BoundKeyAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
//? if >= 1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
//? if forge || neoforge {
/*import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
//? if neoforge {
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
//?} else {
import net.minecraftforge.client.settings.IKeyConflictContext;
//?}
*///?}

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
    //? if forge || neoforge {
    /*private Map<KeyMapping, MovementKeyContext> movementKeyContexts;
    *///?}

    protected MovementAllowedScreen(Component title) {
        super(title);
    }

    @Override
    public final void
    //$ render_method_swap
    extractRenderState
            (GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        //? if < 1.21.6 {
        /*this.renderScreenBackground(context, deltaTicks);
        *///?}
        this.renderScreenContents(context, mouseX, mouseY, deltaTicks);
    }

    protected abstract void renderScreenContents(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            float deltaTicks
    );

    /**
     * The themed overlay. Without a world, as when the screen opens from a mod list, vanilla's
     * background for screens outside a world goes underneath it.
     */
    private void renderScreenBackground(GuiGraphicsExtractor context, float deltaTicks) {
        if (this.minecraft.level == null) {
            this.renderBackgroundWithoutWorld(context, deltaTicks);
        }
        context.fill(0, 0, this.width, this.height,
                WidgetThemeManager.getColor(WidgetThemeVariable.SCREEN_BACKGROUND));
    }

    private void renderBackgroundWithoutWorld(GuiGraphicsExtractor context, float deltaTicks) {
        //? if < 1.20.5 {
        /*this.renderDirtBackground(context);
        *///?} elif < 1.21.2 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground(deltaTicks);
        this.renderMenuBackground(context);
        *///?} elif < 1.21.6 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground();
        this.renderMenuBackground(context);
        *///?} elif < 26 {
        /*this.renderPanorama(context, deltaTicks);
        this.renderBlurredBackground(context);
        this.renderMenuBackground(context);
        *///?} else {
        this.extractPanorama(context, deltaTicks);
        this.extractBlurredBackground(context);
        this.extractMenuBackground(context);
        //?}
    }

    //? if = 1.21.6 {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.renderScreenBackground(context, deltaTicks);
    }
    *///?} elif >= 1.21.9 && < 26 {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.renderScreenBackground(context, deltaTicks);
        this.minecraft.gui.renderDeferredSubtitles();
    }
    *///?} elif >= 26 && < 26.2 {
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.renderScreenBackground(context, deltaTicks);
        this.minecraft.gui.extractDeferredSubtitles();
    }
    //?} elif >= 26.2 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        this.renderScreenBackground(context, deltaTicks);
        this.minecraft.gui.hud.extractDeferredSubtitles();
    }
    *///?}

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

    /**
     * Vanilla closes the screen on Escape before the focused widget receives the key. Dismiss
     * an open popup or leave text entry first, even when no suggestions or choices are showing.
     * An {@link IntegerSlider} is text entry while its number field has focus; leaving the field
     * commits the typed number.
     */
    protected boolean dismissFocusedInput() {
        GuiEventListener focused = this.getFocused();
        boolean closedPopup = focused instanceof PopupOwner owner && owner.closePopupIfOpen();
        if (closedPopup || focused instanceof EditBox || focused instanceof ComboBoxWidget
                || focused instanceof IntegerSlider slider && slider.isEditingNumber()) {
            this.setFocused(null);
            return true;
        }
        return false;
    }

    /** Whether the player last used Tab or the arrow keys rather than a mouse button. */
    boolean isKeyboardNavigating() {
        return this.minecraft.getLastInputType().isKeyboard();
    }

    /**
     * Whether a modal, such as a dialog, is open and holds focus. A rebuild, as after a resize, then
     * leaves focus where the modal put it.
     */
    protected boolean hasOpenModal() {
        return false;
    }

    //? if >= 1.20.5 {
    /**
     * After every rebuild, vanilla moves focus to the next Tab stop when the keyboard was used last,
     * which would take it from a dialog's Cancel button to its confirm button. An open modal keeps
     * its focus instead.
     */
    @Override
    protected void setInitialFocus() {
        if (!this.hasOpenModal()) {
            this.pickInitialFocus();
        }
    }

    /** Vanilla's pick, which reads the game's last input type. */
    void pickInitialFocus() {
        super.setInitialFocus();
    }
    //?}

    private boolean focusedButtonActivatesOn(int keyCode) {
        return this.getFocused() instanceof ShiftableButtonWidget button && button.activatesOn(keyCode);
    }

    @Override
    protected void init() {
        forwardKeyBinding = this.minecraft.options.keyUp;
        leftKeyBinding = this.minecraft.options.keyLeft;
        backKeyBinding = this.minecraft.options.keyDown;
        rightKeyBinding = this.minecraft.options.keyRight;
        jumpKeyBinding = this.minecraft.options.keyJump;
        sneakKeyBinding = this.minecraft.options.keyShift;
        sprintKeyBinding = this.minecraft.options.keySprint;

        //? if forge || neoforge {
        /*if (movementKeyContexts == null) {
            movementKeyContexts = new IdentityHashMap<>();
        }
        for (KeyMapping binding : new KeyMapping[]{forwardKeyBinding, leftKeyBinding, backKeyBinding,
                rightKeyBinding, jumpKeyBinding, sneakKeyBinding, sprintKeyBinding}) {
            // Keep the original context across resize/rebuild calls to init().
            MovementKeyContext context = movementKeyContexts.computeIfAbsent(binding,
                    key -> new MovementKeyContext(key.getKeyConflictContext(),
                            () -> /^? if >=26.2 {^/ this.minecraft.gui.screen() /^?} else {^/ this.minecraft.screen /^?}^/
                                    == this && this.movementAllowed));
            binding.setKeyConflictContext(context);
        }
        *///?}

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

    //? if forge || neoforge {
    /*@Override
    public void removed() {
        if (movementKeyContexts != null) {
            movementKeyContexts.forEach((binding, context) -> binding.setKeyConflictContext(context.original()));
            movementKeyContexts.clear();
        }
        super.removed();
    }

    // Extends only movement bindings' contexts; the loader still checks each binding's modifiers.
    private record MovementKeyContext(IKeyConflictContext original, BooleanSupplier movementActive)
            implements IKeyConflictContext {
        @Override
        public boolean isActive() {
            return movementActive.getAsBoolean() || original.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return original.conflicts(other instanceof MovementKeyContext context ? context.original() : other);
        }
    }
    *///?}

    private void unpressAllMovementKeys() {
        forwardKeyBinding.setDown(false);
        leftKeyBinding.setDown(false);
        backKeyBinding.setDown(false);
        rightKeyBinding.setDown(false);
        jumpKeyBinding.setDown(false);
        sneakKeyBinding.setDown(false);
        sprintKeyBinding.setDown(false);
    }

    boolean testMovementKeysDown(int keyCode) {
        boolean ret = false;
        if (keyCode == forwardKeyCode) {
            forwardKeyBinding.setDown(true);
            KeyMapping.click(forwardKey);
            ret = true;
        } else if (keyCode == leftKeyCode) {
            leftKeyBinding.setDown(true);
            KeyMapping.click(leftKey);
            ret = true;
        } else if (keyCode == backKeyCode) {
            backKeyBinding.setDown(true);
            KeyMapping.click(backKey);
            ret = true;
        } else if (keyCode == rightKeyCode) {
            rightKeyBinding.setDown(true);
            KeyMapping.click(rightKey);
            ret = true;
        } else if (keyCode == jumpKeyCode) {
            jumpKeyBinding.setDown(true);
            KeyMapping.click(jumpKey);
            ret = true;
        } else if (keyCode == sneakKeyCode) {
            sneakKeyBinding.setDown(true);
            KeyMapping.click(sneakKey);
            ret = true;
        } else if (keyCode == sprintKeyCode) {
            sprintKeyBinding.setDown(true);
            KeyMapping.click(sprintKey);
            ret = true;
        }
        return ret;
    }

    private boolean testMovementKeysUp(int keyCode) {
        boolean ret = false;
        if (keyCode == forwardKeyCode) {
            forwardKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == leftKeyCode) {
            leftKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == backKeyCode) {
            backKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == rightKeyCode) {
            rightKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == jumpKeyCode) {
            jumpKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == sneakKeyCode) {
            sneakKeyBinding.setDown(false);
            ret = true;
        } else if (keyCode == sprintKeyCode) {
            sprintKeyBinding.setDown(false);
            ret = true;
        }
        return ret;
    }

    //? if >= 1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClicked) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            return super.mouseClicked(mouseButtonEvent, doubleClicked);
        }
        int button = mouseButtonEvent.button();
        boolean ret = testMovementKeysDown(button);
        boolean ret2 = super.mouseClicked(mouseButtonEvent, doubleClicked);
        return ret || ret2;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouseButtonEvent) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            return super.mouseReleased(mouseButtonEvent);
        }
        int button = mouseButtonEvent.button();
        boolean ret = testMovementKeysUp(button);
        boolean ret2 = super.mouseReleased(mouseButtonEvent);
        return ret || ret2;
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            return super.mouseClicked(mouseX, mouseY, button);
        }
        boolean ret = testMovementKeysDown(button);
        boolean ret2 = super.mouseClicked(mouseX, mouseY, button);
        return ret || ret2;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!movementAllowed) {
            unpressAllMovementKeys();
            return super.mouseReleased(mouseX, mouseY, button);
        }
        boolean ret = testMovementKeysUp(button);
        boolean ret2 = super.mouseReleased(mouseX, mouseY, button);
        return ret || ret2;
    }
    *///?}

    //? if <= 1.20.1 {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double verticalAmount) {
        return this.scrollFocusedPopup(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, verticalAmount);
    }
    *///?} else {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.scrollFocusedPopup(mouseX, mouseY, verticalAmount)
                || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    //?}

    /**
     * Offers the wheel to the focused control's popup first. A popup such as a suggestion list hangs
     * outside its owner, so vanilla would otherwise give the wheel to whatever it covers.
     */
    private boolean scrollFocusedPopup(double mouseX, double mouseY, double verticalAmount) {
        return this.getFocused() instanceof PopupOwner owner
                && owner.scrollPopupIfOver(mouseX, mouseY, verticalAmount);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE && this.dismissFocusedInput()) {
            return true;
        }
        if (!movementAllowed) {
            unpressAllMovementKeys();
            //? if >= 1.21.9 {
            return super.keyPressed(new KeyEvent(keyCode, scanCode, modifiers));
            //?} else {
            /*return super.keyPressed(keyCode, scanCode, modifiers);
            *///?}
        }
        // An activation key that is also bound to movement (Space jumps by default) is handled once:
        // a button reached with Tab or the arrow keys is pressed, otherwise the player moves.
        boolean buttonKey = this.focusedButtonActivatesOn(keyCode);
        boolean ret = !(buttonKey && this.isKeyboardNavigating()) && testMovementKeysDown(keyCode);
        if (buttonKey && ret) {
            return true;
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
        return this.keyPressed(keyEvent.key(), /*? if <26.3 {*/ keyEvent.scancode() /*?} else {*//* keyEvent.keycode() *//*?}*/, keyEvent.modifiers());
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
        boolean ret = testMovementKeysUp(keyCode);
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
        return this.keyReleased(keyEvent.key(), /*? if <26.3 {*/ keyEvent.scancode() /*?} else {*//* keyEvent.keycode() *//*?}*/, keyEvent.modifiers());
    }
    //?}

    /** Players can move while these screens are open, so by default they don't pause singleplayer. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Whether a screen opened from {@code parent} pauses: exactly when {@code parent} does, so a
     * screen reached from the pause menu, such as through a mod list, keeps the game paused.
     */
    static boolean pausesWith(@Nullable Screen parent) {
        return parent != null && parent.isPauseScreen();
    }
}
