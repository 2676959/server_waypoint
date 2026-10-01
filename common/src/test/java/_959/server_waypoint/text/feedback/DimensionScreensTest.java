package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static _959.server_waypoint.text.feedback.Fixtures.TWILIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DimensionScreensTest {
    @TempDir
    Path directory;

    static List<DimensionScreens.DimensionLists> dimensions() {
        return List.of(
                new DimensionScreens.DimensionLists(OVERWORLD, Fixtures.overworldLists()),
                new DimensionScreens.DimensionLists(NETHER, List.of(new WaypointList("Nether Hub", 1, List.of(
                        Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4),
                        Fixtures.waypoint("Fortress", "NF", 0xAA0000, 180, 70, 44),
                        Fixtures.waypoint("Bastion", "BR", 0xFFAA00, -220, 40, 90),
                        Fixtures.waypoint("Blaze Farm", "BF", 0xFFFF55, 175, 72, 60))),
                        new WaypointList("Storage", 1, List.of()))),
                new DimensionScreens.DimensionLists(END, List.of(new WaypointList("End", 1, List.of(
                        Fixtures.waypoint("Main Island", "MI", 0xFF55FF, 0, 60, 0))))),
                new DimensionScreens.DimensionLists(TWILIGHT, List.of(new WaypointList("Bosses", 1, List.of(
                        Fixtures.waypoint("Naga Courtyard", "NC", 0x55FF55, 40, 5, 60),
                        Fixtures.waypoint("Lich Tower", "LT", 0xAAAAAA, 300, 20, -120),
                        Fixtures.waypoint("Hydra Lair", "HL", 0xFF5555, 900, 10, 700))))),
                new DimensionScreens.DimensionLists("aether:the_aether", List.of(new WaypointList("Sky", 1, List.of(
                        Fixtures.waypoint("Bronze Dungeon", "BD", 0xFFAA00, 80, 120, 30),
                        Fixtures.waypoint("Silver Dungeon", "SD", 0xAAAAAA, -300, 140, 210))))),
                new DimensionScreens.DimensionLists("ad_astra:mars", List.of()),
                new DimensionScreens.DimensionLists("undergarden:undergarden", List.of(new WaypointList("Camps", 1, List.of()))));
    }

    /** Everything is loaded except the Aether, which only has waypoint files. */
    static DimensionStyle dims(Viewer viewer) {
        Map<String, String> loaded = new HashMap<>(Fixtures.LOADED);
        loaded.put("ad_astra:mars", "ad_astra:mars");
        loaded.put("undergarden:undergarden", "undergarden:undergarden");
        return DimensionStyle.local(viewer, loaded);
    }

    @Test
    void theDimensionListPutsTheViewersDimensionFirstAndEmptyOnesOnOneLine() {
        Viewer inTwilight = Fixtures.in(Fixtures.player(), TWILIGHT);
        Component list = DimensionScreens.dimensionList(dims(inTwilight), dimensions(), 1, 10);

        assertEquals(List.of(
                "Dimensions  7 on this server",
                "Twilight Forest · 3 ●",
                "Overworld · 14",
                "Nether · 4",
                "End · 1",
                "The Aether · 2",
                "No waypoints yet: Mars · Undergarden",
                "All dimensions · 24"), lines(list));
        assertEquals("/wp list minecraft:overworld", clickOf(list, "Overworld"));
        assertEquals(NamedTextColor.GRAY, colorOf(list, "The Aether"));
        assertTrue(tooltipOf(list, "The Aether").contains("Not loaded"));
        assertEquals("Nether\nminecraft:the_nether\nNether type\n4 waypoints in 2 lists\nClick to open", tooltipOf(list, "Nether"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(list, "No waypoints yet:"));
        assertEquals(NamedTextColor.GRAY, colorOf(list, "Mars"));
        assertEquals("/wp list ad_astra:mars", clickOf(list, "Mars"));
        assertEquals(NamedTextColor.GOLD, colorOf(list, "●"));
        assertEquals("/wp list all", clickOf(list, "All dimensions"));
        ChatAssert.assertFitsChat(list);
    }

    @Test
    void manyDimensionsPageByRows() {
        List<DimensionScreens.DimensionLists> many = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            many.add(new DimensionScreens.DimensionLists("mod:world_" + (char) ('a' + index), List.of(
                    new WaypointList("List", 1, List.of(Fixtures.waypoint("Spot", "S", 0xFFFFFF, 0, 64, 0))))));
        }
        List<String> lines = lines(DimensionScreens.dimensionList(Fixtures.dims(Fixtures.player()), many, 1, 10));

        assertEquals(18, lines.size());
        assertEquals("… 5 more dimensions", lines.get(16));
        assertEquals("All dimensions · 20    ‹ 1/2 ›", lines.get(17));
        assertEquals(List.of("✘ Page 3 does not exist; the last page is 2. Last page"),
                lines(DimensionScreens.dimensionList(Fixtures.dims(Fixtures.player()), many, 3, 10)));
    }

    @Test
    void allDimensionsShowsEveryDimensionWithListsAndOneRowPerList() {
        Component all = DimensionScreens.all(dims(Fixtures.player()), dimensions(), ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "All dimensions ⏷",
                "Overworld · 14 ●",
                "  Home Bases · 3",
                "  Farms · 7",
                "  Exploration · 4",
                "Nether · 4",
                "  Nether Hub · 4",
                "  Storage · 0",
                "End · 1",
                "  End · 1",
                "The Aether · 2",
                "  Sky · 2",
                "Twilight Forest · 3",
                "  Bosses · 3",
                "Undergarden · 0",
                "  Camps · 0",
                "Search"), lines(all));
        assertEquals("/wp list dimensions", clickOf(all, "All dimensions ⏷"));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(all, "Farms"));
        assertEquals("/wp list all search ", clickOf(all, "Search"));
        ChatAssert.assertFitsChat(all);
    }

    @Test
    void aPageThatStartsInsideADimensionRepeatsItsHeading() {
        List<WaypointList> lists = new ArrayList<>();
        for (int index = 1; index <= 9; index++) {
            lists.add(new WaypointList("List " + index, 1, List.of()));
        }
        List<DimensionScreens.DimensionLists> one = List.of(new DimensionScreens.DimensionLists(OVERWORLD, lists));
        Component second = DimensionScreens.all(Fixtures.dims(Fixtures.player()), one, ListQuery.DEFAULT.withPage(2), 2);
        Component first = DimensionScreens.all(Fixtures.dims(Fixtures.player()), one, ListQuery.DEFAULT, 2);

        assertEquals(List.of("All dimensions ⏷", "Overworld (continued) ●", "  List 7 · 0", "  List 8 · 0", "  List 9 · 0",
                "Search    ‹ 2/2 ›"), lines(second));
        assertEquals("… 3 more lists", lines(first).get(8));
        assertEquals("/wp list all page 2", clickOf(first, "… 3 more lists"));
    }

    @Test
    void searchingEveryDimensionGroupsMatchesUnderTheirDimension() {
        WaypointServerCore server = Fixtures.server(this.directory);
        Fixtures.overworldLists().forEach(list -> server.putWaypointList(OVERWORLD, list));
        Viewer player = Fixtures.player();
        ListQuery query = ListQuery.DEFAULT.withSearch("farm");
        Component search = DimensionScreens.allSearch(Fixtures.dims(player), new WaypointQueryEngine(server).queryAll(
                new WaypointQueryEngine.Query("farm", query.sort(), player.position(), player.dimension(), false)), query, 10);

        assertEquals(List.of(
                "All dimensions ⏷",
                "Search \"farm\" · 7 matches · Clear",
                "Overworld ●",
                "  [IF] Iron Farm · Farms · 263 m",
                "  [WF] Wheat Fields · Farms · 63 m",
                "  [CF] Cane Farm · Farms · 78 m",
                "  [MG] Mob Grinder · Farms · 315 m",
                "  [VH] Villager Hall · Farms · 117 m",
                "  [SF] Slime Farm · Farms · 395 m",
                "  [GF] Gold Farm · Farms · 759 m"), lines(search));
        assertEquals("/wp list all", clickOf(search, "Clear"));
    }

    @Test
    void plainTextViewersReadIdsAndNoControls() {
        assertEquals(List.of(
                "Dimensions  7 on this server",
                "Overworld (minecraft:overworld) · 14",
                "Nether (minecraft:the_nether) · 4",
                "End (minecraft:the_end) · 1",
                "The Aether (aether:the_aether) · 2",
                "Twilight Forest (twilightforest:twilight_forest) · 3",
                "No waypoints yet: Mars (ad_astra:mars) · Undergarden (undergarden:undergarden)"),
                lines(DimensionScreens.dimensionList(dims(Fixtures.console()), dimensions(), 1, 10)));
        assertEquals("All dimensions", lines(DimensionScreens.all(dims(Fixtures.console()), dimensions(), ListQuery.DEFAULT, 10)).get(0));
    }
}
