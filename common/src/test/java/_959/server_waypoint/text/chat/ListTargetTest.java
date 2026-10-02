package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListTargetTest {
    @Test
    void commandsCarryOptionsInTheGrammarOrderAndLeaveDefaultsOut() {
        ListTarget target = ListTarget.dimension("minecraft:overworld");

        assertEquals("/wp list minecraft:overworld", target.command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld search \"iron farm\" sort name order descending limit 20 view tree page 2",
                target.command(new ListQuery("iron farm", SortMode.NAME, true, ListView.TREE, 2, 20)));
        assertEquals("/wp list minecraft:overworld view flat page ", target.pagePrompt(ListQuery.DEFAULT.withView(ListView.FLAT).withPage(3)));
        assertEquals("/wp list minecraft:overworld search ", target.searchPrompt());
    }

    @Test
    void namesThatNeedQuotingAreQuoted() {
        assertEquals("/wp list minecraft:overworld \"search\"", ListTarget.list("minecraft:overworld", "search").command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld \"Farm \\\"North\\\"\"",
                ListTarget.list("minecraft:overworld", "Farm \"North\"").command(ListQuery.DEFAULT));
        assertEquals("/wp list minecraft:overworld \"\"", ListTarget.list("minecraft:overworld", "").command(ListQuery.DEFAULT));
        assertEquals("/wp remote list survival \"minecraft:overworld\" Farms",
                ListTarget.remote("survival", "minecraft:overworld", "Farms").command(ListQuery.DEFAULT));
        assertEquals("/wp remote list", ListTarget.remote(null, null, null).command(ListQuery.DEFAULT));
    }

    @Test
    void changingAnOptionReturnsToTheFirstPageAndDefaultSortHasNoDirection() {
        ListQuery query = new ListQuery("", SortMode.NAME, true, ListView.FLAT, 4, null);

        assertEquals(1, query.withSort(SortMode.COLOR).page());
        assertFalse(query.withSort(SortMode.COLOR).descending());
        assertFalse(query.reversed().descending());
        assertEquals(1, query.withView(ListView.TREE).page());
        assertEquals(4, query.withPage(4).page());
        assertFalse(new ListQuery("", SortMode.DEFAULT, true, ListView.DEFAULT, 1, null).descending());
        assertEquals(10, query.pageLimit(10));
        assertThrows(IllegalArgumentException.class, () -> query.withPage(0));
    }
}
