package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.widgets.IconButton;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WidgetThemeConfigScreenClickTest {
    @Test
    void aCommitThatWrapsTheStatusKeepsTheClickedButtonInPlaceUntilDispatchFinishes() {
        AtomicInteger presses = new AtomicInteger();
        IconButton button = new IconButton(0, 0, 50, 11, Component.empty(), null, presses::incrementAndGet);
        button.setPosition(318, 224);
        button.active = false;
        WidgetThemeConfigScreen.ClickDispatch clicks = new WidgetThemeConfigScreen.ClickDispatch(() -> {
            // At 378x245, two status lines grow the footer from 11 to 18 pixels.
            var layout = WidgetThemeEditorLayout.arrange(378, 245, 120, 162, 11, width -> 18);
            button.setPosition(button.getX(), layout.buttons().y() + (layout.buttons().height() - 11) / 2);
        });

        assertTrue(clicks.dispatch(() -> {
            // Finishing six ARGB digits activates Save and requests the new status layout.
            button.active = true;
            clicks.requestLayout();
            // Use the real hit box and callback without the click sound, which requires a game client.
            if (!button.isMouseOver(343, 233)) {
                return false;
            }
            button.onClick(343, 233);
            return true;
        }));

        assertEquals(1, presses.get(), "the click on the old bottom edge still reaches Save");
        assertEquals(220, button.getY(), "the wrapped status is laid out after dispatch");
    }

    @Test
    void aStatusOutsideClickDispatchIsLaidOutImmediately() {
        AtomicInteger layouts = new AtomicInteger();
        WidgetThemeConfigScreen.ClickDispatch clicks = new WidgetThemeConfigScreen.ClickDispatch(layouts::incrementAndGet);

        clicks.requestLayout();

        assertEquals(1, layouts.get());
    }

    @Test
    void anInterruptedDispatchFlushesTheLayoutAndDoesNotLeaveFutureLayoutsDeferred() {
        AtomicInteger layouts = new AtomicInteger();
        WidgetThemeConfigScreen.ClickDispatch clicks = new WidgetThemeConfigScreen.ClickDispatch(layouts::incrementAndGet);

        assertThrows(IllegalStateException.class, () -> clicks.dispatch(() -> {
            clicks.requestLayout();
            assertEquals(0, layouts.get());
            throw new IllegalStateException("interrupted click");
        }));
        assertEquals(1, layouts.get());
        clicks.requestLayout();
        assertEquals(2, layouts.get());
    }
}
