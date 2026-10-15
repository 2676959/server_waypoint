package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static _959.server_waypoint.text.feedback.Fixtures.END;
import static _959.server_waypoint.text.feedback.Fixtures.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which feedback is a screen on the paths the audit's sample data doesn't reach. A screen's empty state, or its
 * search without matches, is still a screen. A page that doesn't exist is an error line, like any other.
 */
class ScreenMarksTest {
    /** The outputs that are screens; every other output of {@link #outputs(Viewer)} is a page that doesn't exist. */
    private static final Set<String> SCREENS = Set.of(
            "no lists", "no matches", "list no matches", "all empty", "all no matches", "add picker empty",
            "add picker paged", "remote picker empty", "remote all empty", "remote server nothing",
            "remote search no matches", "remote dimension empty", "remote dimension no matches",
            "remote dimension lists", "remote dimension flat", "remote list empty", "remote list no matches");

    private static Map<String, Component> outputs(Viewer viewer) {
        DimensionStyle dims = Fixtures.dims(viewer);
        WaypointList farms = Fixtures.farms();
        WaypointList storage = new WaypointList("Storage", 1, List.of());
        Map<String, List<WaypointList>> overworld = Map.of(OVERWORLD, Fixtures.overworldLists());
        Map<String, List<WaypointList>> onlyFarms = Map.of(OVERWORLD, List.of(farms));
        WaypointQueryEngine.Query all = query("", viewer);
        WaypointQueryEngine.Query farm = query("farm", viewer);
        WaypointQueryEngine.Query nothing = query("zzz", viewer);
        ListScreen.Totals totals = new ListScreen.Totals(3, 14);
        // Every change but the page returns to page 1, so the page comes last.
        ListQuery missing = ListQuery.DEFAULT.withPage(9);
        ListQuery missingInSearch = ListQuery.DEFAULT.withSearch("farm").withPage(9);
        ListQuery noMatches = ListQuery.DEFAULT.withSearch("zzz");
        List<DimensionScreens.DimensionLists> dimensions = List.of(
                new DimensionScreens.DimensionLists(OVERWORLD, Fixtures.overworldLists()),
                new DimensionScreens.DimensionLists(END, List.of()));
        Server survival = new Server(new RemoteServerId("survival"), "Survival", RemoteCatalogState.AVAILABLE,
                Map.of(OVERWORLD, Fixtures.overworldLists()));
        Server bare = new Server(new RemoteServerId("bare"), "Bare", RemoteCatalogState.AVAILABLE,
                Map.of(OVERWORLD, List.of()));
        Server empty = new Server(new RemoteServerId("empty"), "Empty", RemoteCatalogState.AVAILABLE, Map.of());
        List<Server> servers = List.of(survival, bare, empty);

        Map<String, Component> outputs = new LinkedHashMap<>();
        outputs.put("no lists", ListScreen.dimension(dims, OVERWORLD, new ListScreen.Totals(0, 0),
                WaypointQueryEngine.queryLists(Map.of(), all), ListQuery.DEFAULT, 10));
        outputs.put("no matches", ListScreen.dimension(dims, OVERWORLD, totals,
                WaypointQueryEngine.queryLists(overworld, nothing), noMatches, 10));
        for (ListView view : List.of(ListView.TREE, ListView.LISTS, ListView.FLAT)) {
            outputs.put("missing page of " + view, ListScreen.dimension(dims, OVERWORLD, totals,
                    WaypointQueryEngine.queryLists(overworld, all), ListQuery.DEFAULT.withView(view).withPage(9), 10));
        }
        outputs.put("list no matches", ListScreen.list(dims, OVERWORLD, farms,
                WaypointQueryEngine.queryLists(onlyFarms, nothing), noMatches, 10));
        outputs.put("missing page of a list", ListScreen.list(dims, OVERWORLD, farms,
                WaypointQueryEngine.queryLists(onlyFarms, all), missing, 10));
        outputs.put("missing page of dimensions", DimensionScreens.dimensionList(dims, dimensions, 9, 10));
        outputs.put("all empty", DimensionScreens.all(dims, List.of(new DimensionScreens.DimensionLists(END, List.of())),
                ListQuery.DEFAULT, 10));
        outputs.put("missing page of all", DimensionScreens.all(dims, dimensions, missing, 10));
        outputs.put("all no matches", DimensionScreens.allSearch(dims, WaypointQueryEngine.queryLists(overworld, nothing),
                noMatches, 10));
        outputs.put("missing page of all search", DimensionScreens.allSearch(dims,
                WaypointQueryEngine.queryLists(overworld, farm), missingInSearch, 10));
        outputs.put("add picker empty", PickerScreens.add(dims, OVERWORLD, List.of(), 1, 10));
        outputs.put("add picker paged", PickerScreens.add(dims, OVERWORLD, Fixtures.overworldLists(), 1, 1));
        outputs.put("missing page of the add picker", PickerScreens.add(dims, OVERWORLD, Fixtures.overworldLists(), 9, 1));
        outputs.put("remote picker empty", RemoteScreens.picker(viewer, List.of(), 1, 10));
        outputs.put("missing page of the remote picker", RemoteScreens.picker(viewer, servers, 9, 10));
        outputs.put("remote all empty", RemoteScreens.allServers(viewer, List.of(), ListQuery.DEFAULT, 10));
        outputs.put("missing page of remote all", RemoteScreens.allServers(viewer, servers, missing, 10));
        outputs.put("remote server nothing", RemoteScreens.server(viewer, empty, ListQuery.DEFAULT, 10));
        outputs.put("missing page of a remote server", RemoteScreens.server(viewer, survival, missing, 10));
        outputs.put("remote search no matches", RemoteScreens.search(viewer, null, servers, noMatches, 10));
        outputs.put("missing page of remote search", RemoteScreens.search(viewer, null, servers,
                missingInSearch, 10));
        outputs.put("remote dimension empty", RemoteScreens.dimension(viewer, bare, OVERWORLD, ListQuery.DEFAULT, 10));
        outputs.put("remote dimension no matches", RemoteScreens.dimension(viewer, survival, OVERWORLD, noMatches, 10));
        outputs.put("remote dimension lists", RemoteScreens.dimension(viewer, survival, OVERWORLD,
                ListQuery.DEFAULT.withView(ListView.LISTS), 10));
        outputs.put("remote dimension flat", RemoteScreens.dimension(viewer, survival, OVERWORLD,
                ListQuery.DEFAULT.withView(ListView.FLAT), 10));
        for (ListView view : List.of(ListView.TREE, ListView.LISTS, ListView.FLAT)) {
            outputs.put("missing page of a remote dimension in " + view, RemoteScreens.dimension(viewer, survival,
                    OVERWORLD, ListQuery.DEFAULT.withView(view).withPage(9), 10));
        }
        outputs.put("remote list empty", RemoteScreens.list(viewer, survival, OVERWORLD, storage, ListQuery.DEFAULT, 10));
        outputs.put("remote list no matches", RemoteScreens.list(viewer, survival, OVERWORLD, farms, noMatches, 10));
        outputs.put("missing page of a remote list", RemoteScreens.list(viewer, survival, OVERWORLD, farms, missing, 10));
        return outputs;
    }

    private static WaypointQueryEngine.Query query(String search, Viewer viewer) {
        return new WaypointQueryEngine.Query(search, SortMode.DEFAULT, viewer.position(), viewer.dimension(), false);
    }

    @Test
    void emptyStatesAndSearchesWithoutMatchesAreScreensButMissingPagesAreErrors() {
        for (Viewer viewer : List.of(Fixtures.player(), Fixtures.console())) {
            outputs(viewer).forEach((name, output) -> {
                boolean screen = SCREENS.contains(name);
                assertEquals(screen, Chat.isScreen(output),
                        name + (screen ? " is a screen" : " is an error line") + " for "
                                + (viewer.plainText() ? "plain-text viewers" : "players"));
            });
        }
    }
}
