package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static _959.server_waypoint.util.StringCommandBuilder.detailsListCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Local list screens (spec 6): Tree, Lists and Flat views, single lists, search and empty states. */
public final class ListScreen {
    static final int COLLAPSE_ABOVE = 5;
    static final int PREVIEW = 3;
    static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.DISTANCE, SortMode.COLOR);

    /** Every list and waypoint of the dimension, whatever the search: what the header counts. */
    public record Totals(int lists, int waypoints) {
    }

    private ListScreen() {
    }

    /** /wp list <dimension> */
    public static Component dimension(DimensionStyle dims, String dimension, Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        ChatLines lines = new ChatLines().add(header(dims, dimension, totals));
        if (totals.lists() == 0) {
            Component newList = ListActions.newList(dims, dimension);
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).build();
        }
        if (query.searching() && result.listCount() == 0) {
            return lines.add(ListControls.noMatches(viewer, query.search(), target.command(query.withSearch("")))).build();
        }
        List<WaypointListDisplayModel.DisplayList> groups = WaypointListDisplayModel.build(result, true).lists();
        ListView view = resolveView(query, groups, totals, pageLimit);
        ListQuery shown = query.withView(view).withPage(query.page());
        if (query.searching()) {
            lines.add(ListControls.searchLine(viewer, query.search(), result.waypointCount(),
                    target.command(query.withSearch(""))));
        }
        return switch (view) {
            case LISTS -> listsView(dims, dimension, totals, groups, shown, pageLimit, lines);
            case FLAT -> flatView(dims, dimension, totals, result, shown, pageLimit, lines);
            default -> treeView(dims, dimension, totals, groups, shown, pageLimit, lines);
        };
    }

    /** /wp list <dimension> <list> */
    public static Component list(DimensionStyle dims, String dimension, WaypointList list,
                                 WaypointQueryEngine.QueryResult result, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.list(dimension, list.name());
        Component crumb = Chat.concat(dimensionLink(dims, dimension), Chat.CRUMB, WaypointRefs.listLink(dims, list, GOLD,
                Click.run(detailsListCmd(dimension, list.name())), "wp.hint.list_details"));
        Component all = Chat.control(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"),
                Tooltip.of("wp.all.tooltip"));
        ChatLines lines = new ChatLines();
        if (list.isEmpty()) {
            lines.line(crumb, all == null ? null : Chat.SEPARATOR, all);
            List<Component> actions = new ArrayList<>();
            actions.add(ListActions.add(dims, dimension, list));
            actions.add(ListActions.removeList(dims, dimension, list));
            return lines.line(translatable("wp.list.empty.sentence", GRAY), Chat.isEmpty(actions) ? null : text(" "),
                    Chat.join(actions)).build();
        }
        lines.line(crumb, text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY),
                all == null ? null : Chat.SEPARATOR, all);
        List<SimpleWaypoint> rows = result.dimensions().isEmpty()
                ? List.of()
                : result.dimensions().get(0).lists().get(0).waypoints();
        if (query.searching()) {
            String clear = target.command(query.withSearch(""));
            if (rows.isEmpty()) {
                return lines.add(ListControls.noMatches(viewer, query.search(), clear)).build();
            }
            lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        }
        List<List<SimpleWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (SimpleWaypoint waypoint : pages.get(query.page() - 1)) {
            lines.add(row(dims, dimension, list, waypoint, false));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        Component actions = viewer.plainText() ? null : Chat.join(
                ListControls.search(viewer, target, translatable("wp.search.list",
                        WaypointRefs.label(list.displayName(), list.name()))),
                ListActions.add(dims, dimension, list));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                actions, sortRow(dims, dimension, target, query));
        return lines.build();
    }

    /** The dimension in its colour, opening its lists: the first step of a breadcrumb. */
    static Component dimensionLink(DimensionStyle dims, String dimension) {
        Viewer viewer = dims.viewer();
        return viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, DimensionStyle.displayName(dimension), dims.color(dimension),
                Click.run("/wp list " + dimension),
                Tooltip.of("wp.list.every_list_in", dims.name(dimension)).hint("wp.hint.open"));
    }

    /** Overworld ⏷  3 lists · 12 waypoints · All */
    public static Component header(DimensionStyle dims, String dimension, Totals totals) {
        Viewer viewer = dims.viewer();
        Component switcher = viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, Chat.concat(DimensionStyle.displayName(dimension), text(" " + Chat.PICKER)),
                dims.color(dimension), Click.run("/wp list dimensions"),
                dims.tooltip(dimension, DimensionStyle.counts(totals.waypoints(), totals.lists()), "wp.hint.choose_dimension"));
        Component summary = totals.lists() == 0 ? null : Chat.concat(text("  "), Chat.colored(Chat.join(
                Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints())), GRAY));
        Component all = Chat.control(viewer, translatable("wp.all"), AQUA, Click.run("/wp list all"),
                Tooltip.of("wp.all.tooltip"));
        return Chat.concat(switcher, summary, all == null ? null : Chat.SEPARATOR, all);
    }

    /** [AB] Name, then the list in Flat rows, then the distance (or the coordinates in plain text). */
    public static Component row(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                boolean withList) {
        Component detail = WaypointRefs.rowDetail(dims.viewer(), dimension, waypoint.pos());
        return Chat.join(WaypointRefs.reference(dims, dimension, list, waypoint),
                withList ? WaypointRefs.listLink(dims, list, GRAY,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open") : null,
                detail);
    }

    /** The view to show: Tree when the dimension fits on one page or a search runs, otherwise Lists. Remote lists use it too. */
    static ListView resolveView(ListQuery query, List<WaypointListDisplayModel.DisplayList> groups, Totals totals,
                                        int pageLimit) {
        ListView view = query.view();
        if (view == ListView.FLAT && totals.waypoints() == 0) {
            view = ListView.TREE;
        }
        if (view == ListView.DEFAULT || view == ListView.LISTS && query.searching()) {
            view = query.searching() || treePages(groups, pageLimit).size() <= 1 ? ListView.TREE : ListView.LISTS;
        }
        return view;
    }

    static List<List<WaypointListDisplayModel.DisplayList>> treePages(
            List<WaypointListDisplayModel.DisplayList> groups, int pageLimit) {
        return Paging.byLines(groups, ListScreen::treeLines, pageLimit + 5);
    }

    /** A list's heading plus its rows: 4 when collapsed, at least 1 (spec 6.3). */
    static int treeLines(WaypointListDisplayModel.DisplayList group) {
        int rows = group.waypoints().size();
        return 1 + (rows > COLLAPSE_ABOVE ? PREVIEW + 1 : Math.max(1, rows));
    }

    private static Component treeView(DimensionStyle dims, String dimension, Totals totals,
                                      List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<List<WaypointListDisplayModel.DisplayList>> pages = treePages(groups, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(WaypointRefs.listLink(dims, list, WHITE, Click.run(detailsListCmd(dimension, list.name())),
                    "wp.hint.list_details"), ListActions.plus(dims, dimension, list));
            List<SimpleWaypoint> rows = group.waypoints();
            if (rows.isEmpty()) {
                lines.line(text("  "), translatable("wp.list.empty", GRAY).decorate(TextDecoration.ITALIC));
            }
            boolean collapsed = rows.size() > COLLAPSE_ABOVE;
            for (SimpleWaypoint waypoint : collapsed ? rows.subList(0, PREVIEW) : rows) {
                lines.line(text("  "), row(dims, dimension, list, waypoint, false));
            }
            if (collapsed) {
                lines.line(text("  "), collapsedMore(dims, dimension, list, rows.size(), shown, pageLimit));
            }
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(), Chat.join(
                        Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints()))),
                viewRow(dims, dimension, totals, shown, ListView.TREE),
                totals.waypoints() > 0 ? sortRow(dims, dimension, target, shown) : null);
        return lines.build();
    }

    private static Component listsView(DimensionStyle dims, String dimension, Totals totals,
                                       List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                       ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<List<WaypointListDisplayModel.DisplayList>> pages = Paging.bySize(groups, pageLimit + 5);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(WaypointRefs.listLink(dims, list, WHITE,
                            Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open"),
                    Chat.SEPARATOR, text(String.valueOf(list.size()), GRAY), ListActions.plus(dims, dimension, list));
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", totals.lists()))),
                viewRow(dims, dimension, totals, shown, ListView.LISTS));
        return lines.build();
    }

    private static Component flatView(DimensionStyle dims, String dimension, Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        List<WaypointListDisplayModel.DisplayWaypoint> rows = WaypointListDisplayModel.build(result, false).flatWaypoints();
        List<List<WaypointListDisplayModel.DisplayWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayWaypoint row : pages.get(shown.page() - 1)) {
            lines.add(row(dims, dimension, row.sourceList(), row.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                viewRow(dims, dimension, totals, shown, ListView.FLAT), sortRow(dims, dimension, target, shown));
        return lines.build();
    }

    /** "… 4 more" opening the whole list, keeping the search and the sort. */
    private static Component collapsedMore(DimensionStyle dims, String dimension, WaypointList list, int rows,
                                           ListQuery shown, int pageLimit) {
        Viewer viewer = dims.viewer();
        String command = ListTarget.list(dimension, list.name()).command(shown.withView(ListView.DEFAULT));
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), translatable("wp.more.rows", text(rows - PREVIEW)));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command),
                Tooltip.of("wp.open", WaypointRefs.label(list.displayName(), list.name()))
                        .line(translatable("wp.list.all_rows", Chat.count("wp.count.waypoint", rows), text(pageLimit))));
    }

    /** Lists · Tree · Flat · Search · New list; Flat and Search only when there are waypoints. */
    private static @Nullable Component viewRow(DimensionStyle dims, String dimension, Totals totals, ListQuery shown,
                                               ListView current) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimension(dimension);
        boolean waypoints = totals.waypoints() > 0;
        List<Component> extras = new ArrayList<>();
        if (waypoints) {
            extras.add(ListControls.search(viewer, target, translatable("wp.search.in", dims.name(dimension))));
        }
        extras.add(ListActions.newList(dims, dimension));
        return ListControls.viewRow(viewer, target, shown, current,
                waypoints ? List.of(ListView.LISTS, ListView.TREE, ListView.FLAT) : List.of(ListView.LISTS, ListView.TREE),
                extras);
    }

    private static @Nullable Component sortRow(DimensionStyle dims, String dimension, ListTarget target, ListQuery shown) {
        Viewer viewer = dims.viewer();
        return ListControls.sortRow(viewer, target, shown, SORTS, "wp.sort.default.saved",
                viewer.isIn(dimension) ? null : translatable("wp.sort.distance.unavailable", dims.name(dimension)));
    }
}
