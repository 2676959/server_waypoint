package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.find;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointRefsTest {
    private static final WaypointList HOME = Fixtures.homeBases();
    private static final SimpleWaypoint MAIN_HOME = HOME.getWaypointByName("Main Home");

    @Test
    void theInitialsTeleportAndTheNameOpensDetails() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals("[MH] Main Home", render(reference));
        assertEquals(TextColor.color(0xFFAA00), colorOf(reference, "[MH]"));
        assertEquals("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(reference, "[MH]"));
        assertEquals(NamedTextColor.WHITE, colorOf(reference, "Main Home"));
        assertEquals("/wp details waypoint minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(reference, "Main Home"));
    }

    @Test
    void tooltipsShowTheDescriptionCoordinatesNetherCoordinatesAndDistance() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, MAIN_HOME);

        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\n25 m away\nClick for details",
                tooltipOf(reference, "Main Home"));
        assertEquals("Teleport to Main Home\n120, 64, -35\n25 m away", tooltipOf(reference, "[MH]"));
    }

    @Test
    void elsewhereTheTooltipSaysWhichDimensionAndNetherWaypointsShowOverworldCoordinates() {
        DimensionStyle inNether = Fixtures.dims(Fixtures.in(Fixtures.player(), NETHER));
        Component tooltip = WaypointRefs.waypointTooltip(inNether, OVERWORLD, MAIN_HOME, null).build();
        SimpleWaypoint hub = Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4);

        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\nIn Overworld", render(tooltip));
        assertEquals(NamedTextColor.GREEN, colorOf(tooltip, "Overworld"));
        assertEquals("Hub\n12, 64, -4\nOverworld 96, 64, -32\n89 m away",
                render(WaypointRefs.waypointTooltip(inNether, NETHER, hub, null).build()));
    }

    @Test
    void withoutTeleportPermissionTheInitialsAreColouredTextWithTheWaypointTooltip() {
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.member()), OVERWORLD, HOME, MAIN_HOME);

        assertNull(clickOf(reference, "[MH]"));
        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\n25 m away", tooltipOf(reference, "[MH]"));
    }

    @Test
    void distancesUseMetresBelowAKilometre() {
        assertEquals("999 m", render(WaypointRefs.distance(Fixtures.player(), OVERWORLD, new WaypointPos(100, 64, 979))));
        assertEquals("1.5 km", render(WaypointRefs.distance(Fixtures.player(), OVERWORLD, new WaypointPos(1600, 64, -20))));
        assertNull(WaypointRefs.distance(Fixtures.player(), NETHER, new WaypointPos(100, 64, -20)));
        assertEquals(NamedTextColor.GRAY, colorOf(WaypointRefs.rowDetail(Fixtures.player(), OVERWORLD, MAIN_HOME.pos()), "25 m"));
        Component coordinates = WaypointRefs.rowDetail(Fixtures.console(), OVERWORLD, MAIN_HOME.pos());
        assertEquals("120, 64, -35", render(coordinates));
        assertEquals(NamedTextColor.GRAY, colorOf(coordinates, "120, 64, -35"));
    }

    @Test
    void formattedDisplayNamesKeepTheirStyleWithoutLeakingIt() {
        SimpleWaypoint gold = new SimpleWaypoint("Gold", "{\"text\":\"Gold\",\"color\":\"gold\",\"italic\":true}", "GF",
                new WaypointPos(0, 64, 0), 0xFFAA00, 0, true, List.of(), "");
        Component line = Chat.concat(WaypointRefs.reference(Fixtures.dims(Fixtures.player()), OVERWORLD, HOME, gold), text(" · next"));

        assertEquals("[GF] Gold · next", render(line));
        assertEquals(NamedTextColor.GOLD, colorOf(line, "Gold"));
        assertNotEquals(TextDecoration.State.TRUE, find(line, " · next").style().decoration(TextDecoration.ITALIC));
        assertEquals("[GF] Gold", render(WaypointRefs.reference(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, gold)));
    }

    @Test
    void plainTextViewersReadTheIdentifierWhenTheDisplayNameDiffers() {
        SimpleWaypoint renamed = new SimpleWaypoint("main_home", "Home", "MH", new WaypointPos(0, 64, 0), 0, 0, true, List.of(), "");
        Component reference = WaypointRefs.reference(Fixtures.dims(Fixtures.console()), OVERWORLD, HOME, renamed);

        assertEquals("[MH] Home (main_home)", render(reference));
        assertTrue(runCommands(reference).isEmpty());
    }

    @Test
    void blankNamesShowTheirIdentifierInQuotes() {
        assertEquals("\"\"", render(WaypointRefs.label("", "")));
        assertEquals("\"Gate\"", render(WaypointRefs.label("", "Gate")));
    }

    @Test
    void listsShowTheirSizeAndTheirIdentifierWhenRenamed() {
        WaypointList renamed = new WaypointList("farms", "Farms", 1, Fixtures.farms().simpleWaypoints());
        Component link = WaypointRefs.listLink(Fixtures.dims(Fixtures.player()), renamed, NamedTextColor.WHITE,
                Click.run("/wp list minecraft:overworld farms"), "wp.hint.open");

        assertEquals("Farms", render(link));
        assertEquals("Farms · 7 waypoints\nfarms\nClick to open", tooltipOf(link, "Farms"));
        assertEquals("Farms · 7 waypoints\nClick to open", render(WaypointRefs.listTooltip(Fixtures.farms(), "wp.hint.open").build()));
        assertEquals("Farms (farms)", render(WaypointRefs.listLink(Fixtures.dims(Fixtures.console()), renamed,
                NamedTextColor.WHITE, Click.run("/wp"), null)));
    }
}
