package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** The dimension list and all dimensions (spec 7). */
public final class DimensionScreens {
    /** A dimension with its lists in saved order. */
    public record DimensionLists(String dimension, List<WaypointList> lists) {
        public int waypoints() {
            return this.lists.stream().mapToInt(WaypointList::size).sum();
        }
    }

    /** A line of the dimension list, and how many dimensions it shows. */
    private record Row(Component line, int dimensions) {
    }

    /** A dimension heading (list is null) or one of its lists, in /wp list all. */
    private record Block(DimensionLists dimension, @Nullable WaypointList list) {
        boolean heading() {
            return this.list == null;
        }
    }

    private record Match(String dimension, WaypointList list, SimpleWaypoint waypoint) {
    }

    private DimensionScreens() {
    }

    /** /wp list dimensions */
    public static Component dimensionList(DimensionStyle dims, List<DimensionLists> dimensions, int page, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.dimensions();
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        List<Row> rows = new ArrayList<>();
        List<DimensionLists> empty = new ArrayList<>();
        for (DimensionLists dimension : ordered(dims, dimensions)) {
            if (dimension.waypoints() > 0 || viewer.isIn(dimension.dimension())) {
                rows.add(new Row(dimensionRow(dims, dimension), 1));
            } else {
                empty.add(dimension);
            }
        }
        if (!empty.isEmpty()) {
            rows.add(new Row(Chat.concat(translatable("wp.dimensions.no_waypoints", DARK_GRAY), text(" "),
                    Chat.join(empty.stream().map(dimension -> dimensionLink(dims, dimension, GRAY)).toList())), empty.size()));
        }
        List<List<Row>> pages = Paging.bySize(rows, pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        ChatLines lines = new ChatLines().line(translatable("wp.dimensions.title", GOLD), text("  "),
                translatable("wp.dimensions.on_server", GRAY, text(dimensions.size())));
        pages.get(page - 1).forEach(row -> lines.add(row.line()));
        int after = pages.subList(page, pages.size()).stream().flatMap(List::stream).mapToInt(Row::dimensions).sum();
        lines.add(ListControls.more(viewer, "dimension", after, target.command(query.withPage(page + 1))));
        if (!viewer.plainText()) {
            int total = dimensions.stream().mapToInt(DimensionLists::waypoints).sum();
            lines.line(Chat.link(viewer, translatable("wp.all.title"), AQUA, Click.run("/wp list all"),
                            Tooltip.of("wp.all.tooltip")), Chat.SEPARATOR, text(String.valueOf(total), GRAY),
                    ListControls.pager(viewer, target, query, pages.size(), ListControls.pageDetail(pageLimit + 5,
                            Chat.count("wp.count.dimension", dimensions.size()))));
        }
        return lines.buildScreen();
    }

    /** /wp list all */
    public static Component all(DimensionStyle dims, List<DimensionLists> dimensions, ListQuery query, int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.allDimensions();
        List<Block> blocks = new ArrayList<>();
        for (DimensionLists dimension : ordered(dims, dimensions)) {
            if (dimension.lists().isEmpty()) {
                continue;
            }
            blocks.add(new Block(dimension, null));
            dimension.lists().forEach(list -> blocks.add(new Block(dimension, list)));
        }
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        if (blocks.isEmpty()) {
            return lines.add(translatable("wp.dimension.no_lists.sentence", GRAY)).buildScreen();
        }
        List<List<Block>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Block> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(heading(dims, page.get(0).dimension(), true));
        }
        for (Block block : page) {
            lines.add(block.heading() ? heading(dims, block.dimension(), false) : listRow(dims, block));
        }
        int listsAfter = (int) pages.subList(query.page(), pages.size()).stream().flatMap(List::stream)
                .filter(block -> !block.heading()).count();
        lines.add(ListControls.more(viewer, "list", listsAfter, target.command(query.withPage(query.page() + 1))));
        int listCount = (int) blocks.stream().filter(block -> !block.heading()).count();
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(), translatable("wp.in",
                        Chat.count("wp.count.list", listCount), Chat.count("wp.count.dimension", blocks.size() - listCount))),
                ListControls.search(viewer, target, translatable("wp.search.everywhere")));
        return lines.buildScreen();
    }

    /** /wp list all search <text>: matches under dimension headings, pageLimit rows per page. */
    public static Component allSearch(DimensionStyle dims, WaypointQueryEngine.QueryResult result, ListQuery query,
                                      int pageLimit) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.allDimensions();
        String clear = target.command(query.withSearch(""));
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        List<Match> rows = new ArrayList<>();
        result.dimensions().stream()
                .sorted(Comparator.comparing(WaypointQueryEngine.DimensionResult::dimensionName, dims.order()))
                .forEach(dimension -> dimension.lists().forEach(list -> list.waypoints().forEach(waypoint ->
                        rows.add(new Match(dimension.dimensionName(), list.sourceList(), waypoint)))));
        if (rows.isEmpty()) {
            return lines.add(ListControls.noMatches(viewer, query.search(), clear)).buildScreen();
        }
        List<List<Match>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        String last = null;
        for (Match match : pages.get(query.page() - 1)) {
            if (!match.dimension().equals(last)) {
                Component mark = dims.hereMark(match.dimension());
                lines.line(dimensionLink(dims, new DimensionLists(match.dimension(), List.of()), dims.color(match.dimension()), false),
                        mark == null ? null : text(" "), mark);
                last = match.dimension();
            }
            lines.line(text("  "), ListScreen.row(dims, match.dimension(), match.list(), match.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit, Chat.count("wp.count.match", rows.size()))));
        return lines.buildScreen();
    }

    /** The viewer's dimension first, then Overworld, Nether and End, then the rest A–Z. */
    private static List<DimensionLists> ordered(DimensionStyle dims, List<DimensionLists> dimensions) {
        return dimensions.stream().sorted(Comparator.comparing(DimensionLists::dimension, dims.order())).toList();
    }

    /** Pages of at most budget blocks; a heading never ends a page, since its first list would leave it. */
    private static List<List<Block>> pageBlocks(List<Block> blocks, int budget) {
        List<List<Block>> pages = new ArrayList<>();
        List<Block> page = new ArrayList<>();
        for (Block block : blocks) {
            boolean full = page.size() >= budget || block.heading() && page.size() >= budget - 1;
            if (full && !page.isEmpty()) {
                pages.add(page);
                page = new ArrayList<>();
            }
            page.add(block);
        }
        pages.add(page);
        return pages;
    }

    private static Component allTitle(Viewer viewer) {
        if (viewer.plainText()) {
            return translatable("wp.all.title", GOLD);
        }
        return Chat.link(viewer, Chat.concat(translatable("wp.all.title"), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp list dimensions"), Tooltip.of("wp.dimensions.choose"));
    }

    /** Twilight Forest · 3 ● */
    private static Component dimensionRow(DimensionStyle dims, DimensionLists dimension) {
        int waypoints = dimension.waypoints();
        Component mark = dims.hereMark(dimension.dimension());
        return Chat.concat(dimensionLink(dims, dimension, waypoints > 0 ? dims.color(dimension.dimension()) : GRAY),
                Chat.SEPARATOR, text(String.valueOf(waypoints), waypoints > 0 ? GRAY : DARK_GRAY),
                mark == null ? null : text(" "), mark);
    }

    /** Overworld · 12 ●, or Overworld (continued) ● at the top of a page. */
    private static Component heading(DimensionStyle dims, DimensionLists dimension, boolean continued) {
        Component mark = dims.hereMark(dimension.dimension());
        Component tail = continued
                ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                : Chat.concat(Chat.SEPARATOR, text(String.valueOf(dimension.waypoints()), GRAY));
        return Chat.concat(dimensionLink(dims, dimension, dims.color(dimension.dimension())), tail,
                mark == null ? null : text(" "), mark);
    }

    private static Component listRow(DimensionStyle dims, Block block) {
        WaypointList list = block.list();
        String dimension = block.dimension().dimension();
        return Chat.concat(text("  "), WaypointRefs.listLink(dims, list, WHITE,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)), "wp.hint.open"),
                Chat.SEPARATOR, text(String.valueOf(list.size()), GRAY));
    }

    private static Component dimensionLink(DimensionStyle dims, DimensionLists dimension, TextColor color) {
        return dimensionLink(dims, dimension, color, true);
    }

    /** A dimension name opening its lists; plain-text viewers read "Display (id)". */
    private static Component dimensionLink(DimensionStyle dims, DimensionLists dimension, TextColor color, boolean counts) {
        Viewer viewer = dims.viewer();
        String id = dimension.dimension();
        return Chat.link(viewer, dims.label(id), color, Click.run("/wp list " + id), dims.tooltip(id,
                counts ? DimensionStyle.counts(dimension.waypoints(), dimension.lists().size()) : null, "wp.hint.open"));
    }
}
