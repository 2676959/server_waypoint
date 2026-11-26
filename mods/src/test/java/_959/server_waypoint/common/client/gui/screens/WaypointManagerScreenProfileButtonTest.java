package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.TestFont;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The text of the manager's Local profile button. Tests load no translations, so a label is as wide as
 * its key: "waypoint.profile.current" is 24 characters of 6 pixels.
 */
class WaypointManagerScreenProfileButtonTest {
    private static final TestFont FONT = new TestFont();

    @Test
    void theButtonNamesTheActiveProfileAndItsTooltipTheScreenItOpens() {
        WaypointManagerScreen.ProfileButtonText text = WaypointManagerScreen.profileButtonText(FONT, "Default", 400);

        assertEquals(Component.translatable("waypoint.profile.current", "Default"), text.label());
        assertEquals(Component.translatable("waypoint.profile.manage"), text.tooltip());
    }

    @Test
    void withoutAnActiveProfileTheButtonNamesTheProfilesScreen() {
        WaypointManagerScreen.ProfileButtonText text = WaypointManagerScreen.profileButtonText(FONT, null, 400);

        assertEquals(Component.translatable("waypoint.profile.manage"), text.label());
    }

    @Test
    void aLabelTooWideIsCutAndItsTooltipKeepsTheWholeLabel() {
        WaypointManagerScreen.ProfileButtonText text = WaypointManagerScreen.profileButtonText(FONT, "Default", 60);

        assertEquals(60, FONT.width(text.label()));
        assertTrue(text.label().getString().endsWith("…"));
        assertEquals(Component.translatable("waypoint.profile.current", "Default"), text.tooltip());
    }
}
