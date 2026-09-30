package _959.server_waypoint.common.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRenderVisibilityTest {
    @Test
    void visibleDuringNormalGameplay() {
        assertTrue(WaypointRenderVisibility.isVisible(true, true, false, false, false));
    }

    @Test
    void hiddenWhileLoadingEvenWhenAWorldAlreadyExists() {
        assertFalse(WaypointRenderVisibility.isVisible(true, true, true, false, false));
        assertFalse(WaypointRenderVisibility.isVisible(true, false, false, false, false));
    }

    @Test
    void hiddenWhenF1HidesTheHud() {
        assertFalse(WaypointRenderVisibility.isVisible(true, true, false, true, false));
    }

    @Test
    void f1OverrideAllowsGameplayButNeverLoadingOrDisabledRendering() {
        assertTrue(WaypointRenderVisibility.isVisible(true, true, false, true, true));
        assertFalse(WaypointRenderVisibility.isVisible(true, true, true, true, true));
        assertFalse(WaypointRenderVisibility.isVisible(true, false, false, true, true));
        assertFalse(WaypointRenderVisibility.isVisible(false, true, false, true, true));
    }

    @Test
    void disablingWaypointsTakesPrecedence() {
        assertFalse(WaypointRenderVisibility.isVisible(false, true, false, false, false));
    }
}
