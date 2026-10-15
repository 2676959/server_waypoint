package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatIcons;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;

/** Remote browsing (spec 14): the server picker, all servers, a server, a dimension, a list and details. */
public final class RemoteScreens {
    private static final List<SortMode> SORTS = List.of(SortMode.DEFAULT, SortMode.NAME, SortMode.COLOR);

    /** Servers with waypoints first, then the rest, each group A–Z by name. */
    static final Comparator<Server> ORDER = Comparator
            .comparingInt((Server server) -> server.waypointCount() > 0 ? 0 : 1)
            .thenComparing(Server::displayName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(server -> server.id().value());

    /** A heading (a server or a dimension) or one of its rows, for block paging. */
    private record Line(Server server, @Nullable String dimension, @Nullable WaypointList list, boolean heading) {
    }

    private record Match(Server server, String dimension, WaypointList list, SimpleWaypoint waypoint) {
    }

    private RemoteScreens() {
    }

    /** /wp remote: every server with its state, L + 5 per page, then All servers. */
    public static Component picker(Viewer viewer, List<Server> servers, int page, int pageLimit) {
        ListTarget target = new ListTarget("/wp remote");
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        ChatLines lines = new ChatLines().line(translatable("wp.remote.title", GOLD), text("  "),
                translatable("wp.remote.connected", GRAY, Chat.count("wp.count.server", servers.size())));
        if (servers.isEmpty()) {
            return lines.add(translatable("wp.remote.none", GRAY)).buildScreen();
        }
        List<List<Server>> pages = Paging.bySize(servers.stream().sorted(ORDER).toList(), pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (Server server : pages.get(page - 1)) {
            lines.line(RemoteRefs.dot(viewer, server), text(" "), RemoteRefs.serverLink(viewer, server), Chat.SEPARATOR,
                    RemoteRefs.detail(viewer, server));
        }
        lines.add(ListControls.more(viewer, "server", Paging.after(pages, page), target.command(query.withPage(page + 1))));
        if (!viewer.plainText()) {
            int total = servers.stream().mapToInt(Server::waypointCount).sum();
            lines.line(Chat.link(viewer, translatable("wp.remote.all"), AQUA, Click.run("/wp remote list"),
                            Tooltip.of("wp.remote.all.tooltip")), Chat.SEPARATOR, text(String.valueOf(total), GRAY),
                    ListControls.pager(viewer, target, query, pages.size(),
                            ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.server", servers.size()))));
        }
        return lines.buildScreen();
    }

    /** /wp remote list: each server with its dimensions, L + 5 lines per page. */
    public static Component allServers(Viewer viewer, List<Server> servers, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(null, null, null);
        List<Line> blocks = new ArrayList<>();
        for (Server server : servers.stream().sorted(ORDER).toList()) {
            blocks.add(new Line(server, null, null, true));
            ordered(dims, server).forEach(dimension -> blocks.add(new Line(server, dimension, null, false)));
        }
        ChatLines lines = new ChatLines().add(allTitle(viewer));
        if (blocks.isEmpty()) {
            return lines.add(translatable("wp.remote.none", GRAY)).buildScreen();
        }
        List<List<Line>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Line> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(serverHeading(viewer, page.get(0).server(), true));
        }
        for (Line line : page) {
            lines.add(line.heading() ? serverHeading(viewer, line.server(), false)
                    : dimensionRow(dims, line.server(), Objects.requireNonNull(line.dimension()), "  ", false));
        }
        int dimensionsAfter = rowsAfter(pages, query.page());
        String next = target.command(query.withPage(query.page() + 1));
        lines.add(dimensionsAfter > 0 ? ListControls.more(viewer, "dimension", dimensionsAfter, next)
                : ListControls.more(viewer, "server", headingsAfter(pages, query.page()), next));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.server", servers.size()))),
                ListControls.search(viewer, target, translatable("wp.remote.search.everywhere")));
        return lines.buildScreen();
    }

    /** /wp remote list <server>: its dimensions and their lists, L + 5 lines per page. */
    public static Component server(Viewer viewer, Server server, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(server.id().value(), null, null);
        ChatLines lines = new ChatLines();
        Component title = serverTitle(viewer, server);
        if (!server.readable()) {
            Component servers = serversLink(viewer);
            return lines.add(title).line(translatable(server.state() == RemoteCatalogState.UNAUTHORIZED
                                    ? "wp.remote.no_access" : "wp.remote.unreachable", GRAY,
                            RemoteRefs.label(server.displayName(), server.id().value())),
                    servers == null ? null : text(" "), servers).buildScreen();
        }
        if (server.dimensions().isEmpty()) {
            return lines.add(title).add(translatable("wp.remote.nothing", GRAY)).buildScreen();
        }
        lines.line(title, text("  "), Chat.colored(Chat.join(Chat.count("wp.count.dimension", server.dimensions().size()),
                Chat.count("wp.count.waypoint", server.waypointCount())), GRAY));
        List<Line> blocks = new ArrayList<>();
        for (String dimension : ordered(dims, server)) {
            blocks.add(new Line(server, dimension, null, true));
            Objects.requireNonNull(server.lists(dimension)).forEach(list -> blocks.add(new Line(server, dimension, list, false)));
        }
        List<List<Line>> pages = pageBlocks(blocks, pageLimit + 5);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        List<Line> page = pages.get(query.page() - 1);
        if (!page.get(0).heading()) {
            lines.add(dimensionRow(dims, server, Objects.requireNonNull(page.get(0).dimension()), "", true));
        }
        for (Line line : page) {
            String dimension = Objects.requireNonNull(line.dimension());
            lines.add(line.heading() ? dimensionRow(dims, server, dimension, "", false)
                    : Chat.concat(text("  "), RemoteRefs.listLink(dims, server, dimension, Objects.requireNonNull(line.list()),
                            WHITE, ListQuery.DEFAULT), Chat.SEPARATOR, text(String.valueOf(line.list().size()), GRAY)));
        }
        lines.add(ListControls.more(viewer, "list", rowsAfter(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", server.listCount()))),
                ListControls.search(viewer, target, translatable("wp.remote.search.server",
                        RemoteRefs.label(server.displayName(), server.id().value()))));
        return lines.buildScreen();
    }

    /**
     * /wp remote list [<server>] search <text>: matches under headings, L rows per page. Across all
     * servers a heading names the server and the dimension; on one server only the dimension.
     */
    public static Component search(Viewer viewer, @Nullable Server only, List<Server> servers, ListQuery query,
                                   int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = only == null ? ListTarget.remote(null, null, null) : ListTarget.remote(only.id().value(), null, null);
        String clear = target.command(query.withSearch(""));
        ChatLines lines = new ChatLines().add(only == null ? allTitle(viewer) : serverTitle(viewer, only));
        List<Match> matches = new ArrayList<>();
        for (Server server : only == null ? servers.stream().sorted(ORDER).toList() : List.of(only)) {
            if (!server.readable()) {
                continue;
            }
            WaypointQueryEngine.queryLists(server.dimensions(), engineQuery(query)).dimensions().stream()
                    .sorted(Comparator.comparing(WaypointQueryEngine.DimensionResult::dimensionName, dims.order()))
                    .forEach(dimension -> dimension.lists().forEach(list -> list.waypoints().forEach(waypoint ->
                            matches.add(new Match(server, dimension.dimensionName(), list.sourceList(), waypoint)))));
        }
        if (matches.isEmpty()) {
            return lines.add(ListControls.noMatches(viewer, query.search(), clear)).buildScreen();
        }
        List<List<Match>> pages = Paging.bySize(matches, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(ListControls.searchLine(viewer, query.search(), matches.size(), clear));
        String lastServer = null;
        String lastDimension = null;
        for (Match match : pages.get(query.page() - 1)) {
            String server = match.server().id().value();
            if (!server.equals(lastServer) || !match.dimension().equals(lastDimension)) {
                lines.add(only == null
                        ? Chat.concat(RemoteRefs.serverLink(viewer, match.server()), text(" "),
                        RemoteRefs.dot(viewer, match.server()), Chat.CRUMB, dimensionLink(dims, match.server(), match.dimension()))
                        : dimensionLink(dims, match.server(), match.dimension()));
                lastServer = server;
                lastDimension = match.dimension();
            }
            lines.line(text("  "), RemoteRefs.row(dims, match.server(), match.dimension(), match.list(), match.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit, Chat.count("wp.count.match", matches.size()))));
        return lines.buildScreen();
    }

    /** /wp remote list <server> <dimension>: the local views (spec 6.2) with remote actions. */
    public static Component dimension(Viewer viewer, Server server, String dimension, ListQuery query, int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        ListScreen.Totals totals = new ListScreen.Totals(lists.size(), lists.stream().mapToInt(WaypointList::size).sum());
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        ChatLines lines = new ChatLines().add(dimensionHeader(dims, server, dimension, totals));
        if (totals.lists() == 0) {
            return lines.add(translatable("wp.dimension.no_lists.sentence", GRAY)).buildScreen();
        }
        WaypointQueryEngine.QueryResult result = WaypointQueryEngine.queryLists(Map.of(dimension, lists), engineQuery(query));
        if (query.searching() && result.listCount() == 0) {
            return lines.add(ListControls.noMatches(viewer, query.search(), target.command(query.withSearch("")))).buildScreen();
        }
        List<WaypointListDisplayModel.DisplayList> groups = WaypointListDisplayModel.build(result, true).lists();
        ListView view = ListScreen.resolveView(query, groups, totals, pageLimit);
        ListQuery shown = query.withView(view).withPage(query.page());
        if (query.searching()) {
            lines.add(ListControls.searchLine(viewer, query.search(), result.waypointCount(),
                    target.command(query.withSearch(""))));
        }
        return switch (view) {
            case LISTS -> listsView(dims, server, dimension, totals, groups, shown, pageLimit, lines);
            case FLAT -> flatView(dims, server, dimension, totals, result, shown, pageLimit, lines);
            default -> treeView(dims, server, dimension, totals, groups, shown, pageLimit, lines);
        };
    }

    /** /wp remote list <server> <dimension> <list> */
    public static Component list(Viewer viewer, Server server, String dimension, WaypointList list, ListQuery query,
                                 int pageLimit) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        ListTarget target = ListTarget.remote(server.id().value(), dimension, list.name());
        Component crumb = Chat.concat(crumbs(dims, server, dimension), Chat.CRUMB,
                Chat.hover(viewer, Chat.colored(RemoteRefs.label(viewer, list.displayName(), list.name()), GOLD),
                        RemoteRefs.listTooltip(list, null)));
        ChatLines lines = new ChatLines();
        if (list.isEmpty()) {
            return lines.add(crumb).add(translatable("wp.list.empty.sentence", GRAY)).buildScreen();
        }
        lines.line(crumb, text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY));
        WaypointQueryEngine.QueryResult result = WaypointQueryEngine.queryLists(Map.of(dimension, List.of(list)),
                engineQuery(query));
        List<SimpleWaypoint> rows = result.dimensions().isEmpty()
                ? List.of()
                : result.dimensions().get(0).lists().get(0).waypoints();
        if (query.searching()) {
            String clear = target.command(query.withSearch(""));
            if (rows.isEmpty()) {
                return lines.add(ListControls.noMatches(viewer, query.search(), clear)).buildScreen();
            }
            lines.add(ListControls.searchLine(viewer, query.search(), rows.size(), clear));
        }
        List<List<SimpleWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (query.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        for (SimpleWaypoint waypoint : pages.get(query.page() - 1)) {
            lines.add(RemoteRefs.row(dims, server, dimension, list, waypoint, false));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, query.page()),
                target.command(query.withPage(query.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                ListControls.search(viewer, target, translatable("wp.search.list",
                        RemoteRefs.label(list.displayName(), list.name()))),
                sortRow(viewer, target, query));
        return lines.buildScreen();
    }

    /** /wp remote details: read-only, with Identifier first when the display name differs. */
    public static Component details(Viewer viewer, Server server, String dimension, WaypointList list,
                                    SimpleWaypoint waypoint) {
        DimensionStyle dims = DimensionStyle.remote(viewer);
        Viewer reader = dims.viewer();
        ChatLines lines = new ChatLines().line(crumbs(dims, server, dimension), Chat.CRUMB,
                RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT), Chat.CRUMB,
                RemoteRefs.title(dims, server, dimension, list, waypoint));
        if (!waypoint.displayName().equals(waypoint.name())) {
            lines.add(property("identifier", text(RemoteRefs.truncate(waypoint.name()))));
        }
        lines.add(property("position", text(DimensionStyle.coordinates(waypoint.pos()))));
        lines.add(property("color", PickerScreens.colorValue(waypoint.rgb())));
        lines.add(property("yaw", PickerScreens.yawValue(waypoint.yaw())));
        lines.add(property("visibility", translatable(waypoint.global() ? "wp.visibility.global" : "wp.visibility.local")));
        lines.add(property("keywords", waypoint.keywords().isEmpty() ? none()
                : text(RemoteRefs.truncate(String.join(", ", waypoint.keywords())))));
        lines.add(property("description", waypoint.description().isBlank() ? none()
                : text(RemoteRefs.truncate(waypoint.description()))));
        Component teleport = null;
        if (reader.can(Viewer.Permission.REMOTE_TP)) {
            teleport = server.available()
                    ? Chat.button(reader, translatable("wp.action.teleport"), LIGHT_PURPLE,
                    Click.run(RemoteRefs.command("tp", server, dimension, list, waypoint)),
                    RemoteRefs.teleportTooltip(dims, server, dimension, waypoint))
                    : Chat.disabledButton(reader, translatable("wp.action.teleport"), Tooltip.of("wp.remote.teleport.stale",
                    RemoteRefs.label(server.displayName(), server.id().value())));
        }
        List<Component> buttons = Arrays.asList(teleport, Chat.button(reader, translatable("wp.action.back"), GRAY,
                Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(ListQuery.DEFAULT)),
                Tooltip.of("wp.action.back.tooltip", RemoteRefs.label(list.displayName(), list.name()))));
        if (!Chat.isEmpty(buttons)) {
            lines.add(Chat.spaced(buttons));
        }
        return lines.buildScreen();
    }

    /** ✘ No server called x. Servers */
    public static Component noServer(Viewer viewer, String id) {
        return Chat.error(translatable("wp.error.remote.no_server", text(RemoteRefs.truncate(id))), serversLink(viewer));
    }

    /** ✘ Survival has no dimension x. Browse */
    public static Component noDimension(Viewer viewer, Server server, String dimension) {
        return Chat.error(translatable("wp.error.remote.no_dimension", RemoteRefs.serverName(viewer, server),
                text(RemoteRefs.truncate(dimension))), browse(viewer, ListTarget.remote(server.id().value(), null, null)));
    }

    /** ✘ Survival has no list x in Overworld. Browse */
    public static Component noList(Viewer viewer, Server server, String dimension, String list) {
        return Chat.error(translatable("wp.error.remote.no_list", RemoteRefs.serverName(viewer, server),
                        text(RemoteRefs.truncate(list)), DimensionStyle.remote(viewer).name(dimension)),
                browse(viewer, ListTarget.remote(server.id().value(), dimension, null)));
    }

    /** ✘ No waypoint called x in Farms. Open Farms */
    public static Component noWaypoint(Viewer viewer, Server server, String dimension, WaypointList list, String waypoint) {
        Component name = RemoteRefs.label(list.displayName(), list.name());
        return Chat.error(translatable("wp.error.no_waypoint", text(RemoteRefs.truncate(waypoint)), name),
                Chat.control(viewer, translatable("wp.open", name), AQUA,
                        Click.run(ListTarget.remote(server.id().value(), dimension, list.name()).command(ListQuery.DEFAULT)),
                        RemoteRefs.listTooltip(list, "wp.hint.open")));
    }

    /** Independent permission statuses; labels and glyphs are literal components. */
    public static Component permissionCheckFailed(Component serverName,
            _959.server_waypoint.crossserver.TeleportPermissionCheck permissions) {
        return Chat.error(Chat.concat(translatable("wp.remote.tp.permission_check_failed", Chat.colored(serverName, WHITE)),
                text(": "), Chat.join(permissionStatus("tp", permissions.tp()),
                        permissionStatus("remote.tp", permissions.remoteTp()))));
    }

    private static Component permissionStatus(String permission, boolean allowed) {
        return text(permission + " " + (allowed ? Chat.CHECK : Chat.CROSS), allowed ? GREEN : RED);
    }

    public static Component distanceUnavailable() {
        return Errors.of("wp.error.remote.distance");
    }

    /** Switching you to Survival for [IF] Iron Farm… */
    public static Component switching(Viewer viewer, Server server, String dimension, WaypointList list,
                                      SimpleWaypoint waypoint) {
        return Chat.colored(Chat.concat(translatable("wp.remote.switching",
                        Chat.colored(RemoteRefs.serverName(viewer, server), WHITE), RemoteRefs.plain(viewer, waypoint)),
                text(Chat.ELLIPSIS)), GRAY);
    }

    /**
     * ✘ Why a switch failed, with Try again where retrying can help, or Open <list> when the
     * waypoint is gone. The server is named by its display name when it is cached, else by its ID.
     */
    public static Component teleportFailed(Viewer viewer, @Nullable Server server, String serverId, String dimension,
                                           String list, String waypoint, Result result) {
        Component name = server == null ? text(RemoteRefs.truncate(serverId)) : RemoteRefs.serverName(viewer, server);
        Component message = translatable("wp.remote.tp." + result.name().toLowerCase(Locale.ROOT), Chat.colored(name, WHITE));
        WaypointList cached = server == null ? null : server.list(dimension, list);
        Component listName = cached == null ? text(RemoteRefs.truncate(list)) : RemoteRefs.label(cached.displayName(), list);
        Component recovery = switch (result) {
            case NOT_FOUND -> Chat.control(viewer, translatable("wp.open", listName), AQUA,
                    Click.run(ListTarget.remote(serverId, dimension, list).command(ListQuery.DEFAULT)),
                    Tooltip.of("wp.remote.browse.tooltip"));
            case UNAUTHORIZED, WRONG_SOURCE, WRONG_DESTINATION, UNSUPPORTED, SUCCESS -> null;
            default -> Chat.control(viewer, translatable("wp.remote.try_again"), AQUA,
                    Click.run("/wp remote tp " + escapeListName(serverId) + " " + dimension + " "
                            + escapeListName(list) + " " + escapeListName(waypoint)),
                    Tooltip.of("wp.remote.try_again.tooltip"));
        };
        return Chat.error(message, recovery);
    }

    /**
     * At the destination: ✔ Arrived at [IF] Iron Farm on survival, naming this server by its ID,
     * or why the arrival failed. The waypoint is this server's own, when it still exists.
     */
    public static Component arrival(ChatIcons icons, Result result, String server, @Nullable String waypointName,
                                    @Nullable SimpleWaypoint waypoint) {
        Component serverName = Chat.colored(text(RemoteRefs.truncate(server)), WHITE);
        if (result != Result.SUCCESS) {
            return Chat.error(translatable("wp.remote.tp." + result.name().toLowerCase(Locale.ROOT), serverName));
        }
        Viewer arriving = new Viewer(Set.of(), true, false, null, null, 0F, icons);
        Component target = waypoint != null ? WaypointRefs.plain(arriving, waypoint)
                : Chat.colored(text(RemoteRefs.truncate(Objects.requireNonNullElse(waypointName, ""))), WHITE);
        return Chat.ok(translatable("wp.remote.arrived", target, serverName));
    }

    private static WaypointQueryEngine.Query engineQuery(ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), null, null, query.descending());
    }

    private static Component treeView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                      List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<List<WaypointListDisplayModel.DisplayList>> pages = ListScreen.treePages(groups, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.add(RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT));
            List<SimpleWaypoint> rows = group.waypoints();
            if (rows.isEmpty()) {
                lines.line(text("  "), translatable("wp.list.empty", GRAY).decorate(TextDecoration.ITALIC));
            }
            boolean collapsed = rows.size() > ListScreen.COLLAPSE_ABOVE;
            for (SimpleWaypoint waypoint : collapsed ? rows.subList(0, ListScreen.PREVIEW) : rows) {
                lines.line(text("  "), RemoteRefs.row(dims, server, dimension, list, waypoint, false));
            }
            if (collapsed) {
                lines.line(text("  "), collapsedMore(dims, server, dimension, list, rows.size(), shown, pageLimit));
            }
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(), Chat.join(
                        Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints()))),
                viewRow(dims, server, dimension, totals, shown, ListView.TREE),
                totals.waypoints() > 0 ? sortRow(viewer, target, shown) : null);
        return lines.buildScreen();
    }

    private static Component listsView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                       List<WaypointListDisplayModel.DisplayList> groups, ListQuery shown, int pageLimit,
                                       ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<List<WaypointListDisplayModel.DisplayList>> pages = Paging.bySize(groups, pageLimit + 5);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayList group : pages.get(shown.page() - 1)) {
            WaypointList list = group.sourceList();
            lines.line(RemoteRefs.listLink(dims, server, dimension, list, WHITE, ListQuery.DEFAULT), Chat.SEPARATOR,
                    text(String.valueOf(list.size()), GRAY));
        }
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", totals.lists()))),
                viewRow(dims, server, dimension, totals, shown, ListView.LISTS));
        return lines.buildScreen();
    }

    private static Component flatView(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals,
                                      WaypointQueryEngine.QueryResult result, ListQuery shown, int pageLimit,
                                      ChatLines lines) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        List<WaypointListDisplayModel.DisplayWaypoint> rows = WaypointListDisplayModel.build(result, false).flatWaypoints();
        List<List<WaypointListDisplayModel.DisplayWaypoint>> pages = Paging.bySize(rows, pageLimit);
        if (shown.page() > pages.size()) {
            return ListControls.pageNotFound(viewer, target, shown, pages.size());
        }
        for (WaypointListDisplayModel.DisplayWaypoint row : pages.get(shown.page() - 1)) {
            lines.add(RemoteRefs.row(dims, server, dimension, row.sourceList(), row.waypoint(), true));
        }
        lines.add(ListControls.more(viewer, "waypoint", Paging.after(pages, shown.page()),
                target.command(shown.withPage(shown.page() + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, shown, pages.size(),
                        ListControls.pageDetail(pageLimit, Chat.count("wp.count.waypoint", rows.size()))),
                viewRow(dims, server, dimension, totals, shown, ListView.FLAT), sortRow(viewer, target, shown));
        return lines.buildScreen();
    }

    /** "… 4 more" opening the whole list on its server, keeping the search and the sort. */
    private static Component collapsedMore(DimensionStyle dims, Server server, String dimension, WaypointList list,
                                           int rows, ListQuery shown, int pageLimit) {
        Viewer viewer = dims.viewer();
        String command = ListTarget.remote(server.id().value(), dimension, list.name()).command(shown.withView(ListView.DEFAULT));
        Component label = Chat.concat(text(Chat.ELLIPSIS + " "), translatable("wp.more.rows", text(rows - ListScreen.PREVIEW)));
        if (viewer.plainText()) {
            return translatable("wp.plain.continue", AQUA, label, text(command));
        }
        return Chat.link(viewer, label, AQUA, Click.run(command),
                Tooltip.of("wp.open", RemoteRefs.label(list.displayName(), list.name()))
                        .line(translatable("wp.list.all_rows", Chat.count("wp.count.waypoint", rows), text(pageLimit))));
    }

    /** Lists · Tree · Flat · Search; Flat and Search only when there are waypoints. */
    private static @Nullable Component viewRow(DimensionStyle dims, Server server, String dimension,
                                               ListScreen.Totals totals, ListQuery shown, ListView current) {
        Viewer viewer = dims.viewer();
        ListTarget target = ListTarget.remote(server.id().value(), dimension, null);
        boolean waypoints = totals.waypoints() > 0;
        return ListControls.viewRow(viewer, target, shown, current,
                waypoints ? List.of(ListView.LISTS, ListView.TREE, ListView.FLAT) : List.of(ListView.LISTS, ListView.TREE),
                waypoints ? Collections.singletonList(ListControls.search(viewer, target,
                        translatable("wp.search.in", dims.name(dimension)))) : List.of());
    }

    private static @Nullable Component sortRow(Viewer viewer, ListTarget target, ListQuery shown) {
        return ListControls.sortRow(viewer, target, shown, SORTS, "wp.sort.default.published", null);
    }

    /** ● Survival ⏷, the name opening the server picker. */
    private static Component serverTitle(Viewer viewer, Server server) {
        Component name = viewer.plainText() ? Chat.colored(RemoteRefs.serverName(viewer, server), GOLD)
                : Chat.link(viewer, Chat.concat(RemoteRefs.serverName(viewer, server), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp remote"), RemoteRefs.serverTooltip(server, "wp.hint.choose_server"));
        return Chat.concat(RemoteRefs.dot(viewer, server), text(" "), name);
    }

    /** ● Survival ⏷ › Overworld ⏷  3 lists · 14 waypoints */
    private static Component dimensionHeader(DimensionStyle dims, Server server, String dimension, ListScreen.Totals totals) {
        Viewer viewer = dims.viewer();
        Component dimensionName = viewer.plainText() ? dims.name(dimension)
                : Chat.link(viewer, Chat.concat(DimensionStyle.displayName(dimension), text(" " + Chat.PICKER)),
                dims.color(dimension), Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                dims.tooltip(dimension, DimensionStyle.counts(totals.waypoints(), totals.lists()), "wp.hint.choose_dimension"));
        Component summary = totals.lists() == 0 ? null : Chat.concat(text("  "), Chat.colored(Chat.join(
                Chat.count("wp.count.list", totals.lists()), Chat.count("wp.count.waypoint", totals.waypoints())), GRAY));
        return Chat.concat(serverTitle(viewer, server), Chat.CRUMB, dimensionName, summary);
    }

    /** ● Survival › Overworld: the server opens its view and the dimension its lists. */
    private static Component crumbs(DimensionStyle dims, Server server, String dimension) {
        Viewer viewer = dims.viewer();
        Component serverName = Chat.link(viewer, RemoteRefs.serverName(viewer, server), WHITE,
                Click.run(ListTarget.remote(server.id().value(), null, null).command(ListQuery.DEFAULT)),
                RemoteRefs.serverTooltip(server, "wp.hint.open"));
        return Chat.concat(RemoteRefs.dot(viewer, server), text(" "), serverName, Chat.CRUMB,
                dimensionLink(dims, server, dimension));
    }

    private static Component allTitle(Viewer viewer) {
        if (viewer.plainText()) {
            return translatable("wp.remote.all", GOLD);
        }
        return Chat.link(viewer, Chat.concat(translatable("wp.remote.all"), text(" " + Chat.PICKER)), GOLD,
                Click.run("/wp remote"), Tooltip.of("wp.remote.choose"));
    }

    /** Creative Plots ● · 4, or Survival ● (continued) at the top of a page. */
    private static Component serverHeading(Viewer viewer, Server server, boolean continued) {
        return Chat.concat(RemoteRefs.serverLink(viewer, server), text(" "), RemoteRefs.dot(viewer, server),
                continued ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                        : Chat.concat(Chat.SEPARATOR, RemoteRefs.detail(viewer, server)));
    }

    /** Overworld · 14, indented under a server in All servers, or Overworld (continued). */
    private static Component dimensionRow(DimensionStyle dims, Server server, String dimension, String indent,
                                          boolean continued) {
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        int waypoints = lists.stream().mapToInt(WaypointList::size).sum();
        return Chat.concat(text(indent), dimensionLink(dims, server, dimension), continued
                ? Chat.concat(text(" "), translatable("wp.all.continued", DARK_GRAY))
                : Chat.concat(Chat.SEPARATOR, text(String.valueOf(waypoints), GRAY)));
    }

    /** A dimension name in its colour, opening its lists on that server. */
    private static Component dimensionLink(DimensionStyle dims, Server server, String dimension) {
        Viewer viewer = dims.viewer();
        List<WaypointList> lists = Objects.requireNonNull(server.lists(dimension));
        Component label = viewer.plainText() ? dims.name(dimension) : DimensionStyle.displayName(dimension);
        return Chat.link(viewer, label, dims.color(dimension),
                Click.run(ListTarget.remote(server.id().value(), dimension, null).command(ListQuery.DEFAULT)),
                dims.tooltip(dimension, DimensionStyle.counts(lists.stream().mapToInt(WaypointList::size).sum(),
                        lists.size()), "wp.hint.open"));
    }

    private static List<String> ordered(DimensionStyle dims, Server server) {
        return server.dimensions().keySet().stream().sorted(dims.order()).toList();
    }

    /** Pages of at most budget lines; a heading never ends a page, since its first row would leave it. */
    private static List<List<Line>> pageBlocks(List<Line> blocks, int budget) {
        List<List<Line>> pages = new ArrayList<>();
        List<Line> page = new ArrayList<>();
        for (Line block : blocks) {
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

    private static int rowsAfter(List<List<Line>> pages, int page) {
        return (int) pages.subList(page, pages.size()).stream().flatMap(List::stream).filter(line -> !line.heading()).count();
    }

    private static int headingsAfter(List<List<Line>> pages, int page) {
        return (int) pages.subList(page, pages.size()).stream().flatMap(List::stream).filter(Line::heading).count();
    }

    private static @Nullable Component serversLink(Viewer viewer) {
        return Chat.control(viewer, translatable("wp.remote.servers"), AQUA, Click.run("/wp remote"),
                Tooltip.of("wp.remote.choose"));
    }

    private static @Nullable Component browse(Viewer viewer, ListTarget target) {
        return Chat.control(viewer, translatable("wp.remote.browse"), AQUA, Click.run(target.command(ListQuery.DEFAULT)),
                Tooltip.of("wp.remote.browse.tooltip"));
    }

    private static Component property(String field, Component value) {
        return translatable("wp.details.property", GRAY, translatable("wp.details." + field), Chat.colored(value, WHITE));
    }

    private static Component none() {
        return translatable("wp.details.none", DARK_GRAY).decorate(TextDecoration.ITALIC);
    }
}
