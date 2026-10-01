package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Controls that local and remote list screens share (spec 6.3, 6.4, 6.6). */
public final class ListControls {
    private ListControls() {
    }

    /**
     * The pager that closes the last control line: « ‹ 2/5 › ». « and » appear only with three or
     * more pages, and arrows that lead nowhere are dark gray. Null for one page and for plain text.
     */
    public static @Nullable Component pager(Viewer viewer, ListTarget target, ListQuery query, int pages,
                                            Component detail) {
        if (pages <= 1 || viewer.plainText()) {
            return null;
        }
        int page = query.page();
        List<Component> pieces = new ArrayList<>();
        pieces.add(text("    "));
        if (pages >= 3) {
            pieces.add(arrow(viewer, "«", target, query, 1, page > 1, "wp.page.first"));
            pieces.add(space());
        }
        pieces.add(arrow(viewer, "‹", target, query, page - 1, page > 1, "wp.page.previous"));
        pieces.add(Chat.link(viewer, text(" " + page + "/" + pages + " "), GRAY, Click.suggest(target.pagePrompt(query)),
                Tooltip.of("wp.page.of", text(page), text(pages)).line(detail).hint("wp.hint.page")));
        pieces.add(arrow(viewer, "›", target, query, page + 1, page < pages, "wp.page.next"));
        if (pages >= 3) {
            pieces.add(space());
            pieces.add(arrow(viewer, "»", target, query, pages, page < pages, "wp.page.last"));
        }
        return Chat.concat(pieces);
    }

    private static Component arrow(Viewer viewer, String glyph, ListTarget target, ListQuery query, int page,
                                   boolean enabled, String key) {
        if (!enabled) {
            return text(glyph, DARK_GRAY);
        }
        return Chat.link(viewer, text(glyph), AQUA, Click.run(target.command(query.withPage(page))), Tooltip.of(key));
    }

    /** "10 per page · 42 waypoints", the second line of the page number's tooltip. */
    public static Component pageDetail(int pageLimit, Component total) {
        return Chat.join(translatable("wp.page.size", text(pageLimit)), total);
    }

    /** "… 3 more lists" opening the next page. Plain-text viewers read the command after a colon. */
    public static @Nullable Component more(Viewer viewer, String unit, int count, String command) {
        if (count <= 0) {
            return null;
        }
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), Chat.count("wp.more." + unit, count));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command), Tooltip.of("wp.page.next")
                .line(translatable("wp.more.after", Chat.count("wp.count." + unit, count))));
    }

    /**
     * Sort Default · Name ↑ · Distance · Color. The selected mode is gold; apart from Default it
     * carries its direction and reverses on click. distanceBlocked, when given, disables Distance
     * and becomes its tooltip.
     */
    public static @Nullable Component sortRow(Viewer viewer, ListTarget target, ListQuery query, List<SortMode> modes,
                                              String defaultTooltipKey, @Nullable Component distanceBlocked) {
        if (viewer.plainText()) {
            return null;
        }
        List<Component> items = new ArrayList<>();
        for (SortMode mode : modes) {
            items.add(sortItem(viewer, target, query, mode, defaultTooltipKey, distanceBlocked));
        }
        return Chat.concat(translatable("wp.sort", GRAY), space(), Chat.join(items));
    }

    private static Component sortItem(Viewer viewer, ListTarget target, ListQuery query, SortMode mode,
                                      String defaultTooltipKey, @Nullable Component distanceBlocked) {
        String key = "wp.sort." + mode.name().toLowerCase(Locale.ROOT);
        Component label = translatable(key);
        if (query.sort() == mode && mode == SortMode.DEFAULT) {
            return Chat.hover(viewer, Chat.colored(label, GOLD), Tooltip.of(defaultTooltipKey));
        }
        if (query.sort() == mode) {
            Component direction = translatable(key + (query.descending() ? ".descending" : ".ascending"));
            return Chat.link(viewer, Chat.concat(label, text(query.descending() ? " ↓" : " ↑")), GOLD,
                    Click.run(target.command(query.reversed())),
                    Tooltip.of(Chat.join(label, direction)).hint("wp.hint.reverse"));
        }
        if (mode == SortMode.DISTANCE && distanceBlocked != null) {
            return Chat.hover(viewer, Chat.colored(label, DARK_GRAY), Tooltip.of(distanceBlocked));
        }
        return Chat.link(viewer, label, AQUA, Click.run(target.command(query.withSort(mode))),
                Tooltip.of(key + ".tooltip"));
    }

    /** Lists · Tree · Flat, then the extras. The current view is gold; switching to Lists drops the search. */
    public static @Nullable Component viewRow(Viewer viewer, ListTarget target, ListQuery query, ListView current,
                                              List<ListView> views, List<? extends @Nullable Component> extras) {
        if (viewer.plainText()) {
            return null;
        }
        List<Component> items = new ArrayList<>();
        for (ListView view : views) {
            Component label = translatable("wp.view." + view.id());
            if (view == current) {
                items.add(Chat.colored(label, GOLD));
                continue;
            }
            ListQuery next = view == ListView.LISTS ? query.withSearch("").withView(view) : query.withView(view);
            items.add(Chat.link(viewer, label, AQUA, Click.run(target.command(next)),
                    Tooltip.of("wp.view." + view.id() + ".tooltip")));
        }
        items.addAll(extras);
        return Chat.join(items);
    }

    /** The aqua Search link, which suggests "<target> search ". */
    public static @Nullable Component search(Viewer viewer, ListTarget target, Component tooltipTitle) {
        return Chat.control(viewer, translatable("wp.search"), AQUA, Click.suggest(target.searchPrompt()),
                Tooltip.of(tooltipTitle).hint("wp.hint.type_search"));
    }

    /** Search "farm" · 3 matches · Clear */
    public static Component searchLine(Viewer viewer, String search, int matches, String clearCommand) {
        return Chat.join(
                translatable("wp.search.results", GRAY, text(search, WHITE)),
                Chat.colored(Chat.count("wp.count.match", matches), GRAY),
                Chat.control(viewer, translatable("wp.search.clear"), AQUA, Click.run(clearCommand),
                        Tooltip.of("wp.search.clear.tooltip")));
    }

    /** Nothing matches "farm". Clear search */
    public static Component noMatches(Viewer viewer, String search, String clearCommand) {
        Component clear = Chat.control(viewer, translatable("wp.search.clear_search"), AQUA, Click.run(clearCommand),
                Tooltip.of("wp.search.clear.tooltip"));
        return Chat.concat(translatable("wp.search.none", GRAY, text(search)), clear == null ? null : space(), clear);
    }

    /** ✘ Page 5 does not exist; the last page is 2. Last page */
    public static Component pageNotFound(Viewer viewer, ListTarget target, ListQuery query, int pages) {
        return Chat.error(translatable("wp.page.missing", text(query.page()), text(pages)),
                Chat.control(viewer, translatable("wp.page.last"), AQUA,
                        Click.run(target.command(query.withPage(pages))), Tooltip.of("wp.page.go", text(pages))));
    }

    /** Adds the control rows that exist; the pager closes the last of them (spec 6.3). */
    public static void controls(ChatLines lines, @Nullable Component pager, @Nullable Component... rows) {
        List<Component> present = new ArrayList<>();
        for (Component row : rows) {
            if (row != null) {
                present.add(row);
            }
        }
        if (present.isEmpty()) {
            lines.add(pager);
            return;
        }
        for (int index = 0; index < present.size(); index++) {
            boolean last = index == present.size() - 1;
            lines.add(last ? Chat.concat(present.get(index), pager) : present.get(index));
        }
    }
}
