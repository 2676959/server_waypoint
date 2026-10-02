package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.lines;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.runCommands;
import static _959.server_waypoint.text.chat.ChatAssert.suggestions;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.NETHER;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteScreensTest {
    private static final String OW = "\"minecraft:overworld\"";
    private static final Server SURVIVAL = server("survival", "Survival", RemoteCatalogState.AVAILABLE, Map.of(
            OVERWORLD, Fixtures.overworldLists(),
            NETHER, List.of(new WaypointList("Nether Hub", 1, List.of(
                    Fixtures.waypoint("Hub", "NH", 0xFF5555, 12, 64, -4),
                    Fixtures.waypoint("Fortress", "NF", 0xAA0000, 40, 70, 90),
                    Fixtures.waypoint("Bastion", "NB", 0x555555, -80, 60, 30)))),
            END, List.of(new WaypointList("End", 1, List.of(Fixtures.waypoint("Gateway", "EG", 0xAA00AA, 100, 70, 0))))));
    private static final Server CREATIVE = server("creative-1", "Creative Plots", RemoteCatalogState.STALE, Map.of(
            OVERWORLD, List.of(new WaypointList("Plots", 1, List.of(
                    Fixtures.waypoint("Plot A", "PA", 0x55FF55, 0, 64, 0),
                    Fixtures.waypoint("Plot B", "PB", 0x55FF55, 32, 64, 0),
                    Fixtures.waypoint("Pumpkin Farm", "PF", 0xFFAA00, 64, 64, 0),
                    Fixtures.waypoint("Sky Farm", "SF", 0x55FFFF, 96, 120, 0))))));
    private static final Server HALLOWEEN = server("halloween", "Halloween Event", RemoteCatalogState.UNAUTHORIZED, Map.of());
    private static final Server LOBBY = server("lobby", "Lobby", RemoteCatalogState.UNAVAILABLE, Map.of());
    private static final Server SKYBLOCK = server("skyblock", "Skyblock", RemoteCatalogState.AVAILABLE, Map.of());
    private static final List<Server> SERVERS = List.of(SURVIVAL, LOBBY, CREATIVE, SKYBLOCK, HALLOWEEN);

    private static Server server(String id, String name, RemoteCatalogState state, Map<String, List<WaypointList>> dimensions) {
        return new Server(new RemoteServerId(id), name, state, dimensions);
    }

    @Test
    void thePickerShowsServersWithWaypointsFirstAndTheirStates() {
        Component picker = RemoteScreens.picker(Fixtures.player(), SERVERS, 1, 10);

        assertEquals(List.of(
                "Remote servers  5 servers connected",
                "● Creative Plots · 4",
                "● Survival · 18",
                "● Halloween Event · no access",
                "● Lobby · unreachable",
                "● Skyblock · nothing published",
                "All servers · 22"), lines(picker));
        assertEquals(NamedTextColor.YELLOW, colorOf(picker, "●"));
        assertEquals("Stale\nTeleporting is off until it refreshes", tooltipOf(picker, "●"));
        assertEquals("/wp remote list survival", clickOf(picker, "Survival"));
        assertEquals(NamedTextColor.WHITE, colorOf(picker, "Survival"));
        assertNull(clickOf(picker, "Lobby"));
        assertEquals(NamedTextColor.GRAY, colorOf(picker, "Lobby"));
        assertEquals(NamedTextColor.GRAY, colorOf(picker, "Skyblock"));
        assertEquals("/wp remote list", clickOf(picker, "All servers"));
    }

    @Test
    void plainTextPickersNameEveryServerAndItsState() {
        assertEquals(List.of(
                "Remote servers  5 servers connected",
                "● Creative Plots (creative-1) · 4 · stale",
                "● Survival (survival) · 18",
                "● Halloween Event (halloween) · no access",
                "● Lobby (lobby) · unreachable",
                "● Skyblock (skyblock) · nothing published"), lines(RemoteScreens.picker(Fixtures.console(), SERVERS, 1, 10)));
    }

    @Test
    void thePickerPagesWithTheDimensionListBudget() {
        List<Server> many = IntStream.range(0, 8)
                .mapToObj(index -> server("s" + index, "Server " + index, RemoteCatalogState.AVAILABLE, Map.of())).toList();
        Component first = RemoteScreens.picker(Fixtures.player(), many, 1, 1);

        assertEquals(9, lines(first).size());
        assertEquals("/wp remote page 2", clickOf(first, "… 2 more servers"));
        assertEquals("✘ Page 3 does not exist; the last page is 2. Last page",
                render(RemoteScreens.picker(Fixtures.player(), many, 3, 1)));
    }

    @Test
    void allServersListEachServerWithItsDimensions() {
        Component all = RemoteScreens.allServers(Fixtures.player(), SERVERS, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "All servers ⏷",
                "Creative Plots ● · 4",
                "  Overworld · 4",
                "Survival ● · 18",
                "  Overworld · 14",
                "  Nether · 3",
                "  End · 1",
                "Halloween Event ● · no access",
                "Lobby ● · unreachable",
                "Skyblock ● · nothing published",
                "Search"), lines(all));
        assertEquals("/wp remote", clickOf(all, "All servers ⏷"));
        assertEquals("/wp remote list survival \"minecraft:the_nether\"", clickOf(all, "Nether"));
        assertEquals(NamedTextColor.RED, colorOf(all, "Nether"));
        assertEquals("/wp remote list search ", suggestions(all).get(0));
    }

    @Test
    void aServerShowsItsDimensionsAndLists() {
        Component server = RemoteScreens.server(Fixtures.player(), SURVIVAL, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival ⏷  3 dimensions · 18 waypoints",
                "Overworld · 14",
                "  Home Bases · 3",
                "  Farms · 7",
                "  Exploration · 4",
                "Nether · 3",
                "  Nether Hub · 3",
                "End · 1",
                "  End · 1",
                "Search"), lines(server));
        assertEquals("/wp remote", clickOf(server, "Survival ⏷"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(server, "Farms"));
        assertEquals("/wp remote list survival search ", suggestions(server).get(0));
    }

    @Test
    void unreadableAndEmptyServersSaySo() {
        assertEquals(List.of("● Lobby ⏷", "Lobby can't be reached right now. Servers"),
                lines(RemoteScreens.server(Fixtures.player(), LOBBY, ListQuery.DEFAULT, 10)));
        assertEquals("You don't have access to Halloween Event. Servers",
                lines(RemoteScreens.server(Fixtures.player(), HALLOWEEN, ListQuery.DEFAULT, 10)).get(1));
        assertEquals(List.of("● Skyblock ⏷", "Nothing published yet."),
                lines(RemoteScreens.server(Fixtures.player(), SKYBLOCK, ListQuery.DEFAULT, 10)));
    }

    @Test
    void aDimensionUsesTheLocalViewsWithRemoteActions() {
        Component tree = RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints",
                "Home Bases",
                "  [MH] Main Home",
                "  [GM] Gem Mine",
                "  [SV] Spawn Village",
                "Farms",
                "  [IF] Iron Farm",
                "  [WF] Wheat Fields",
                "  [CF] Cane Farm",
                "  … 4 more",
                "Exploration",
                "  [OM] Ocean Monument",
                "  [DT] Desert Temple",
                "  [SH] Stronghold",
                "  [WM] Woodland Mansion",
                "Lists · Tree · Flat · Search",
                "Sort Default · Name · Color"), lines(tree));
        assertEquals("/wp remote list survival", clickOf(tree, "Overworld ⏷"));
        assertEquals("/wp remote list survival " + OW + " \"Home Bases\"", clickOf(tree, "Home Bases"));
        assertEquals("/wp remote tp survival " + OW + " \"Home Bases\" \"Main Home\"", clickOf(tree, "[MH]"));
        assertEquals("/wp remote details survival " + OW + " \"Home Bases\" \"Main Home\"", clickOf(tree, "Main Home"));
        assertEquals("Main Home\nWhere the beds are\n120, 64, -35\nNether 15, 64, -5\nOn Survival in Overworld\nClick for details",
                tooltipOf(tree, "Main Home"));
        assertEquals("Teleport to Main Home\n120, 64, -35\nSwitches you to the Survival server", tooltipOf(tree, "[MH]"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(tree, "… 4 more"));
    }

    @Test
    void plainTextViewersKeepTheServerTitleCoordinatesAndContinuingCommandsColoured() {
        Component tree = RemoteScreens.dimension(Fixtures.console(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT, 10);

        assertEquals(NamedTextColor.GOLD, colorOf(tree, "Survival (survival)"));
        assertEquals(NamedTextColor.GRAY, colorOf(tree, "120, 64, -35"));
        assertEquals(NamedTextColor.AQUA, colorOf(tree, "/wp remote list survival " + OW + " Farms"));
    }

    @Test
    void theListsAndFlatViewsShowNoDistancesOrAddActions() {
        assertEquals(List.of(
                "● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints",
                "Home Bases · 3",
                "Farms · 7",
                "Exploration · 4",
                "Lists · Tree · Flat · Search"),
                lines(RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT.withView(ListView.LISTS), 10)));
        List<String> flat = lines(RemoteScreens.dimension(Fixtures.player(), SURVIVAL, OVERWORLD,
                ListQuery.DEFAULT.withView(ListView.FLAT), 10));
        assertEquals("[MH] Main Home · Home Bases", flat.get(1));
        assertEquals("… 4 more waypoints", flat.get(11));
    }

    @Test
    void staleServersAndReadersWithoutPermissionDontTeleport() {
        Component stale = RemoteScreens.dimension(Fixtures.player(), CREATIVE, OVERWORLD, ListQuery.DEFAULT, 10);
        Component member = RemoteScreens.dimension(Fixtures.member(), SURVIVAL, OVERWORLD, ListQuery.DEFAULT, 10);

        assertNull(clickOf(stale, "[PA]"));
        assertEquals("Off while Creative Plots is stale", tooltipOf(stale, "[PA]"));
        assertNull(clickOf(member, "[MH]"));
        assertTrue(runCommands(member).stream().noneMatch(command -> command.startsWith("/wp remote tp ")));
    }

    @Test
    void aListHasItsRowsSearchAndTheSortRow() {
        Component list = RemoteScreens.list(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), ListQuery.DEFAULT, 10);

        assertEquals(List.of(
                "● Survival › Overworld › Farms  7 waypoints",
                "[IF] Iron Farm",
                "[WF] Wheat Fields",
                "[CF] Cane Farm",
                "[MG] Mob Grinder",
                "[VH] Villager Hall",
                "[SF] Slime Farm",
                "[GF] Gold Farm",
                "Search",
                "Sort Default · Name · Color"), lines(list));
        assertEquals("/wp remote list survival", clickOf(list, "Survival"));
        assertEquals("/wp remote list survival " + OW, clickOf(list, "Overworld"));
        assertEquals(NamedTextColor.GOLD, colorOf(list, "Farms"));
        assertEquals("No waypoints yet.", lines(RemoteScreens.list(Fixtures.player(), SURVIVAL, OVERWORLD,
                new WaypointList("Storage", 1, List.of()), ListQuery.DEFAULT, 10)).get(1));
    }

    @Test
    void detailsAreReadOnly() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");
        Component details = RemoteScreens.details(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron);

        assertEquals(List.of(
                "● Survival › Overworld › Farms › [IF] Iron Farm",
                "Position: 300, 80, 150",
                "Color: ■ #AAAAAA",
                "Yaw: 0° (south)",
                "Visibility: Global",
                "Keywords: none",
                "Description: none",
                "[Teleport] [Back]"), lines(details));
        assertEquals("/wp remote tp survival " + OW + " Farms \"Iron Farm\"", clickOf(details, "[Teleport]"));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(details, "[Back]"));
        SimpleWaypoint renamed = new SimpleWaypoint("iron_farm", "Iron Farm", "IF", iron.pos(), iron.rgb(), 0, true,
                List.of(), "", null);
        assertEquals("Identifier: iron_farm",
                lines(RemoteScreens.details(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), renamed)).get(1));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(RemoteScreens.details(Fixtures.player(), CREATIVE, OVERWORLD,
                CREATIVE.lists(OVERWORLD).get(0), CREATIVE.lists(OVERWORLD).get(0).getWaypointByName("Plot A")), "[Teleport]"));
        assertEquals(7, lines(RemoteScreens.details(Fixtures.console(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron)).size());
    }

    @Test
    void searchResultsSitUnderServerAndDimensionHeadings() {
        Component everywhere = RemoteScreens.search(Fixtures.player(), null, SERVERS, ListQuery.DEFAULT.withSearch("farm"), 10);

        assertEquals(List.of(
                "All servers ⏷",
                "Search \"farm\" · 9 matches · Clear",
                "Creative Plots ● › Overworld",
                "  [PF] Pumpkin Farm · Plots",
                "  [SF] Sky Farm · Plots",
                "Survival ● › Overworld",
                "  [IF] Iron Farm · Farms",
                "  [WF] Wheat Fields · Farms",
                "  [CF] Cane Farm · Farms",
                "  [MG] Mob Grinder · Farms",
                "  [VH] Villager Hall · Farms",
                "  [SF] Slime Farm · Farms",
                "  [GF] Gold Farm · Farms"), lines(everywhere));
        assertEquals("/wp remote list", clickOf(everywhere, "Clear"));
        List<String> onServer = lines(RemoteScreens.search(Fixtures.player(), SURVIVAL, List.of(SURVIVAL),
                ListQuery.DEFAULT.withSearch("farm"), 10));
        assertEquals("● Survival ⏷", onServer.get(0));
        assertEquals("Overworld", onServer.get(2));
    }

    @Test
    void errorsNameTheMissingPartAndHowToGoBack() {
        assertEquals("✘ No server called mars. Servers", render(RemoteScreens.noServer(Fixtures.player(), "mars")));
        assertEquals("✘ Survival has no dimension ad_astra:mars. Browse",
                render(RemoteScreens.noDimension(Fixtures.player(), SURVIVAL, "ad_astra:mars")));
        assertEquals("✘ Survival has no list Farm in Overworld. Browse",
                render(RemoteScreens.noList(Fixtures.player(), SURVIVAL, OVERWORLD, "Farm")));
        assertEquals("✘ No waypoint called Gate in Farms. Open Farms",
                render(RemoteScreens.noWaypoint(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), "Gate")));
        assertEquals("✘ Remote waypoints can't be sorted by distance.", render(RemoteScreens.distanceUnavailable()));
    }

    @Test
    void switchingNamesTheServerAndTheWaypoint() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");

        assertEquals("Switching you to Survival for [IF] Iron Farm…",
                render(RemoteScreens.switching(Fixtures.player(), SURVIVAL, OVERWORLD, Fixtures.farms(), iron)));
    }

    @Test
    void failedSwitchesOfferTryAgainOrTheListWhenTheWaypointIsGone() {
        Component unreachable = RemoteScreens.teleportFailed(Fixtures.player(), SURVIVAL, "survival", OVERWORLD, "Farms",
                "Iron Farm", Result.UNAVAILABLE);
        Component gone = RemoteScreens.teleportFailed(Fixtures.player(), SURVIVAL, "survival", OVERWORLD, "Farms",
                "Iron Farm", Result.NOT_FOUND);

        assertEquals("✘ Survival can't be reached right now. Try again", render(unreachable));
        assertEquals("/wp remote tp survival " + OW + " Farms \"Iron Farm\"", clickOf(unreachable, "Try again"));
        assertEquals("✘ That waypoint is no longer on Survival. Open Farms", render(gone));
        assertEquals("/wp remote list survival " + OW + " Farms", clickOf(gone, "Open Farms"));
        assertEquals("✘ You don't have permission to switch to survival.", render(RemoteScreens.teleportFailed(
                Fixtures.player(), null, "survival", OVERWORLD, "Farms", "Iron Farm", Result.UNAUTHORIZED)));
    }

    @Test
    void theDestinationSaysWhereThePlayerArrived() {
        SimpleWaypoint iron = Fixtures.farms().getWaypointByName("Iron Farm");

        assertEquals("✔ Arrived at [IF] Iron Farm on survival",
                render(RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", iron)));
        assertEquals("✔ Arrived at Iron Farm on survival",
                render(RemoteScreens.arrival(Result.SUCCESS, "survival", "Iron Farm", null)));
        assertEquals("✘ The switch to survival timed out.", render(RemoteScreens.arrival(Result.EXPIRED, "survival", null, null)));
    }

    @Test
    void remoteLabelsStayLiteralAndShort() {
        Server odd = server("odd", "{\"text\":\"Odd\",\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/op me\"}}",
                RemoteCatalogState.AVAILABLE, Map.of(OVERWORLD, List.of(new WaypointList("x".repeat(300), 1, List.of()))));
        Component picker = RemoteScreens.picker(Fixtures.player(), List.of(odd), 1, 10);

        assertTrue(render(picker).contains("{\"text\":\"Odd\""));
        assertTrue(runCommands(picker).stream().noneMatch(command -> command.contains("/op")));
        assertTrue(render(RemoteScreens.server(Fixtures.player(), odd, ListQuery.DEFAULT, 10)).contains("x".repeat(256) + "…"));
    }
}
