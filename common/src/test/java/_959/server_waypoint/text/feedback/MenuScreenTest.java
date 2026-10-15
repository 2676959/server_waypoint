package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.ChatAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuScreenTest {
    private static PlacedWaypoint mainHome() {
        var list = Fixtures.homeBases();
        return new PlacedWaypoint(OVERWORLD, list, list.getWaypointByName("Main Home"));
    }

    @Test
    void everythingAPlayerCanReachIsOneClickAway() {
        Component menu = MenuScreen.menu(Fixtures.player(), Fixtures.dims(Fixtures.player()), mainHome(), true);

        assertEquals(List.of(
                "Server Waypoint   Open GUI · Help · Reload",
                "Browse",
                "  This dimension · All · Remote · Search",
                "Create",
                "  Waypoint here · List",
                "Travel",
                "  Navigation to [MH] Main Home · Stop",
                "Transfer",
                "  Download · Upload"), lines(menu));
        assertEquals("/wp_gui", clickOf(menu, "Open GUI"));
        assertEquals("/wp help", clickOf(menu, "Help"));
        assertEquals("/wp reload", clickOf(menu, "Reload"));
        assertEquals("/wp list", clickOf(menu, "This dimension"));
        assertEquals("/wp list all", clickOf(menu, "All"));
        assertEquals("/wp remote", clickOf(menu, "Remote"));
        assertEquals("/wp list all search ", clickOf(menu, "Search"));
        assertEquals("/wp add", clickOf(menu, "Waypoint here"));
        assertEquals("/wp add minecraft:overworld ", clickOf(menu, "List"));
        assertEquals("/wp navigate", clickOf(menu, "Navigation"));
        assertEquals("/wp navigate disable", clickOf(menu, "Stop"));
        assertEquals("/wp download", clickOf(menu, "Download"));
        assertEquals("/wp upload", clickOf(menu, "Upload"));
        ChatAssert.assertFitsChat(menu);
    }

    @Test
    void colorsFollowTheVisualLanguage() {
        Component menu = MenuScreen.menu(Fixtures.player(), Fixtures.dims(Fixtures.player()), mainHome(), true);

        assertEquals(NamedTextColor.GOLD, colorOf(menu, "Server Waypoint"));
        assertEquals(NamedTextColor.AQUA, colorOf(menu, "Open GUI"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, "Help"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, "Browse"));
        assertEquals(NamedTextColor.GREEN, colorOf(menu, "Waypoint here"));
        assertEquals(NamedTextColor.LIGHT_PURPLE, colorOf(menu, "Navigation"));
        assertEquals(NamedTextColor.GRAY, colorOf(menu, " to "));
        assertEquals(NamedTextColor.RED, colorOf(menu, "Stop"));
        assertEquals("Reload config and translations\nPress Enter to confirm", ChatAssert.tooltipOf(menu, "Reload"));
        assertEquals("Waypoints in Overworld", ChatAssert.tooltipOf(menu, "This dimension"));
    }

    @Test
    void controlsTheViewerCannotUseAreLeftOut() {
        Component menu = MenuScreen.menu(Fixtures.member(), Fixtures.dims(Fixtures.member()), null, false);

        assertEquals(List.of(
                "Server Waypoint   Help",
                "Browse",
                "  This dimension · All · Search",
                "Travel",
                "  Navigation"), lines(menu));
    }
}
