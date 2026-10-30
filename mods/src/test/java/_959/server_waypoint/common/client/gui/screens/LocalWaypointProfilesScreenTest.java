package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.local.LocalWaypointProfiles.Profile;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class LocalWaypointProfilesScreenTest {
    private static final Profile FIRST = new Profile(UUID.fromString("12345678-1111-1111-1111-111111111111"), "Survival World", "/tp @s 0 64 0");
    private static final Profile SECOND = new Profile(UUID.fromString("abcdef12-2222-2222-2222-222222222222"), "Creative", "/tp @s 0 64 0");

    @Test
    void namesAreStrippedAndBlankNamesAreNone() {
        assertEquals("Creative", LocalWaypointProfilesScreen.ProfileNames.clean("  Creative "));
        assertNull(LocalWaypointProfilesScreen.ProfileNames.clean("   "));
        assertEquals("W".repeat(128), LocalWaypointProfilesScreen.ProfileNames.clean(" " + "W".repeat(128) + " "));
        assertNull(LocalWaypointProfilesScreen.ProfileNames.clean("W".repeat(129)));
    }

    @Test
    void conflictsIgnoreCaseAndSpacesButNotTheRenamedProfile() {
        var profiles = List.of(FIRST, SECOND);
        assertEquals(FIRST, LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, " survival WORLD ", null));
        assertNull(LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, "SURVIVAL WORLD", FIRST.id()));
        assertEquals(SECOND, LocalWaypointProfilesScreen.ProfileNames.conflict(profiles, "creative", FIRST.id()));
    }

    @Test
    void configStylePanelCentersItsContentAndLeavesRoomForTitleAndFooter() {
        var layout = LocalWaypointProfilesScreen.ProfileLayout.calculate(800, 450, 420, 14, 230, 15);
        assertEquals(420, layout.width());
        assertEquals(230, layout.height());
        assertEquals(190, layout.x());
        assertTrue(layout.titleY() > 10, "Short content is centered instead of pinned to the top");
        assertEquals(layout.titleY() + 20, layout.y());
        assertEquals(layout.y() + layout.height() + 6, layout.footerY());
        assertTrue(layout.footerY() + 15 <= 440);
    }

    @Test
    void constrainedViewportsClampOneScrollablePanelBetweenTheTitleAndFooter() {
        for (int width : new int[] {320, 400, 480}) {
            var layout = LocalWaypointProfilesScreen.ProfileLayout.calculate(width, 240, 420, 24, 600, 25);
            assertTrue(layout.width() <= width - 20);
            assertTrue(layout.x() >= 10);
            assertTrue(layout.height() >= 33);
            assertTrue(layout.y() >= layout.titleY() + 30);
            assertTrue(layout.y() + layout.height() + 6 <= layout.footerY());
            assertTrue(layout.footerY() + 25 <= 230);
        }
    }
}
