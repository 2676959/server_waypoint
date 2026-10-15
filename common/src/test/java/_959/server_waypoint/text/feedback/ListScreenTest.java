package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.text.chat.ChatAssert;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListScreenTest {
    @TempDir
    static Path directory;
    private static WaypointQueryEngine engine;
    private static WaypointServerCore server;

    @BeforeAll
    static void load() {
        server = Fixtures.server(directory);
        Fixtures.overworldLists().forEach(list -> server.putWaypointList(OVERWORLD, list));
        server.putWaypointList(NETHER, new WaypointList("Storage", 1, List.of()));
        List<SimpleWaypoint> many = new ArrayList<>();
        for (int index = 1; index <= 23; index++) {
            many.add(Fixtures.waypoint("Pillar " + index, "P" + index, 0xFFFFFF, index, 64, 0));
        }
        server.putWaypointList("minecraft:the_end", new WaypointList("Pillars", 1, many));
        engine = new WaypointQueryEngine(server);
    }

    private static WaypointQueryEngine.Query engineQuery(Viewer viewer, ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), viewer.position(), viewer.dimension(), query.descending());
    }

    static Component overworld(Viewer viewer, ListQuery query) {
        return ListScreen.dimension(Fixtures.dims(viewer), OVERWORLD, new ListScreen.Totals(3, 14),
                engine.queryDimension(OVERWORLD, engineQuery(viewer, query)), query, 10);
    }

    static Component farms(Viewer viewer, ListQuery query) {
        WaypointList farms = server.getWaypointFileManager(OVERWORLD).getWaypointListByName("Farms");
        return ListScreen.list(Fixtures.dims(viewer), OVERWORLD, farms,
                engine.queryList(OVERWORLD, "Farms", engineQuery(viewer, query)), query, 10);
    }

    @Test
    void theTreeShowsEveryListWithItsFirstWaypoints() {
        Component tree = overworld(Fixtures.player(), ListQuery.DEFAULT);

        assertEquals(List.of(
                "Overworld ⏷  3 lists · 14 waypoints · All",
                "Home Bases  +",
                "  [MH] Main Home · 25 m",
                "  [GM] Gem Mine · 591 m",
                "  [SV] Spawn Village · 102 m",
                "Farms  +",
                "  [IF] Iron Farm · 263 m",
                "  [WF] Wheat Fields · 63 m",
                "  [CF] Cane Farm · 78 m",
                "  … 4 more",
                "Exploration  +",
                "  [OM] Ocean Monument · 1.3 km",
                "  [DT] Desert Temple · 1.1 km",
                "  [SH] Stronghold · 2.5 km",
                "  [WM] Woodland Mansion · 4.1 km",
                "Lists · Tree · Flat · Search · New list",
                "Sort Default · Name · Distance · Color"), lines(tree));
        ChatAssert.assertFitsChat(tree);
    }

    @Test
    void theHeaderSwitchesDimensionsAndListHeadingsOpenDetails() {
        Component tree = overworld(Fixtures.player(), ListQuery.DEFAULT);

        assertEquals("/wp list dimensions", clickOf(tree, "Overworld ⏷"));
        assertEquals(NamedTextColor.GREEN, colorOf(tree, "Overworld ⏷"));
        assertEquals("/wp list all", clickOf(tree, "All"));
        assertEquals("/wp details list minecraft:overworld \"Home Bases\"", clickOf(tree, "Home Bases"));
        assertEquals("Home Bases · 3 waypoints\nClick for list details", tooltipOf(tree, "Home Bases"));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(tree, "… 4 more"));
        assertEquals("Open Farms\nAll 7 waypoints, 10 per page", tooltipOf(tree, "… 4 more"));
        assertTrue(suggestions(tree).contains("/wp add ~ ~ ~ \"Home Bases\" "));
        assertEquals("/wp list minecraft:overworld view lists", clickOf(tree, "Lists"));
        assertEquals("/wp list minecraft:overworld sort name view tree", clickOf(tree, "Name"));
    }

    @Test
    void theListsViewHasOneLinePerList() {
        Component lists = overworld(Fixtures.player(), ListQuery.DEFAULT.withView(ListView.LISTS));

        assertEquals(List.of(
                "Overworld ⏷  3 lists · 14 waypoints · All",
                "Home Bases · 3  +",
                "Farms · 7  +",
                "Exploration · 4  +",
                "Lists · Tree · Flat · Search · New list"), lines(lists));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(lists, "Farms"));
        assertEquals(NamedTextColor.GOLD, colorOf(lists, "Lists"));
    }

    @Test
    void flatRowsNameTheirListAndPageByTheLimit() {
        Component flat = overworld(Fixtures.player(), ListQuery.DEFAULT.withView(ListView.FLAT));
        List<String> lines = lines(flat);

        assertEquals("[MH] Main Home · Home Bases · 25 m", lines.get(1));
        assertEquals(10, lines.stream().filter(line -> line.startsWith("[")).count());
        assertEquals("… 4 more waypoints", lines.get(11));
        assertEquals("Sort Default · Name · Distance · Color    ‹ 1/2 ›", lines.get(lines.size() - 1));
        assertEquals("/wp list minecraft:overworld view flat page 2", clickOf(flat, "… 4 more waypoints"));
        assertEquals("/wp list minecraft:overworld \"Home Bases\"", clickOf(flat, "Home Bases"));
        assertEquals(NamedTextColor.GRAY, colorOf(flat, "Home Bases"));
    }

    @Test
    void searchesShowTheirMatchesAndHowToClearThem() {
        Component search = overworld(Fixtures.player(), ListQuery.DEFAULT.withSearch("farm"));
        Component nothing = overworld(Fixtures.player(), ListQuery.DEFAULT.withSearch("zzz"));

        assertEquals("Search \"farm\" · 7 matches · Clear", lines(search).get(1));
        assertEquals("Farms  +", lines(search).get(2));
        assertEquals("/wp list minecraft:overworld", clickOf(search, "Clear"));
        assertEquals(List.of("Overworld ⏷  3 lists · 14 waypoints · All", "Nothing matches \"zzz\". Clear search"), lines(nothing));
    }

    @Test
    void distanceSortingNeedsTheViewerInTheDimension() {
        Component elsewhere = overworld(Fixtures.in(Fixtures.player(), NETHER), ListQuery.DEFAULT);

        assertEquals(NamedTextColor.DARK_GRAY, colorOf(elsewhere, "Distance"));
        assertEquals("Distance needs you in Overworld", tooltipOf(elsewhere, "Distance"));
        assertEquals("  [MH] Main Home", lines(elsewhere).get(2));
        assertTrue(suggestions(elsewhere).contains("/wp add minecraft:overworld \"Home Bases\" "));
    }

    @Test
    void aSingleListHasItsBreadcrumbActionsAndSortRow() {
        Component farms = farms(Fixtures.player(), ListQuery.DEFAULT.withSort(SortMode.NAME));

        assertEquals(List.of(
                "Overworld › Farms  7 waypoints · All",
                "[CF] Cane Farm · 78 m",
                "[GF] Gold Farm · 759 m",
                "[IF] Iron Farm · 263 m",
                "[MG] Mob Grinder · 315 m",
                "[SF] Slime Farm · 395 m",
                "[VH] Villager Hall · 117 m",
                "[WF] Wheat Fields · 63 m",
                "Search · Add here",
                "Sort Default · Name ↑ · Distance · Color"), lines(farms));
        assertEquals("/wp list minecraft:overworld", clickOf(farms, "Overworld"));
        assertEquals(NamedTextColor.GOLD, colorOf(farms, "Farms"));
        assertEquals("/wp details list minecraft:overworld Farms", clickOf(farms, "Farms"));
        assertEquals("/wp list minecraft:overworld Farms search ", clickOf(farms, "Search"));
        assertEquals("/wp add ~ ~ ~ Farms ", clickOf(farms, "Add here"));
        assertEquals("/wp list minecraft:overworld Farms sort name order descending", clickOf(farms, "Name"));
    }

    @Test
    void emptyStatesOfferTheNextStep() {
        Viewer player = Fixtures.player();
        WaypointList storage = server.getWaypointFileManager(NETHER).getWaypointListByName("Storage");
        Component emptyList = ListScreen.list(Fixtures.dims(player), NETHER, storage,
                engine.queryList(NETHER, "Storage", engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);
        Component noLists = ListScreen.dimension(Fixtures.dims(player), "ad_astra:mars", new ListScreen.Totals(0, 0),
                WaypointQueryEngine.QueryResult.empty(engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);
        Component storageTree = ListScreen.dimension(Fixtures.dims(player), NETHER, new ListScreen.Totals(1, 0),
                engine.queryDimension(NETHER, engineQuery(player, ListQuery.DEFAULT)), ListQuery.DEFAULT, 10);

        assertEquals(List.of("Nether › Storage · All", "No waypoints yet. Add waypoint · Remove list"), lines(emptyList));
        assertEquals("/wp remove minecraft:the_nether Storage", clickOf(emptyList, "Remove list"));
        assertEquals("Remove this empty list\nPress Enter to confirm", tooltipOf(emptyList, "Remove list"));
        assertEquals(List.of("Mars ⏷ · All", "No lists yet. New list"), lines(noLists));
        assertEquals(List.of("Nether ⏷  1 list · 0 waypoints · All", "Storage  +", "  No waypoints yet",
                "Lists · Tree · New list"), lines(storageTree));
    }

    @Test
    void pagersCloseTheLastRowAndMissingPagesLinkToTheLastOne() {
        Viewer player = Fixtures.player();
        Component end = ListScreen.list(Fixtures.dims(player), "minecraft:the_end",
                server.getWaypointFileManager("minecraft:the_end").getWaypointListByName("Pillars"),
                engine.queryList("minecraft:the_end", "Pillars", engineQuery(player, ListQuery.DEFAULT.withPage(3))),
                ListQuery.DEFAULT.withPage(3), 10);
        Component missing = farms(player, ListQuery.DEFAULT.withPage(5));

        assertEquals("Sort Default · Name · Distance · Color    « ‹ 3/3 › »", lines(end).get(lines(end).size() - 1));
        assertEquals(List.of("✘ Page 5 does not exist; the last page is 1. Last page"), lines(missing));
        assertEquals("/wp list minecraft:overworld Farms", clickOf(missing, "Last page"));
    }

    @Test
    void plainTextViewersReadCoordinatesAndTheCommandsThatContinue() {
        Component tree = overworld(Fixtures.console(), ListQuery.DEFAULT);
        List<String> lines = lines(tree);

        assertEquals("Overworld (minecraft:overworld)  3 lists · 14 waypoints", lines.get(0));
        assertEquals("Home Bases", lines.get(1));
        assertEquals("  [MH] Main Home · 120, 64, -35", lines.get(2));
        assertEquals("  … 4 more: /wp list minecraft:overworld Farms", lines.get(9));
        assertEquals(NamedTextColor.AQUA, colorOf(tree, "/wp list minecraft:overworld Farms"));
        assertFalse(String.join("\n", lines).contains("Sort"));
        assertTrue(runCommands(tree).isEmpty());
    }

    @Test
    void namesThatNeedQuotingAndTheEmptyIdentifierStayClickable() {
        Viewer player = Fixtures.player();
        WaypointList search = new WaypointList("search", 1, List.of(Fixtures.waypoint("Gate", "G", 0xFFFFFF, 0, 64, 0)));
        WaypointList empty = new WaypointList("", 1, List.of(Fixtures.waypoint("", "E", 0xFFFFFF, 0, 64, 0)));
        WaypointQueryEngine.QueryResult result = new WaypointQueryEngine.QueryResult(List.of(
                new WaypointQueryEngine.DimensionResult("test:dim", List.of(
                        new WaypointQueryEngine.ListResult(search, search.simpleWaypoints(), true),
                        new WaypointQueryEngine.ListResult(empty, empty.simpleWaypoints(), true)))),
                engineQuery(player, ListQuery.DEFAULT));
        Component lists = ListScreen.dimension(Fixtures.dims(player), "test:dim", new ListScreen.Totals(2, 2), result,
                ListQuery.DEFAULT.withView(ListView.LISTS), 10);
        Component tree = ListScreen.dimension(Fixtures.dims(player), "test:dim", new ListScreen.Totals(2, 2), result,
                ListQuery.DEFAULT, 10);

        assertEquals("/wp list test:dim \"search\"", clickOf(lists, "search"));
        assertEquals("/wp list test:dim \"\"", clickOf(lists, "\"\""));
        assertEquals("/wp details list test:dim \"\"", clickOf(tree, "\"\""));
        assertTrue(runCommands(tree).contains("/wp details waypoint test:dim \"\" \"\""));
    }
}
