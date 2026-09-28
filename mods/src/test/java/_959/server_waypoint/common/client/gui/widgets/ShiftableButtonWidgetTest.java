package _959.server_waypoint.common.client.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.concurrent.atomic.AtomicInteger;
//? if >= 1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftableButtonWidgetTest {
    @ParameterizedTest
    @ValueSource(ints = {InputConstants.KEY_RETURN, InputConstants.KEY_SPACE, InputConstants.KEY_NUMPADENTER})
    void activationKeyPressesTheButtonOnceWithTheClickSound(int keyCode) {
        ButtonProbe probe = new ButtonProbe();

        assertTrue(probe.button.keyPressed(keyCode, 0, 0));
        assertEquals(1, probe.presses.get());
        assertEquals(1, probe.clickSounds.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB, InputConstants.KEY_UP, InputConstants.KEY_W})
    void otherKeysLeaveTheButtonAlone(int keyCode) {
        ButtonProbe probe = new ButtonProbe();

        assertFalse(probe.button.keyPressed(keyCode, 0, 0));
        assertEquals(0, probe.presses.get());
        assertEquals(0, probe.clickSounds.get());
    }

    @Test
    void inactiveButtonIgnoresActivationKeys() {
        ButtonProbe probe = new ButtonProbe();
        probe.button.active = false;

        assertFalse(probe.button.keyPressed(InputConstants.KEY_RETURN, 0, 0));
        assertEquals(0, probe.presses.get());
        assertEquals(0, probe.clickSounds.get());
    }

    @Test
    void hiddenButtonIgnoresActivationKeys() {
        ButtonProbe probe = new ButtonProbe();
        probe.button.visible = false;

        assertFalse(probe.button.keyPressed(InputConstants.KEY_SPACE, 0, 0));
        assertEquals(0, probe.presses.get());
        assertEquals(0, probe.clickSounds.get());
    }

    @Test
    void leftClickRunsTheSamePressAction() {
        ButtonProbe probe = new ButtonProbe();

        probe.button.onClick(8, 8);

        assertEquals(1, probe.presses.get());
    }

    //? if >= 1.21.9 {
    @Test
    void keyEventReachesTheSameActivation() {
        ButtonProbe probe = new ButtonProbe();

        assertTrue(probe.button.keyPressed(new KeyEvent(InputConstants.KEY_NUMPADENTER, 0, 0)));
        assertFalse(probe.button.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
        assertEquals(1, probe.presses.get());
    }
    //?}

    /** These buttons read the client font when constructed, so only their shared base is checked. */
    @ParameterizedTest
    @ValueSource(classes = {TranslucentButton.class, ToggleButton.class})
    void textAndToggleButtonsShareKeyboardActivation(Class<?> buttonType) {
        assertTrue(ShiftableButtonWidget.class.isAssignableFrom(buttonType));
    }

    /** An icon button needs no client resources, so it stands in for every button widget. */
    private static final class ButtonProbe {
        private final AtomicInteger presses = new AtomicInteger();
        private final AtomicInteger clickSounds = new AtomicInteger();
        private final IconButton button = new IconButton(
                0, 0, 16, 16, Component.literal("Button"), null, this.presses::incrementAndGet
        ) {
            @Override
            protected void playClickSound() {
                clickSounds.incrementAndGet();
            }
        };
    }
}
