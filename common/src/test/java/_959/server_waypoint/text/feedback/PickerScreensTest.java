package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.ChatAssert;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PickerScreensTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");
    private static final String EDIT = "/wp edit waypoint minecraft:overworld \"Home Bases\" \"Main Home\" ";

    @Test
    void theColourPickerOffersTheNamedColoursRandomAndCustom() {
        Component picker = PickerScreens.color(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals(List.of(
                "Color · [MH] Main Home   now ■ #FFAA00",
                "█ █ █ █ █ █ █ █ █ █ █ █ █ █ █ █",
                "Random · Custom… · Back"), lines(picker));
        assertEquals(EDIT + "set color black", clickOf(picker, "█"));
        assertEquals("Black\n#000000", tooltipOf(picker, "█"));
        assertEquals(TextColor.color(0x000000), colorOf(picker, "█"));
        assertEquals(EDIT + "set color random", clickOf(picker, "Random"));
        assertEquals(EDIT + "set color FFAA00", clickOf(picker, "Custom…"));
        assertEquals(NamedTextColor.YELLOW, colorOf(picker, "Custom…"));
        assertEquals("/wp details waypoint minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(picker, "Back"));
        assertTrue(runCommands(picker).contains(EDIT + "set color white"));
        ChatAssert.assertFitsChat(picker);
    }

    @Test
    void theFacingPickerOffersTheFourDirectionsAndYours() {
        Component picker = PickerScreens.facing(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals(List.of(
                "Facing · [MH] Main Home   now 0° (south)",
                "South 0° · West 90° · North 180° · East -90°",
                "Yours 37° · Custom… · Back"), lines(picker));
        assertEquals(EDIT + "set yaw -90", clickOf(picker, "East -90°"));
        assertEquals("Face east", tooltipOf(picker, "East -90°"));
        assertEquals(EDIT + "set yaw 37", clickOf(picker, "Yours 37°"));
        assertEquals(EDIT + "set yaw 0", clickOf(picker, "Custom…"));
    }

    @Test
    void plainTextViewersReadTheAcceptedValues() {
        Component color = PickerScreens.color(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME);
        Component facing = PickerScreens.facing(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals("  Accepted values: black, dark_blue, dark_green, dark_aqua, dark_red, dark_purple, gold, gray, "
                        + "dark_gray, blue, green, aqua, red, light_purple, yellow, white, random, or a hex code such as 39C5BB",
                lines(color).get(1));
        assertEquals("  Accepted values: a number of degrees, such as 0 (south), 90 (west), 180 (north) or -90 (east)",
                lines(facing).get(1));
        assertEquals(NamedTextColor.GRAY, colorOf(color, "Accepted values: black"));
        assertEquals(NamedTextColor.GRAY, colorOf(facing, "Accepted values: a number"));
    }

    @Test
    void yawValuesNameTheFourDirections() {
        assertEquals("180° (north)", render(PickerScreens.yawValue(180)));
        assertEquals("-180° (north)", render(PickerScreens.yawValue(-180)));
        assertEquals("90° (west)", render(PickerScreens.yawValue(90)));
        assertEquals("37°", render(PickerScreens.yawValue(37)));
    }

    @Test
    void theAddPickerListsThisDimensionsLists() {
        Component picker = PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, Fixtures.overworldLists(), 1, 10);

        assertEquals(List.of(
                "Add a waypoint at 100, 64, -20",
                "Into  Home Bases · Farms · Exploration",
                "New list · Back"), lines(picker));
        assertEquals("/wp add ~ ~ ~ \"Home Bases\" ", clickOf(picker, "Home Bases"));
        assertEquals("Add it to Home Bases\nType its name, then press Enter", tooltipOf(picker, "Home Bases"));
        assertEquals(NamedTextColor.GREEN, colorOf(picker, "Farms"));
        assertEquals("/wp add minecraft:overworld ", clickOf(picker, "New list"));
        assertEquals("/wp", clickOf(picker, "Back"));
        assertEquals(List.of("Add a waypoint at 100, 64, -20", "No lists yet. New list"),
                lines(PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, List.of(), 1, 10)));
    }

    @Test
    void manyListsGetALineEachAndPages() {
        List<WaypointList> lists = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            lists.add(new WaypointList("List " + index, 1, List.of()));
        }
        Component picker = PickerScreens.add(Fixtures.dims(Fixtures.player()), OVERWORLD, lists, 1, 10);
        List<String> lines = lines(picker);

        assertEquals("Into", lines.get(1));
        assertEquals("  List 1", lines.get(2));
        assertEquals("… 15 more lists", lines.get(17));
        assertEquals("New list · Back    ‹ 1/2 ›", lines.get(18));
        assertEquals("/wp add page 2", clickOf(picker, "… 15 more lists"));
        ChatAssert.assertFitsChat(picker);
    }
}
