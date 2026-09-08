package _959.server_waypoint.command;

import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.*;
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

/** Vanilla-safe read-only commands. The only data dependency is a bounded local replica facade. */
final class RemoteWaypointCommand<S> {
    private static final String SERVER = "remote server", DIMENSION = "remote dimension", LIST = "remote list";
    private final Supplier<RemoteCatalogStore> store;
    private final BiConsumer<S, Component> send, error;
    private final IntSupplier defaultLimit;
    private final Predicate<S> canList;
    private final RemoteCatalogQuery query = new RemoteCatalogQuery();

    RemoteWaypointCommand(Supplier<RemoteCatalogStore> store, BiConsumer<S, Component> send,
                          BiConsumer<S, Component> error, IntSupplier defaultLimit, Predicate<S> canList) {
        this.canList = Objects.requireNonNull(canList, "canList");
        this.store = store; this.send = send; this.error = error; this.defaultLimit = defaultLimit;
    }
    LiteralArgumentBuilder<S> build() {
        LiteralArgumentBuilder<S> root = LiteralArgumentBuilder.<S>literal("remote").requires(canList).executes(context -> help(context.getSource()));
        LiteralArgumentBuilder<S> servers = LiteralArgumentBuilder.<S>literal("servers").executes(this::servers);
        RequiredArgumentBuilder<S, Integer> page = RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1)).executes(this::servers);
        page.then(LiteralArgumentBuilder.<S>literal("limit").then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT)).executes(this::servers)));
        servers.then(LiteralArgumentBuilder.<S>literal("page").then(page)); root.then(servers);
        LiteralArgumentBuilder<S> lists = literal("list"); configure(lists, 0);
        RequiredArgumentBuilder<S, String> server = argument(SERVER, string()); configure(server, 1);
        server.suggests((context, builder) -> suggest(context, builder, 0));
        RequiredArgumentBuilder<S, String> dimension = argument(DIMENSION, string()); configure(dimension, 2);
        dimension.suggests((context, builder) -> suggest(context, builder, 1));
        RequiredArgumentBuilder<S, String> list = argument(LIST, string()); configure(list, 3);
        list.suggests((context, builder) -> suggest(context, builder, 2));
        return root.then(lists.then(server.then(dimension.then(list))));
    }
    boolean canList(S source) { return canList.test(source); }
    int help(S source) { if (!canList.test(source)) return 0; send.accept(source, WaypointCommandHelp.remoteHelp()); return Command.SINGLE_SUCCESS; }
    private void configure(ArgumentBuilder<S, ?> node, int depth) {
        new ListCommandOptions<S>((mode, reversed, grouped) -> context -> execute(context, depth, mode, reversed, grouped), null).configure(node);
    }
    private CompletableFuture<Suggestions> suggest(CommandContext<S> context, SuggestionsBuilder builder, int depth) {
        if (!canList.test(context.getSource())) return Suggestions.empty();
        Map<RemoteServerId, CatalogReceiver.View> cached = store.get().snapshot();
        Collection<String> candidates = List.of();
        if (depth == 0) candidates = cached.entrySet().stream().filter(entry -> entry.getValue().state() != RemoteCatalogState.UNAUTHORIZED)
                .map(entry -> entry.getKey().value()).toList();
        else {
            String id = getString(context, SERVER);
            CatalogReceiver.View view = cached.entrySet().stream().filter(entry -> entry.getKey().value().equals(id)).map(Map.Entry::getValue).findFirst().orElse(null);
            if (view != null && view.snapshot() != null && view.state() != RemoteCatalogState.UNAUTHORIZED && view.state() != RemoteCatalogState.UNAVAILABLE) {
                if (depth == 1) candidates = view.snapshot().dimensions().keySet();
                else candidates = view.snapshot().dimensions().getOrDefault(getString(context, DIMENSION), Map.of()).keySet();
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
        Component output = text().append(translatable("waypoint.remote.title", NamedTextColor.GOLD)).build();
        if (result.rows().isEmpty()) output = output.appendNewline().append(translatable(cached.isEmpty() ? "waypoint.remote.no_servers" : "waypoint.remote.no_results"));
        String lastServer = null, lastDimension = null, lastList = null;
        for (RemoteCatalogQuery.Row row : result.rows()) {
            if (grouped) {
                if (!row.server().value().equals(lastServer)) {
                    output = output.appendNewline().append(serverLabel(row.server(), row.serverLabel(), row.state()));
                    lastServer = row.server().value(); lastDimension = null; lastList = null;
                }
                if (row.dimension() != null && !row.dimension().equals(lastDimension)) {
                    output = output.appendNewline().append(text("  ")).append(safe(row.dimension()));
                    lastDimension = row.dimension(); lastList = null;
                }
                if (row.list() != null && !row.list().equals(lastList)) {
                    output = output.appendNewline().append(text("    ")).append(label(row.listLabel(), row.list())); lastList = row.list();
                }
            } else {
                output = output.appendNewline().append(serverLabel(row.server(), row.serverLabel(), row.state()));
                if (row.dimension() != null) output = output.append(text(" / ")).append(safe(row.dimension()));
                if (row.list() != null) output = output.append(text(" / ")).append(label(row.listLabel(), row.list()));
            }
            if (row.waypoint() != null) {
                var waypoint = row.waypoint();
                output = output.appendNewline().append(text(grouped ? "      " : "  "))
                        .append(label(waypoint.displayName(), row.waypointName()).color(TextColor.color(waypoint.rgb())))
                        .append(text(" (" + waypoint.position().x() + ", " + waypoint.position().y() + ", " + waypoint.position().z() + ")", NamedTextColor.GRAY));
            } else if (row.state() == RemoteCatalogState.AVAILABLE || row.state() == RemoteCatalogState.STALE) {
                output = output.appendNewline().append(text("      ")).append(translatable("waypoint.remote.empty"));
            }
        }
        output = output.appendNewline().append(translatable("waypoint.remote.page", text(options.pageNumber()), text(result.totalPages())));
        if (options.pageNumber() > 1) output = output.appendSpace().append(button("←", StringCommandBuilder.remoteListPageCmd(scope.server(), scope.dimension(), scope.list(), options, options.pageNumber() - 1)));
        if (options.pageNumber() < result.totalPages()) output = output.appendSpace().append(button("→", StringCommandBuilder.remoteListPageCmd(scope.server(), scope.dimension(), scope.list(), options, options.pageNumber() + 1)));
        send.accept(source, output); return Command.SINGLE_SUCCESS;
    }
    private int servers(CommandContext<S> context) {
        if (!canList.test(context.getSource())) return 0;
        var entries = store.get().snapshot().entrySet().stream().sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value))).toList();
        int page = optionalInt(context, PAGE_NUMBER_ARG, 1), limit = optionalInt(context, PAGE_LIMIT_ARG, defaultLimit.getAsInt());
        int pages = Math.max(1, (entries.size() + limit - 1) / limit);
        if (page > pages) { error.accept(context.getSource(), translatable("waypoint.list.page.invalid", text(page), text(pages))); return 0; }
        Component output = text().append(translatable("waypoint.remote.servers", NamedTextColor.GOLD)).build();
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
        return text().append(label(display, id.value())).appendSpace()
                .append(translatable("waypoint.remote.state." + state.name().toLowerCase(Locale.ROOT),
                        state == RemoteCatalogState.AVAILABLE ? NamedTextColor.GREEN : NamedTextColor.YELLOW)).build();
    }
    private static Component label(String display, String identity) {
        Component result = safe(display);
        return display.equals(identity) ? result : text().append(result).append(text(" [")).append(safe(identity)).append(text("]")).build();
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
