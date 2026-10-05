package _959.server_waypoint.command;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.handoff.RemoteTeleportInitiator;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.RemoteScreens;
import _959.server_waypoint.text.feedback.RemoteRefs;
import _959.server_waypoint.util.StringCommandBuilder;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static net.kyori.adventure.text.Component.*;

/** Vanilla-safe remote commands. Suggestions and target resolution use only the bounded local replica. */
final class RemoteWaypointCommand<S> {
    private static final String SERVER = "remote server", DIMENSION = "remote dimension", LIST = "remote list", WAYPOINT = "remote waypoint";
    private final Supplier<RemoteCatalogStore> store;
    private final BiConsumer<S, Component> send, error;
    private final IntSupplier defaultLimit;
    private final Predicate<S> canList, canTeleport;
    private final RemoteTeleportInitiator<S> teleport;
    private final Function<S, Component> helpScreen;
    private final Function<S, Viewer> viewer;

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList,
                          Predicate<S> canTeleport, RemoteTeleportInitiator<S> teleport,
                          Function<S, Component> helpScreen, Function<S, Viewer> viewer) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.canTeleport = Objects.requireNonNull(canTeleport, "canTeleport");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.helpScreen = Objects.requireNonNull(helpScreen, "helpScreen");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.store = store; this.send = send; this.error = error; this.defaultLimit = defaultLimit;
    }
    LiteralArgumentBuilder<S> build() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("remote").requires(this::canUse)
                .executes(context -> picker(context.getSource(), 1));
        root.then(LiteralArgumentBuilder.<S>literal(PAGE_COMMAND).requires(canList)
                .then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1))
                        .executes(context -> picker(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));
        LiteralArgumentBuilder<S> lists = LiteralArgumentBuilder.<S>literal("list").requires(canList); configure(lists, 0);
        RequiredArgumentBuilder<S, String> server = argument(SERVER, string()); configure(server, 1);
        server.suggests((context, builder) -> suggest(context, builder, 0));
        RequiredArgumentBuilder<S, String> dimension = argument(DIMENSION, string()); configure(dimension, 2);
        dimension.suggests((context, builder) -> suggest(context, builder, 1));
        RequiredArgumentBuilder<S, String> list = argument(LIST, string()); configure(list, 3);
        list.suggests((context, builder) -> suggest(context, builder, 2));
        root.then(lists.then(server.then(dimension.then(list))));
        RequiredArgumentBuilder<S, String> detailsServer = argument(SERVER, string());
        RequiredArgumentBuilder<S, String> detailsDimension = argument(DIMENSION, string());
        RequiredArgumentBuilder<S, String> detailsList = argument(LIST, string());
        RequiredArgumentBuilder<S, String> detailsWaypoint = argument(WAYPOINT, string());
        detailsServer.suggests((context, builder) -> suggest(context, builder, 0));
        detailsDimension.suggests((context, builder) -> suggest(context, builder, 1));
        detailsList.suggests((context, builder) -> suggest(context, builder, 2));
        detailsWaypoint.suggests((context, builder) -> suggest(context, builder, 3)).executes(this::details);
        root.then(LiteralArgumentBuilder.<S>literal("details").requires(canList)
                .then(detailsServer.then(detailsDimension.then(detailsList.then(detailsWaypoint)))));
        RequiredArgumentBuilder<S, String> tpServer = argument(SERVER, string());
        RequiredArgumentBuilder<S, String> tpDimension = argument(DIMENSION, string());
        RequiredArgumentBuilder<S, String> tpList = argument(LIST, string());
        RequiredArgumentBuilder<S, String> tpWaypoint = argument(WAYPOINT, string());
        tpServer.suggests((context, builder) -> suggest(context, builder, 0, true));
        tpDimension.suggests((context, builder) -> suggest(context, builder, 1, true));
        tpList.suggests((context, builder) -> suggest(context, builder, 2, true));
        tpWaypoint.suggests((context, builder) -> suggest(context, builder, 3, true)).executes(this::teleport);
        return root.then(LiteralArgumentBuilder.<S>literal("tp").requires(canTeleport)
                .then(tpServer.then(tpDimension.then(tpList.then(tpWaypoint)))));
    }
    boolean canList(S source) { return canList.test(source); }
    boolean canTeleport(S source) { return canTeleport.test(source); }
    boolean canUse(S source) { return canList.test(source) || canTeleport.test(source); }
    int help(S source) {
        if (!canUse(source)) return 0;
        send.accept(source, helpScreen.apply(source));
        return Command.SINGLE_SUCCESS;
    }
    /** /wp remote: the server picker; readers who may only teleport get the remote help. */
    private int picker(S source, int page) {
        if (!canList.test(source)) return help(source);
        send.accept(source, RemoteScreens.picker(viewer.apply(source), RemoteCatalogQuery.servers(store.get().snapshot()),
                page, defaultLimit.getAsInt()));
        return Command.SINGLE_SUCCESS;
    }
    private int teleport(CommandContext<S> context) {
        S source = context.getSource();
        String id = getString(context, SERVER), dimension = getString(context, DIMENSION),
                listName = getString(context, LIST), name = getString(context, WAYPOINT);
        Viewer reader = viewer.apply(source);
        if (!canTeleport.test(source)) return fail(source, reader, null, id, dimension, listName, name, Result.UNAUTHORIZED);
        var cached = store.get().snapshot();
        var entry = cached.entrySet().stream().filter(value -> value.getKey().value().equals(id)).findFirst().orElse(null);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(cached, id).orElse(null);
        if (entry == null || server == null) return fail(source, reader, null, id, dimension, listName, name, Result.UNAVAILABLE);
        var view = entry.getValue();
        if (view.state() == RemoteCatalogState.UNAUTHORIZED) return fail(source, reader, server, id, dimension, listName, name, Result.UNAUTHORIZED);
        if (view.state() == RemoteCatalogState.STALE) return fail(source, reader, server, id, dimension, listName, name, Result.STALE_CATALOG);
        if (view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) {
            return fail(source, reader, server, id, dimension, listName, name, Result.UNAVAILABLE);
        }
        var list = view.snapshot().dimensions().getOrDefault(dimension, Map.of()).get(listName);
        WaypointList shown = server.list(dimension, listName);
        SimpleWaypoint target = shown == null ? null : shown.getWaypointByName(name);
        if (list == null || !list.waypoints().containsKey(name) || target == null) {
            return fail(source, reader, server, id, dimension, listName, name, Result.NOT_FOUND);
        }
        var selection = new RemoteTeleportInitiator.Selection(new RemoteWaypointKey(entry.getKey(), dimension, listName, name),
                view.snapshot().catalogRevision(), list.listRevision());
        send.accept(source, RemoteScreens.switching(reader, server, dimension, shown, target));
        teleport.initiate(source, selection, result -> {
            if (result.permissions() != null) {
                Component failedServer = result.permissionServer().equals(server.id())
                        ? RemoteRefs.serverName(reader, server) : Component.text(result.permissionServer().value());
                error.accept(source, RemoteScreens.permissionCheckFailed(failedServer, result.permissions()));
            } else if (result.result() != Result.SUCCESS) {
                fail(source, reader, server, id, dimension, listName, name, result.result());
            }
        });
        return Command.SINGLE_SUCCESS;
    }
    /** ✘ why the switch failed; the destination's arrival line reports success. */
    private int fail(S source, Viewer reader, @Nullable RemoteCatalogQuery.Server server, String id, String dimension,
                     String list, String waypoint, Result result) {
        error.accept(source, RemoteScreens.teleportFailed(reader, server, id, dimension, list, waypoint, result));
        return 0;
    }
    private void configure(ArgumentBuilder<S, ?> node, int depth) {
        new ListCommandOptions<S>((mode, reversed, view) -> context -> execute(context, depth, mode, reversed, view), null)
                .configure(node);
    }
    private CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, int depth) {
        return suggest(context, builder, depth, false);
    }
    private CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, int depth, boolean tp) {
        // Brigadier supplies the outer context for redirected commands such as /execute ... run wp.
        context = context.getLastChild();
        if (!(tp ? canTeleport : canList).test(context.getSource())) return Suggestions.empty();
        Map<RemoteServerId, CatalogReceiver.View> cached = store.get().snapshot();
        Collection<String> candidates = List.of();
        if (depth == 0) candidates = cached.entrySet().stream().filter(entry -> entry.getValue().state() != RemoteCatalogState.UNAUTHORIZED)
                .map(entry -> entry.getKey().value()).toList();
        else {
            String id = getString(context, SERVER);
            CatalogReceiver.View view = cached.entrySet().stream().filter(entry -> entry.getKey().value().equals(id)).map(Map.Entry::getValue).findFirst().orElse(null);
            if (view != null && view.snapshot() != null && view.state() != RemoteCatalogState.UNAUTHORIZED && view.state() != RemoteCatalogState.UNAVAILABLE) {
                if (depth == 1) candidates = view.snapshot().dimensions().keySet();
                else {
                    var lists = view.snapshot().dimensions().getOrDefault(getString(context, DIMENSION), Map.of());
                    if (depth == 2) candidates = lists.keySet();
                    else {
                        var selected = lists.get(getString(context, LIST));
                        if (selected != null) candidates = selected.waypoints().keySet();
                    }
                }
            }
        }
        String remaining = builder.getRemaining();
        String prefix;
        try {
            prefix = new com.mojang.brigadier.StringReader(remaining).readString();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException incomplete) {
            prefix = remaining.startsWith("\"") ? remaining.substring(1).replace("\\\"", "\"").replace("\\\\", "\\") : remaining;
        }
        final String match = prefix;
        candidates.stream().sorted().filter(value -> value.startsWith(match)).map(StringCommandBuilder::escapeListName)
                .filter(value -> value.length() <= 256).limit(100).forEach(builder::suggest);
        return builder.buildFuture();
    }
    /** /wp remote list [<server> [<dimension> [<list>]]] with the list options. */
    private int execute(CommandContext<S> context, int depth, WaypointSorting.SortMode mode, boolean reversed, ListView view) {
        S source = context.getSource();
        if (!canList.test(source)) return 0;
        if (mode == WaypointSorting.SortMode.DISTANCE) {
            error.accept(source, RemoteScreens.distanceUnavailable());
            return 0;
        }
        Viewer reader = viewer.apply(source);
        ListQuery query = new ListQuery(optionalString(context, SEARCH_QUERY_ARG), mode, reversed, view,
                optionalInt(context, PAGE_NUMBER_ARG, 1), optionalLimit(context));
        int pageLimit = query.pageLimit(defaultLimit.getAsInt());
        var catalogs = store.get().snapshot();
        if (depth == 0) {
            List<RemoteCatalogQuery.Server> servers = RemoteCatalogQuery.servers(catalogs);
            send.accept(source, query.searching() ? RemoteScreens.search(reader, null, servers, query, pageLimit)
                    : RemoteScreens.allServers(reader, servers, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String id = getString(context, SERVER);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(catalogs, id).orElse(null);
        if (server == null) {
            error.accept(source, RemoteScreens.noServer(reader, id));
            return 0;
        }
        if (depth == 1 || !server.readable()) {
            send.accept(source, query.searching() && server.readable()
                    ? RemoteScreens.search(reader, server, List.of(server), query, pageLimit)
                    : RemoteScreens.server(reader, server, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String dimension = getString(context, DIMENSION);
        if (server.lists(dimension) == null) {
            error.accept(source, RemoteScreens.noDimension(reader, server, dimension));
            return 0;
        }
        if (depth == 2) {
            send.accept(source, RemoteScreens.dimension(reader, server, dimension, query, pageLimit));
            return Command.SINGLE_SUCCESS;
        }
        String listName = getString(context, LIST);
        WaypointList list = server.list(dimension, listName);
        if (list == null) {
            error.accept(source, RemoteScreens.noList(reader, server, dimension, listName));
            return 0;
        }
        send.accept(source, RemoteScreens.list(reader, server, dimension, list, query, pageLimit));
        return Command.SINGLE_SUCCESS;
    }
    /** /wp remote details <server> <dimension> <list> <waypoint>: read-only details. */
    private int details(CommandContext<S> context) {
        S source = context.getSource();
        if (!canList.test(source)) return 0;
        Viewer reader = viewer.apply(source);
        String id = getString(context, SERVER), dimension = getString(context, DIMENSION),
                listName = getString(context, LIST), name = getString(context, WAYPOINT);
        RemoteCatalogQuery.Server server = RemoteCatalogQuery.server(store.get().snapshot(), id).orElse(null);
        if (server == null) {
            error.accept(source, RemoteScreens.noServer(reader, id));
            return 0;
        }
        if (!server.readable()) {
            send.accept(source, RemoteScreens.server(reader, server, ListQuery.DEFAULT, defaultLimit.getAsInt()));
            return 0;
        }
        if (server.lists(dimension) == null) {
            error.accept(source, RemoteScreens.noDimension(reader, server, dimension));
            return 0;
        }
        WaypointList list = server.list(dimension, listName);
        if (list == null) {
            error.accept(source, RemoteScreens.noList(reader, server, dimension, listName));
            return 0;
        }
        SimpleWaypoint waypoint = list.getWaypointByName(name);
        if (waypoint == null) {
            error.accept(source, RemoteScreens.noWaypoint(reader, server, dimension, list, name));
            return 0;
        }
        send.accept(source, RemoteScreens.details(reader, server, dimension, list, waypoint));
        return Command.SINGLE_SUCCESS;
    }
    private static <S> int optionalInt(CommandContext<S> context, String name, int fallback) {
        try { return getInteger(context, name); } catch (IllegalArgumentException missing) { return fallback; }
    }
    private static <S> @Nullable Integer optionalLimit(CommandContext<S> context) {
        try { return getInteger(context, PAGE_LIMIT_ARG); } catch (IllegalArgumentException missing) { return null; }
    }
    private static <S> String optionalString(CommandContext<S> context, String name) {
        try { return getString(context, name); } catch (IllegalArgumentException missing) { return ""; }
    }
}
