package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
import _959.server_waypoint.crossserver.handoff.RemoteTeleportInitiator;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.util.StringCommandBuilder;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import static _959.server_waypoint.text.TextButtonBuilder.*;
import static _959.server_waypoint.text.WaypointTextHelper.getDimensionColor;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import static _959.server_waypoint.command.CoreWaypointCommand.*;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.*;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
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
    private final RemoteCatalogQuery query = new RemoteCatalogQuery();

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList,
                          Predicate<S> canTeleport, RemoteTeleportInitiator<S> teleport) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.canTeleport = Objects.requireNonNull(canTeleport, "canTeleport");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.store = store; this.send = send; this.error = error; this.defaultLimit = defaultLimit;
    }
    LiteralArgumentBuilder<S> build() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("remote").requires(this::canUse).executes(context -> help(context.getSource()));
        LiteralArgumentBuilder<S> servers = LiteralArgumentBuilder.<S>literal("servers").requires(canList).executes(this::servers);
        RequiredArgumentBuilder<S, Integer> page = RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1)).executes(this::servers);
        page.then(LiteralArgumentBuilder.<S>literal("limit").then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT)).executes(this::servers)));
        servers.then(LiteralArgumentBuilder.<S>literal("page").then(page)); root.then(servers);
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
        detailsList.suggests((context, builder) -> suggest(context, builder, 2)).executes(context -> details(context, false));
        detailsWaypoint.suggests((context, builder) -> suggest(context, builder, 3)).executes(context -> details(context, true));
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
    boolean canUse(S source) { return canList.test(source) || canTeleport.test(source); }
    int help(S source) {
        if (!canUse(source)) return 0;
        send.accept(source, WaypointCommandHelp.remoteMenu(canList.test(source), canTeleport.test(source)));
        return Command.SINGLE_SUCCESS;
    }
    private int teleport(CommandContext<S> context) {
        S source = context.getSource();
        if (!canTeleport.test(source)) return fail(source, Result.UNAUTHORIZED);
        var cached = store.get().snapshot();
        var entry = cached.entrySet().stream().filter(value -> value.getKey().value().equals(getString(context, SERVER))).findFirst().orElse(null);
        if (entry == null) return fail(source, Result.UNAVAILABLE);
        var view = entry.getValue();
        if (view.state() == RemoteCatalogState.UNAUTHORIZED) return fail(source, Result.UNAUTHORIZED);
        if (view.state() == RemoteCatalogState.STALE) return fail(source, Result.STALE_CATALOG);
        if (view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) return fail(source, Result.UNAVAILABLE);
        String dimension = getString(context, DIMENSION), listName = getString(context, LIST), waypoint = getString(context, WAYPOINT);
        var list = view.snapshot().dimensions().getOrDefault(dimension, Map.of()).get(listName);
        if (list == null || !list.waypoints().containsKey(waypoint)) return fail(source, Result.NOT_FOUND);
        var selection = new RemoteTeleportInitiator.Selection(new RemoteWaypointKey(entry.getKey(), dimension, listName, waypoint),
                view.snapshot().catalogRevision(), list.listRevision());
        send.accept(source, translatable("waypoint.remote.tp.preparing"));
        teleport.initiate(source, selection, result -> {
            if (result == Result.SUCCESS) send.accept(source, translatable("waypoint.remote.tp.success"));
            else fail(source, result);
        });
        return Command.SINGLE_SUCCESS;
    }
    private int fail(S source, Result result) {
        error.accept(source, translatable("waypoint.remote.tp." + result.name().toLowerCase(Locale.ROOT)));
        return 0;
    }
    private void configure(ArgumentBuilder<S, ?> node, int depth) {
        new ListCommandOptions<S>((mode, reversed, grouped) -> context -> execute(context, depth, mode, reversed, grouped), null).configure(node);
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
    private int execute(CommandContext<S> context, int depth, WaypointSorting.SortMode mode, boolean reversed, boolean grouped) {
        if (!canList.test(context.getSource())) return 0;
        var scope = new RemoteCatalogQuery.Scope(depth > 0 ? getString(context, SERVER) : null,
                depth > 1 ? getString(context, DIMENSION) : null, depth > 2 ? getString(context, LIST) : null);
        if (!grouped && mode == WaypointSorting.SortMode.DEFAULT) mode = WaypointSorting.SortMode.NAME;
        ListOptions options = new ListOptions(optionalString(context, SEARCH_QUERY_ARG), mode, reversed,
                optionalInt(context, PAGE_NUMBER_ARG, 1), optionalInt(context, PAGE_LIMIT_ARG, defaultLimit.getAsInt()), grouped);
        var cached = store.get().snapshot();
        var result = query.query(cached, scope, options);
        S source = context.getSource();
        if (result.problem() != RemoteCatalogQuery.Problem.NONE) {
            error.accept(source, translatable("waypoint.remote." + result.problem().name().toLowerCase(Locale.ROOT))); return 0;
        }
        if (options.pageNumber() > result.totalPages()) {
            error.accept(source, translatable("waypoint.list.page.invalid", text(options.pageNumber()), text(result.totalPages()))); return 0;
        }
        Component output = Component.empty().append(translatable("waypoint.remote.title", NamedTextColor.GOLD));
        if (result.rows().isEmpty()) output = output.appendNewline().append(translatable(cached.isEmpty() ? "waypoint.remote.no_servers" : "waypoint.remote.no_results"));
        String lastServer = null, lastDimension = null, lastList = null;
        for (RemoteCatalogQuery.Row row : result.rows()) {
            if (grouped) {
                if (!row.server().value().equals(lastServer)) {
                    output = output.appendNewline().append(scopeLink(serverLabel(row.server(), row.serverLabel(), row.state()),
                            row.server().value(), null, null, options, translatable("waypoint.remote.title")));
                    lastServer = row.server().value(); lastDimension = null; lastList = null;
                }
                if (row.dimension() != null && !row.dimension().equals(lastDimension)) {
                    output = output.appendNewline().append(text("  ")).append(scopeLink(
                            safe(row.dimension()).color(getDimensionColor(row.dimension())), row.server().value(), row.dimension(), null,
                            options, translatable("button.list.dimension", safe(row.dimension()).color(getDimensionColor(row.dimension())))));
                    lastDimension = row.dimension(); lastList = null;
                }
                if (row.list() != null && !row.list().equals(lastList)) {
                    output = output.appendNewline().append(text("    ")).append(detailsButton(row, false)).appendSpace().append(scopeLink(
                            label(row.listLabel(), row.list()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD),
                            row.server().value(), row.dimension(), row.list(), options,
                            translatable("button.list.waypoint_list", label(row.listLabel(), row.list()))));
                    lastList = row.list();
                }
            } else {
                output = output.appendNewline().append(serverLabel(row.server(), row.serverLabel(), row.state()));
                if (row.dimension() != null) output = output.append(text(" / ", NamedTextColor.DARK_GRAY))
                        .append(safe(row.dimension()).color(getDimensionColor(row.dimension())));
                if (row.list() != null) output = output.append(text(" / ", NamedTextColor.DARK_GRAY))
                        .append(label(row.listLabel(), row.list()).colorIfAbsent(NamedTextColor.GRAY));
            }
            if (row.waypoint() != null) {
                output = grouped ? output.appendNewline().append(text("      "))
                        : output.append(text(" / ", NamedTextColor.DARK_GRAY));
                output = output.append(waypointText(row, canTeleport.test(source)));
            } else if (row.state() == RemoteCatalogState.AVAILABLE || row.state() == RemoteCatalogState.STALE) {
                output = output.appendNewline().append(text("      ")).append(translatable("waypoint.remote.empty", NamedTextColor.GRAY).decorate(TextDecoration.ITALIC));
            }
        }
        output = output.appendNewline().append(listControls(scope, options));
        if (result.totalPages() > 1) {
            output = output.append(getPageNavigation(options, result.totalPages(), result.totalRows(),
                    page -> StringCommandBuilder.remoteListPageCmd(scope.server(), scope.dimension(), scope.list(), options, page)));
        }
        send.accept(source, output); return Command.SINGLE_SUCCESS;
    }
    private int details(CommandContext<S> context, boolean withWaypoint) {
        S source = context.getSource();
        if (!canList.test(source)) return 0;
        String server = getString(context, SERVER), dimension = getString(context, DIMENSION), listName = getString(context, LIST);
        var view = store.get().snapshot().entrySet().stream()
                .filter(entry -> entry.getKey().value().equals(server)).map(Map.Entry::getValue).findFirst().orElse(null);
        if (view == null) {
            error.accept(source, translatable("waypoint.remote.unknown_server"));
            return 0;
        }
        Component heading = serverLabel(new RemoteServerId(server), view.displayName(), view.state());
        if (view.snapshot() == null || view.state() == RemoteCatalogState.UNAUTHORIZED || view.state() == RemoteCatalogState.UNAVAILABLE) {
            send.accept(source, heading);
            return 0;
        }
        var lists = view.snapshot().dimensions().get(dimension);
        if (lists == null || !lists.containsKey(listName)) {
            error.accept(source, translatable(lists == null ? "waypoint.remote.unknown_dimension" : "waypoint.remote.unknown_list"));
            return 0;
        }
        var list = lists.get(listName);
        String name = withWaypoint ? getString(context, WAYPOINT) : listName;
        var waypoint = withWaypoint ? list.waypoints().get(name) : null;
        if (withWaypoint && waypoint == null) {
            error.accept(source, translatable("waypoint.remote.tp.not_found"));
            return 0;
        }
        Component output = Component.empty().append(translatable(withWaypoint
                ? "waypoint.details.waypoint.title" : "waypoint.details.list.title", NamedTextColor.GOLD))
                .appendNewline().append(heading).appendNewline()
                .append(property("identifier", safe(name)))
                .append(property("display_name", safe(withWaypoint ? waypoint.displayName() : list.displayName())))
                .append(property("dimension", safe(dimension).color(getDimensionColor(dimension))));
        if (withWaypoint) {
            output = output.append(property("source_list_display_name", safe(list.displayName())))
                    .append(property("source_list_identifier", safe(listName)))
                    .append(property("initials", safe(waypoint.initials())))
                    .append(property("position", text(waypoint.position().toShortString())))
                    .append(property("color", Component.empty().append(text("■", TextColor.color(waypoint.rgb())))
                            .appendSpace().append(text(String.format(Locale.ROOT, "#%06X", waypoint.rgb()), NamedTextColor.WHITE))))
                    .append(property("yaw", text(waypoint.yaw())))
                    .append(property("visibility", translatable(waypoint.global() ? "waypoint.global" : "waypoint.local")))
                    .append(property("keywords", text(String.join(", ", waypoint.keywords()))))
                    .append(property("description", text(waypoint.description())));
            var row = new RemoteCatalogQuery.Row(new RemoteServerId(server), view.displayName(), view.state(),
                    dimension, listName, list.displayName(), name, waypoint);
            if (canTeleport.test(source) && view.state() == RemoteCatalogState.AVAILABLE) {
                String command = remoteTargetCommand("tp", row, true);
                if (command.length() <= 256) output = output.append(runCommandButton(translatable("button.teleport"),
                        NamedTextColor.LIGHT_PURPLE, command, translatable("button.teleport"))).appendSpace();
            }
        } else {
            output = output.append(property("waypoint_count", text(list.waypoints().size())));
        }
        String back = StringCommandBuilder.remoteListPageCmd(server, dimension, listName,
                new ListOptions("", WaypointSorting.SortMode.DEFAULT, false, 1, defaultLimit.getAsInt()), 1);
        if (back.length() <= 256) output = output.append(runCommandButton(translatable("button.open_list"),
                NamedTextColor.AQUA, back, translatable("button.open_list")));
        send.accept(source, output);
        return Command.SINGLE_SUCCESS;
    }

    private static Component property(String key, Component value) {
        return Component.empty().append(translatable("waypoint.details." + key, NamedTextColor.GRAY))
                .append(text(": ", NamedTextColor.GRAY)).append(value.colorIfAbsent(NamedTextColor.WHITE)).appendNewline();
    }

    private static String remoteTargetCommand(String action, RemoteCatalogQuery.Row row, boolean withWaypoint) {
        String command = "/wp remote " + action + " " + StringCommandBuilder.escapeListName(row.server().value())
                + " " + StringCommandBuilder.escapeListName(row.dimension())
                + " " + StringCommandBuilder.escapeListName(row.list());
        return withWaypoint ? command + " " + StringCommandBuilder.escapeListName(row.waypointName()) : command;
    }

    private static Component detailsButton(RemoteCatalogQuery.Row row, boolean withWaypoint) {
        String command = remoteTargetCommand("details", row, withWaypoint);
        return command.length() <= 256 ? showMoreButton(command)
                : text("[⋯]", NamedTextColor.DARK_GRAY);
    }

    private static Component scopeLink(Component label, String server, String dimension, String list,
                                       ListOptions options, Component hover) {
        String command = StringCommandBuilder.remoteListPageCmd(server, dimension, list, options, 1);
        return command.length() <= 256 ? label.clickEvent(ClickEvent.runCommand(command)).hoverEvent(hover) : label;
    }

    private static Component listControls(RemoteCatalogQuery.Scope scope, ListOptions options) {
        Function<ListOptions, String> command = value -> StringCommandBuilder.remoteListPageCmd(
                scope.server(), scope.dimension(), scope.list(), value, value.pageNumber());
        ListOptions view = new ListOptions(options.filterText(), options.sortMode(), options.reversed(),
                options.pageNumber(), options.pageLimit(), !options.groupByLists());
        ListOptions search = new ListOptions("", options.sortMode(), options.reversed(), 1,
                options.pageLimit(), options.groupByLists());
        return Component.empty().append(getListViewToggleButton(options, command.apply(view)))
                .appendSpace().append(getListSearchButton(command.apply(search) + " search "))
                .appendSpace().append(getListSortControls(options,
                        mode -> command.apply(new ListOptions(options.filterText(), mode, false, 1,
                                options.pageLimit(), options.groupByLists())),
                        reversed -> command.apply(new ListOptions(options.filterText(), options.sortMode(), reversed, 1,
                                options.pageLimit(), options.groupByLists())),
                        mode -> mode != WaypointSorting.SortMode.DISTANCE));
    }

    private static Component waypointText(RemoteCatalogQuery.Row row, boolean canTeleport) {
        RemoteWaypointSnapshot waypoint = row.waypoint();
        Component hover = Component.empty();
        if (!waypoint.description().isEmpty()) hover = hover.append(safe(waypoint.description())).appendNewline();
        hover = hover.append(text(waypoint.position().toShortString()));
        if (_959.server_waypoint.util.VanillaDimensionNames.MINECRAFT_OVERWORLD.equals(row.dimension())) {
            hover = hover.appendNewline().append(text(_959.server_waypoint.util.BlockPosConverter
                    .overWorldToNether(waypoint.position()).toShortString(), NamedTextColor.RED));
        } else if (_959.server_waypoint.util.VanillaDimensionNames.MINECRAFT_THE_NETHER.equals(row.dimension())) {
            hover = hover.appendNewline().append(text(_959.server_waypoint.util.BlockPosConverter
                    .netherToOverWorld(waypoint.position()).toShortString(), NamedTextColor.GREEN));
        }
        Component initials = text("[" + waypoint.initials() + "]", TextColor.color(waypoint.rgb()))
                .decorate(TextDecoration.BOLD);
        String command = remoteTargetCommand("tp", row, true);
        if (canTeleport && row.state() == RemoteCatalogState.AVAILABLE && command.length() <= 256) {
            initials = initials.clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(translatable("button.initials.tp"));
        }
        return Component.empty().append(detailsButton(row, true)).appendSpace().append(initials).appendSpace()
                .append(label(waypoint.displayName(), row.waypointName()).color(NamedTextColor.WHITE)
                        .decoration(TextDecoration.BOLD, false).hoverEvent(HoverEvent.showText(hover)));
    }

    private int servers(CommandContext<S> context) {
        if (!canList.test(context.getSource())) return 0;
        var entries = store.get().snapshot().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value))).toList();
        int page = optionalInt(context, PAGE_NUMBER_ARG, 1), limit = optionalInt(context, PAGE_LIMIT_ARG, defaultLimit.getAsInt());
        int pages = Math.max(1, (entries.size() + limit - 1) / limit);
        if (page > pages) { error.accept(context.getSource(), translatable("waypoint.list.page.invalid", text(page), text(pages))); return 0; }
        Component output = Component.empty().append(translatable("waypoint.remote.servers", NamedTextColor.GOLD));
        if (entries.isEmpty()) output = output.appendNewline().append(translatable("waypoint.remote.no_servers"));
        for (var entry : entries.subList((page - 1) * limit, Math.min(entries.size(), page * limit))) {
            output = output.appendNewline().append(serverLabel(entry.getKey(), entry.getValue().displayName(), entry.getValue().state()));
            if (entry.getValue().state() != RemoteCatalogState.UNAUTHORIZED) output = output.appendSpace().append(button("→",
                    "/wp remote list " + StringCommandBuilder.escapeListName(entry.getKey().value())));
        }
        output = output.appendNewline().append(translatable("waypoint.remote.page", text(page), text(pages)));
        if (page > 1) output = output.appendSpace().append(button("←", "/wp remote servers page " + (page - 1) + " limit " + limit));
        if (page < pages) output = output.appendSpace().append(button("→", "/wp remote servers page " + (page + 1) + " limit " + limit));
        send.accept(context.getSource(), output); return Command.SINGLE_SUCCESS;
    }
    private static Component serverLabel(RemoteServerId id, String display, RemoteCatalogState state) {
        return Component.empty().append(label(display, id.value())).appendSpace()
                .append(translatable("waypoint.remote.state." + state.name().toLowerCase(Locale.ROOT),
                        state == RemoteCatalogState.AVAILABLE ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
    }
    private static Component label(String display, String identity) {
        Component result = safe(display);
        return display.equals(identity) ? result : Component.empty().append(result).append(text(" [")).append(safe(identity)).append(text("]"));
    }
    private static Component safe(String value) { return text(value.length() > 256 ? value.substring(0, 256) + "…" : value); }
    private static Component button(String label, String command) {
        Component result = text(label, NamedTextColor.YELLOW);
        return command.length() <= 256 ? result.clickEvent(ClickEvent.runCommand(command)) : result;
    }
    private static <S> int optionalInt(CommandContext<S> context, String name, int fallback) {
        try { return getInteger(context, name); } catch (IllegalArgumentException missing) { return fallback; }
    }
    private static <S> String optionalString(CommandContext<S> context, String name) {
        try { return getString(context, name); } catch (IllegalArgumentException missing) { return ""; }
    }
}
