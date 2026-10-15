package _959.server_waypoint.common.client.gui.widgets;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ScrollbarFadeTest {
    private final ScrollbarFade fade = new ScrollbarFade();

    private void opacity(boolean show, long millis, float expected) {
        assertEquals(expected, fade.update(show, millis * 1_000_000L), 0.0001F);
    }

    @Test
    void startsHiddenAndFadesInWhileHovered() {
        opacity(false, 0, 0);
        opacity(true, 100, 0);
        opacity(true, 175, 0.5F);
        opacity(true, 250, 1);
        opacity(true, 2000, 1);
    }

    @Test
    void waitsAfterLeavingThenFadesOut() {
        opacity(true, 0, 0);
        opacity(true, 150, 1);
        opacity(false, 200, 1);
        opacity(false, 699, 1);
        opacity(false, 700, 1);
        opacity(false, 850, 0.5F);
        opacity(false, 1000, 0);
        opacity(false, 2000, 0);
    }

    @Test
    void rehoverReversesFromCurrentOpacityWithoutJumping() {
        opacity(true, 0, 0);
        opacity(false, 200, 1);
        opacity(true, 850, 0.5F);
        opacity(true, 925, 0.75F);
        opacity(true, 1000, 1);
    }

    @Test
    void leavingDuringFadeInHoldsCurrentOpacity() {
        opacity(true, 0, 0);
        opacity(false, 75, 0.5F);
        opacity(false, 575, 0.5F);
        opacity(false, 725, 0.25F);
        opacity(false, 875, 0);
    }

    @Test
    void resetImmediatelyHidesAndNextHoverStartsFresh() {
        opacity(true, 0, 0);
        opacity(true, 150, 1);
        fade.reset();
        opacity(false, 200, 0);
        opacity(true, 300, 0);
        opacity(true, 375, 0.5F);
    }
}
