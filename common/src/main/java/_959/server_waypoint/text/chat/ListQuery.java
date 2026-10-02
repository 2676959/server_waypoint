package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * The options of a list command. A null limit stands for the server's configured page limit.
 * Every change except the page itself returns to the first page.
 */
public record ListQuery(
        String search,
        SortMode sort,
        boolean descending,
        ListView view,
        int page,
        @Nullable Integer limit
) {
    public static final ListQuery DEFAULT = new ListQuery("", SortMode.DEFAULT, false, ListView.DEFAULT, 1, null);

    public ListQuery {
        Objects.requireNonNull(search, "search");
        Objects.requireNonNull(sort, "sort");
        Objects.requireNonNull(view, "view");
        if (page < 1) {
            throw new IllegalArgumentException("Page must be positive");
        }
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("Page limit must be positive");
        }
        descending = descending && sort != SortMode.DEFAULT;
    }

    public boolean searching() {
        return !this.search.isBlank();
    }

    public int pageLimit(int configured) {
        return this.limit == null ? configured : this.limit;
    }

    public ListQuery withSearch(String search) {
        return new ListQuery(search, this.sort, this.descending, this.view, 1, this.limit);
    }

    public ListQuery withSort(SortMode sort) {
        return new ListQuery(this.search, sort, false, this.view, 1, this.limit);
    }

    public ListQuery reversed() {
        return new ListQuery(this.search, this.sort, !this.descending, this.view, 1, this.limit);
    }

    public ListQuery withView(ListView view) {
        return new ListQuery(this.search, this.sort, this.descending, view, 1, this.limit);
    }

    public ListQuery withPage(int page) {
        return new ListQuery(this.search, this.sort, this.descending, this.view, page, this.limit);
    }
}
