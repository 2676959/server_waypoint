package _959.server_waypoint.text.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Splits rows into pages. There is always at least one page, which may be empty. */
public final class Paging {
    private Paging() {
    }

    /** Pages of whole items within a line budget; an item larger than the budget gets a page of its own. */
    public static <T> List<List<T>> byLines(List<T> items, ToIntFunction<T> lines, int budget) {
        List<List<T>> pages = new ArrayList<>();
        List<T> page = new ArrayList<>();
        int used = 0;
        for (T item : items) {
            int size = lines.applyAsInt(item);
            if (!page.isEmpty() && used + size > budget) {
                pages.add(page);
                page = new ArrayList<>();
                used = 0;
            }
            page.add(item);
            used += size;
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page);
        }
        return pages;
    }

    public static <T> List<List<T>> bySize(List<T> items, int size) {
        return byLines(items, item -> 1, size);
    }

    /** How many items the pages after this one hold; pages count from 1. */
    public static <T> int after(List<List<T>> pages, int page) {
        int count = 0;
        for (int index = page; index < pages.size(); index++) {
            count += pages.get(index).size();
        }
        return count;
    }
}
