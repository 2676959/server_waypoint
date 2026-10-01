package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.text.chat.ChatAssert.clickOf;
import static _959.server_waypoint.text.chat.ChatAssert.colorOf;
import static _959.server_waypoint.text.chat.ChatAssert.render;
import static _959.server_waypoint.text.chat.ChatAssert.tooltipOf;
import static _959.server_waypoint.text.chat.ChatTest.CONSOLE;
import static _959.server_waypoint.text.chat.ChatTest.PLAYER;
import static net.kyori.adventure.text.Component.text;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ListControlsTest {
    private static final ListTarget OVERWORLD = ListTarget.dimension("minecraft:overworld");
    private static final List<SortMode> LOCAL_SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.DISTANCE, SortMode.COLOR);

    @Test
    void thePagerHasFirstAndLastArrowsFromThreePagesOn() {
        ListQuery query = ListQuery.DEFAULT.withView(ListView.FLAT).withPage(2);
        Component pager = ListControls.pager(PLAYER, OVERWORLD, query, 5,
                ListControls.pageDetail(10, Chat.count("wp.count.waypoint", 42)));

        assertEquals("    « ‹ 2/5 › »", render(pager));
        assertEquals("/wp list minecraft:overworld view flat", clickOf(pager, "«"));
        assertEquals("/wp list minecraft:overworld view flat", clickOf(pager, "‹"));
        assertEquals("/wp list minecraft:overworld view flat page 3", clickOf(pager, "›"));
        assertEquals("/wp list minecraft:overworld view flat page 5", clickOf(pager, "»"));
        assertEquals("/wp list minecraft:overworld view flat page ", clickOf(pager, " 2/5 "));
        assertEquals("Page 2 of 5\n10 per page · 42 waypoints\nClick to type a page", tooltipOf(pager, " 2/5 "));
        assertEquals(NamedTextColor.GRAY, colorOf(pager, " 2/5 "));
    }

    @Test
    void arrowsThatLeadNowhereAreDarkGray() {
        Component pager = ListControls.pager(PLAYER, OVERWORLD, ListQuery.DEFAULT, 2, text("detail"));

        assertEquals("    ‹ 1/2 ›", render(pager));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(pager, "‹"));
        assertNull(clickOf(pager, "‹"));
        assertNull(ListControls.pager(PLAYER, OVERWORLD, ListQuery.DEFAULT, 1, text("detail")));
        assertNull(ListControls.pager(CONSOLE, OVERWORLD, ListQuery.DEFAULT, 3, text("detail")));
    }

    @Test
    void moreLinesOpenTheNextPageOrPrintItsCommand() {
        Component more = ListControls.more(PLAYER, "list", 3, "/wp list minecraft:overworld page 2");

        assertEquals("… 3 more lists", render(more));
        assertEquals("/wp list minecraft:overworld page 2", clickOf(more, "3 more lists"));
        assertEquals("Next page\n3 lists after this page", tooltipOf(more, "3 more lists"));
        assertEquals(NamedTextColor.AQUA, colorOf(more, "3 more lists"));
        assertEquals("… 1 more waypoint: /wp list minecraft:overworld page 2",
                render(ListControls.more(CONSOLE, "waypoint", 1, "/wp list minecraft:overworld page 2")));
        assertNull(ListControls.more(PLAYER, "list", 0, "/wp list"));
    }

    @Test
    void theSelectedSortIsGoldCarriesItsDirectionAndReversesOnClick() {
        Component row = ListControls.sortRow(PLAYER, OVERWORLD, ListQuery.DEFAULT.withSort(SortMode.NAME),
                LOCAL_SORTS, "wp.sort.default.saved", null);

        assertEquals("Sort Default · Name ↑ · Distance · Color", render(row));
        assertEquals(NamedTextColor.GOLD, colorOf(row, "Name"));
        assertEquals("/wp list minecraft:overworld sort name order descending", clickOf(row, "Name"));
        assertEquals("Name · A to Z\nClick to reverse", tooltipOf(row, "Name"));
        assertEquals("/wp list minecraft:overworld", clickOf(row, "Default"));
        assertEquals("/wp list minecraft:overworld sort distance", clickOf(row, "Distance"));
        assertEquals(NamedTextColor.AQUA, colorOf(row, "Color"));
    }

    @Test
    void defaultSortIsGoldWithoutClickAndDistanceCanBeBlocked() {
        Component row = ListControls.sortRow(PLAYER, OVERWORLD, ListQuery.DEFAULT, LOCAL_SORTS,
                "wp.sort.default.saved", text("Distance needs you in Nether"));

        assertEquals(NamedTextColor.GOLD, colorOf(row, "Default"));
        assertNull(clickOf(row, "Default"));
        assertEquals("Saved order", tooltipOf(row, "Default"));
        assertEquals(NamedTextColor.DARK_GRAY, colorOf(row, "Distance"));
        assertNull(clickOf(row, "Distance"));
        assertEquals("Distance needs you in Nether", tooltipOf(row, "Distance"));
        assertEquals("Sort Default · Name ↓ · Color", render(ListControls.sortRow(PLAYER, OVERWORLD,
                ListQuery.DEFAULT.withSort(SortMode.NAME).reversed(),
                List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR), "wp.sort.default.published", null)));
        assertNull(ListControls.sortRow(CONSOLE, OVERWORLD, ListQuery.DEFAULT, LOCAL_SORTS, "wp.sort.default.saved", null));
    }

    @Test
    void theViewRowMarksTheCurrentViewAndListsDropsTheSearch() {
        ListQuery query = ListQuery.DEFAULT.withSearch("farm");
        Component row = ListControls.viewRow(PLAYER, OVERWORLD, query, ListView.TREE,
                List.of(ListView.LISTS, ListView.TREE, ListView.FLAT),
                List.of(ListControls.search(PLAYER, OVERWORLD, text("Search Overworld"))));

        assertEquals("Lists · Tree · Flat · Search", render(row));
        assertEquals(NamedTextColor.GOLD, colorOf(row, "Tree"));
        assertNull(clickOf(row, "Tree"));
        assertEquals("/wp list minecraft:overworld view lists", clickOf(row, "Lists"));
        assertEquals("/wp list minecraft:overworld search farm view flat", clickOf(row, "Flat"));
        assertEquals("/wp list minecraft:overworld search ", clickOf(row, "Search"));
        assertEquals("Search Overworld\nType what to look for, then press Enter", tooltipOf(row, "Search"));
    }

    @Test
    void searchLinesShowTheQueryTheMatchesAndHowToClearIt() {
        Component results = ListControls.searchLine(PLAYER, "farm", 3, "/wp list minecraft:overworld");
        Component none = ListControls.noMatches(PLAYER, "farm", "/wp list minecraft:overworld");

        assertEquals("Search \"farm\" · 3 matches · Clear", render(results));
        assertEquals(NamedTextColor.WHITE, colorOf(results, "farm"));
        assertEquals("/wp list minecraft:overworld", clickOf(results, "Clear"));
        assertEquals("Nothing matches \"farm\". Clear search", render(none));
        assertEquals("Nothing matches \"farm\".", render(ListControls.noMatches(CONSOLE, "farm", "/wp list")));
    }

    @Test
    void thePagerClosesTheLastControlRow() {
        ChatLines lines = new ChatLines();
        ListControls.controls(lines, text("    ‹ 1/2 ›"), text("Lists · Tree"), null, text("Sort Default"));
        ChatLines alone = new ChatLines();
        ListControls.controls(alone, text("    ‹ 1/2 ›"), null, null);

        assertEquals(List.of("Lists · Tree", "Sort Default    ‹ 1/2 ›"), ChatAssert.lines(lines.build()));
        assertEquals(List.of("    ‹ 1/2 ›"), ChatAssert.lines(alone.build()));
    }

    @Test
    void aMissingPageNamesTheLastOneAndLinksToIt() {
        Component error = ListControls.pageNotFound(PLAYER, OVERWORLD, ListQuery.DEFAULT.withPage(5), 2);

        assertEquals("✘ Page 5 does not exist; the last page is 2. Last page", render(error));
        assertEquals("/wp list minecraft:overworld page 2", clickOf(error, "Last page"));
    }
}
