package _959.server_waypoint.text.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagingTest {
    @Test
    void pagesHoldWholeItemsWithinTheBudget() {
        List<List<Integer>> pages = Paging.byLines(List.of(4, 6, 5, 6, 2), size -> size, 15);

        assertEquals(List.of(List.of(4, 6, 5), List.of(6, 2)), pages);
        assertEquals(2, Paging.after(pages, 1));
        assertEquals(0, Paging.after(pages, 2));
    }

    @Test
    void anItemLargerThanTheBudgetGetsAPageOfItsOwn() {
        assertEquals(List.of(List.of(20), List.of(3)), Paging.byLines(List.of(20, 3), size -> size, 15));
    }

    @Test
    void nothingStillMakesOneEmptyPage() {
        assertEquals(List.of(List.of()), Paging.bySize(List.of(), 10));
        assertEquals(List.of(List.of(1, 2), List.of(3)), Paging.bySize(List.of(1, 2, 3), 2));
    }
}
