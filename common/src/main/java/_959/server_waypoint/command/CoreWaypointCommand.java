package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.text.TextButton;
import _959.server_waypoint.util.TriConsumer;
import _959.server_waypoint.util.WaypointInitials;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.waypoint.WaypointList.SERVER_N;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.ADD_LIST;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.REMOVE_LIST;
import static _959.server_waypoint.text.TextButton.*;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.ColorUtils.*;
import static _959.server_waypoint.util.CommandGenerator.escapeListName;
import static _959.server_waypoint.util.CommandGenerator.listPageCmd;
import static _959.server_waypoint.util.WaypointInitials.SINGLE_WORD_REGEX;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;
import static net.kyori.adventure.text.Component.*;
import static net.kyori.adventure.text.Component.translatable;

public abstract class CoreWaypointCommand<S, K, P, D, B> {
    protected final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;
    private final WaypointQueryEngine waypointQueryEngine;
    private final PermissionKeys<K> permissionKeys;
    private final PermissionManager<S, K, P> permissionManager;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final Supplier<ArgumentType<B>> blockPosArgumentProvider;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> NEW_NAME_INITIALS_SUGGESTION = new NewNameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();
    private final SuggestionProvider<S> HEX_COLOR_CODE_SUGGESTION = new HexColorCodeSuggestion();
    public static final String WAYPOINT_COMMAND = "wp";
    public static final String ADD_COMMAND = "add";
    public static final String EDIT_COMMAND = "edit";
    public static final String REMOVE_COMMAND = "remove";
    public static final String LIST_COMMAND = "list";
    public static final String DOWNLOAD_COMMAND = "download";
    public static final String TP_COMMAND = "tp";
    public static final String RELOAD_COMMAND = "reload";
    public static final String SEARCH_COMMAND = "search";
    public static final String SORT_COMMAND = "sort";
    public static final String ORDER_COMMAND = "order";
    public static final String PAGE_COMMAND = "page";
    public static final String LIMIT_COMMAND = "limit";
    public static final String DIMENSION_ARG = "dimension";
    public static final String LIST_NAME_ARG = "list name";
    public static final String WAYPOINT_NAME_ARG = "waypoint name";
    public static final String NEW_WAYPOINT_NAME_ARG = "new waypoint name";
    public static final String INITIALS_ARG = "initials";
    public static final String POS_ARG = "position";
    public static final String YAW_ARG = "yaw";
    public static final String COLOR_ARG = "color";
    public static final String VISIBILITY_ARG = "global";
    public static final String SEARCH_QUERY_ARG = "search query";
    public static final String PAGE_NUMBER_ARG = "page number";
    public static final String PAGE_LIMIT_ARG = "page limit";
    public static final String RANDOM_COLOR = "random";
    public static final int MAX_PAGE_LIMIT = Config.MAX_PAGE_LIMIT;

    public CoreWaypointCommand(WaypointServerCore waypointServer, PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissionManager, Supplier<ArgumentType<D>> dimensionArgument, Supplier<ArgumentType<B>> blockPositionArgument) {
        this.waypointServer = waypointServer;
        this.waypointQueryEngine = new WaypointQueryEngine(waypointServer);
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.dimensionArgumentProvider = dimensionArgument;
        this.blockPosArgumentProvider = blockPositionArgument;
        this.permissionKeys = permissionManager.keys;
    }

    protected abstract String toDimensionName(D dimensionArgument);
    protected abstract WaypointPos toWaypointPos(S source, B blockPositionArgument);
    protected abstract boolean isDimensionValid(S source, D dimensionArgument);
    protected abstract void executeByServer(S source, Runnable task);
    protected abstract D getSourceDimension(S source);
    protected abstract WaypointPos getSourcePosition(S source);
    protected abstract float getSourceYaw(S source);
    protected abstract P getPlayer(S source);
    protected abstract String getPlayerName(P player);
    protected abstract void teleportPlayer(S source, P player, D dimensionArgument, WaypointPos pos, int yaw);
    protected abstract Message getMessageFromComponent(Component component);

    private boolean hasAddPermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.add(), CONFIG.CommandPermission().add());
    }

    private boolean hasEditPermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.edit(), CONFIG.CommandPermission().edit());
    }

    private boolean hasRemovePermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.remove(), CONFIG.CommandPermission().remove());
    }

    private boolean hasTpPermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.tp(), CONFIG.CommandPermission().tp());
    }

    private boolean hasReloadPermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.reload(), CONFIG.CommandPermission().reload());
    }

    @SuppressWarnings("unchecked")
    private <T> T getArgument(CommandContext<S> context, String name) {
        return context.getArgument(name, (Class<T>) Object.class);
    }

    private CommandNode<S> selectorArguments(Command<S> command) {
        return dimensionNode()
                .then(listNameNode()
                        .then(waypointNameNode()
                                .executes(command)
                        )
                ).build();
    }

    private CommandNode<S> selectorArguments(CommandNode<S> node) {
        return dimensionNode()
                .then(listNameNode()
                        .then(waypointNameNode()
                                .then(node)
                        )
                ).build();
    }

    @SuppressWarnings("unchecked")
    private CommandNode<S> propertiesArguments(Command<S> command) {
        return (CommandNode<S>) argument(NEW_WAYPOINT_NAME_ARG, string())
                .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                .then(argument(INITIALS_ARG, string())
                        .suggests((SuggestionProvider<Object>) NEW_NAME_INITIALS_SUGGESTION)
                        .then(argument(POS_ARG, blockPosArgumentProvider.get())
                                .then(argument(COLOR_ARG, string())
                                        .suggests((SuggestionProvider<Object>) HEX_COLOR_CODE_SUGGESTION)
                                        .then(argument(YAW_ARG, integer())
                                                .suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)
                                                .then(argument(VISIBILITY_ARG, bool())
                                                        .executes((Command<Object>) command)
                                                )
                                        )
                                )
                        )
                ).build();
    }

    private ArgumentBuilder<S, ?> dimensionNode() {
        return argument(DIMENSION_ARG, this.dimensionArgumentProvider.get());
    }

    @SuppressWarnings("unchecked")
    private ArgumentBuilder<S, ?> listNameNode() {
        return (ArgumentBuilder<S, ?>) argument(LIST_NAME_ARG, string()).suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION);
    }

    @SuppressWarnings("unchecked")
    private ArgumentBuilder<S, ?> waypointNameNode() {
        return (ArgumentBuilder<S, ?>) argument(WAYPOINT_NAME_ARG, string()).suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION);
    }

    private LiteralArgumentBuilder<S> listCommandNode() {
        LiteralArgumentBuilder<S> listNode = literal(LIST_COMMAND);
        configureListTarget(listNode, ListScope.CURRENT_DIMENSION);

        LiteralArgumentBuilder<S> allNode = literal("all");
        configureListTarget(allNode, ListScope.ALL_DIMENSIONS);
        listNode.then(allNode);

        RequiredArgumentBuilder<S, D> dimensionNode = argument(DIMENSION_ARG, this.dimensionArgumentProvider.get());
        configureListTarget(dimensionNode, ListScope.DIMENSION);

        RequiredArgumentBuilder<S, String> listNameNode = argument(LIST_NAME_ARG, string());
        listNameNode.suggests(this.WAYPOINT_LIST_SUGGESTION);
        configureListTarget(listNameNode, ListScope.WAYPOINT_LIST);
        dimensionNode.then(listNameNode);
        listNode.then(dimensionNode);
        return listNode;
    }

    private void configureListTarget(ArgumentBuilder<S, ?> targetNode, ListScope scope) {
        targetNode.executes(listCommand(scope, WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = listSearchNode(scope);
        LiteralArgumentBuilder<S> sortNode = listSortNode(scope);
        LiteralArgumentBuilder<S> pageNode = listPageNode(scope, WaypointSorting.SortMode.DEFAULT, false);
        LiteralArgumentBuilder<S> limitNode = listLimitNode(scope, WaypointSorting.SortMode.DEFAULT, false);
        if (scope == ListScope.DIMENSION) {
            searchNode.executes(reservedListCommand(SEARCH_COMMAND));
            sortNode.executes(reservedListCommand(SORT_COMMAND));
            pageNode.executes(reservedListCommand(PAGE_COMMAND));
            limitNode.executes(reservedListCommand(LIMIT_COMMAND));
        }
        targetNode.then(searchNode);
        targetNode.then(sortNode);
        targetNode.then(pageNode);
        targetNode.then(limitNode);
    }

    private LiteralArgumentBuilder<S> listSearchNode(ListScope scope) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(listCommand(scope, WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listSortNode(scope));
        queryNode.then(listPageNode(scope, WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listLimitNode(scope, WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> listSortNode(ListScope scope) {
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            LiteralArgumentBuilder<S> modeNode = literal(sortMode.name().toLowerCase(Locale.ROOT));
            modeNode.executes(listCommand(scope, sortMode, false));
            if (sortMode != WaypointSorting.SortMode.DEFAULT) {
                modeNode.then(listOrderNode(scope, sortMode));
            }
            modeNode.then(listPageNode(scope, sortMode, false));
            modeNode.then(listLimitNode(scope, sortMode, false));
            sortNode.then(modeNode);
        }
        return sortNode;
    }

    private LiteralArgumentBuilder<S> listOrderNode(ListScope scope, WaypointSorting.SortMode sortMode) {
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);

        LiteralArgumentBuilder<S> ascendingNode = literal("ascending");
        ascendingNode.executes(listCommand(scope, sortMode, false));
        ascendingNode.then(listPageNode(scope, sortMode, false));
        ascendingNode.then(listLimitNode(scope, sortMode, false));
        orderNode.then(ascendingNode);

        LiteralArgumentBuilder<S> descendingNode = literal("descending");
        descendingNode.executes(listCommand(scope, sortMode, true));
        descendingNode.then(listPageNode(scope, sortMode, true));
        descendingNode.then(listLimitNode(scope, sortMode, true));
        orderNode.then(descendingNode);
        return orderNode;
    }

    private LiteralArgumentBuilder<S> listPageNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> pageNode = argument(PAGE_NUMBER_ARG, integer(1));
        pageNode.executes(listCommand(scope, sortMode, reversed));
        pageNode.then(listLimitNode(scope, sortMode, reversed));
        LiteralArgumentBuilder<S> pageLiteral = literal(PAGE_COMMAND);
        return pageLiteral.then(pageNode);
    }

    private LiteralArgumentBuilder<S> listLimitNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        RequiredArgumentBuilder<S, Integer> limitNode = argument(PAGE_LIMIT_ARG, integer(1, MAX_PAGE_LIMIT));
        limitNode.executes(listCommand(scope, sortMode, reversed));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        return context -> {
            executeList(context, scope, sortMode, reversed, null);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> reservedListCommand(String listName) {
        return context -> {
            executeList(
                    context,
                    ListScope.WAYPOINT_LIST,
                    WaypointSorting.SortMode.DEFAULT,
                    false,
                    listName
            );
            return Command.SINGLE_SUCCESS;
        };
    }

    @SuppressWarnings("unchecked")
    public @NotNull LiteralCommandNode<S> build() {
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
                .then(literal(ADD_COMMAND)
                        .requires(source -> hasAddPermission((S) source))
                        .then(argument(DIMENSION_ARG, this.dimensionArgumentProvider.get())
                                .then(argument(LIST_NAME_ARG, string())
                                        .suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)
                                        .executes(cxt -> {
                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                            executeAddWaypointList(
                                                    context.getSource(),
                                                    getArgument(context, DIMENSION_ARG),
                                                    getString(context, LIST_NAME_ARG)
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        })
                                        .then(argument(POS_ARG, blockPosArgumentProvider.get())
                                                .then(argument(WAYPOINT_NAME_ARG, string())
                                                        .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                                        .then(argument(INITIALS_ARG, string())
                                                                .suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)
                                                                .then(argument(COLOR_ARG, string())
                                                                        .suggests((SuggestionProvider<Object>) HEX_COLOR_CODE_SUGGESTION)
                                                                        .then(argument(YAW_ARG, integer())
                                                                                .suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)
                                                                                .then(argument(VISIBILITY_ARG, bool())
                                                                                        .executes(cxt -> {
                                                                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                                                                            executeAddWaypoint(
                                                                                                    context.getSource(),
                                                                                                    getArgument(context, DIMENSION_ARG),
                                                                                                    getString(context, LIST_NAME_ARG),
                                                                                                    getString(context, WAYPOINT_NAME_ARG),
                                                                                                    getString(context, INITIALS_ARG),
                                                                                                    getArgument(context, POS_ARG),
                                                                                                    getInteger(context, YAW_ARG),
                                                                                                    getArgument(context, COLOR_ARG),
                                                                                                    getBool(context, VISIBILITY_ARG)
                                                                                            );
                                                                                            return Command.SINGLE_SUCCESS;
                                                                                        })
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .then(argument(POS_ARG, blockPosArgumentProvider.get())
                                .then(argument(LIST_NAME_ARG, string())
                                        .suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)
                                        .then(argument(WAYPOINT_NAME_ARG, string())
                                                .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                                .executes(
                                                        cxt -> {
                                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                                            S source = context.getSource();
                                                            executeQuickAddWaypoint(
                                                                    source,
                                                                    getArgument(context, POS_ARG),
                                                                    getString(context, LIST_NAME_ARG),
                                                                    getString(context, WAYPOINT_NAME_ARG)
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                )
                                                .then(argument(INITIALS_ARG, string())
                                                        .suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)
                                                        .then(argument(COLOR_ARG, string())
                                                                .suggests((SuggestionProvider<Object>) HEX_COLOR_CODE_SUGGESTION)
                                                                .then(argument(YAW_ARG, integer())
                                                                        .suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)
                                                                        .then(argument(VISIBILITY_ARG, bool())
                                                                                .executes(cxt -> {
                                                                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                                                                            S source = context.getSource();
                                                                                            executeAddWaypoint(
                                                                                                    source,
                                                                                                    getSourceDimension(source),
                                                                                                    getString(context, LIST_NAME_ARG),
                                                                                                    getString(context, WAYPOINT_NAME_ARG),
                                                                                                    getString(context, INITIALS_ARG),
                                                                                                    getArgument(context, POS_ARG),
                                                                                                    getInteger(context, YAW_ARG),
                                                                                                    getString(context, COLOR_ARG),
                                                                                                    getBool(context, VISIBILITY_ARG)
                                                                                            );
                                                                                            return Command.SINGLE_SUCCESS;
                                                                                        }
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
                .then(literal(EDIT_COMMAND)
                        .requires(source -> hasEditPermission((S) source))
                        .then((CommandNode<Object>)
                                selectorArguments(
                                        propertiesArguments(
                                                context -> {
                                                    executeEdit(
                                                            context.getSource(),
                                                            getArgument(context, DIMENSION_ARG),
                                                            getString(context, LIST_NAME_ARG),
                                                            getString(context, WAYPOINT_NAME_ARG),
                                                            getString(context, NEW_WAYPOINT_NAME_ARG),
                                                            getString(context, INITIALS_ARG),
                                                            getArgument(context, POS_ARG),
                                                            getInteger(context, YAW_ARG),
                                                            getString(context, COLOR_ARG),
                                                            getBool(context, VISIBILITY_ARG)
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                        )
                                )
                        )
                )
                .then(literal(REMOVE_COMMAND)
                        .requires(source -> hasRemovePermission((S) source))
                        .then((ArgumentBuilder<Object, ?>) dimensionNode()
                                .then(listNameNode()
                                        .executes(
                                                context -> {
                                                    executeRemoveList(
                                                            context.getSource(),
                                                            getArgument(context, DIMENSION_ARG),
                                                            getString(context, LIST_NAME_ARG)
                                                            );
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                        )
                                        .then(waypointNameNode()
                                                .executes(
                                                        context -> {
                                                            executeRemoveWaypoint(
                                                                    context.getSource(),
                                                                    getArgument(context, DIMENSION_ARG),
                                                                    getString(context, LIST_NAME_ARG),
                                                                    getString(context, WAYPOINT_NAME_ARG)
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                )
                                        )
                                )
                        )
                )
                .then(literal(TP_COMMAND)
                        .requires(source -> hasTpPermission((S) source))
                        .then((CommandNode<Object>)
                                selectorArguments(
                                        context -> {
                                            executeTp(
                                                    context.getSource(),
                                                    getArgument(context, DIMENSION_ARG),
                                                    getString(context, LIST_NAME_ARG),
                                                    getString(context, WAYPOINT_NAME_ARG)
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }
                                )
                        )
                )
                .then(literal(DOWNLOAD_COMMAND)
                        .executes(
                                context -> {
                                    executeDownload((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                }
                        )
                        .then((ArgumentBuilder<Object, ?>) dimensionNode()
                                .executes(
                                        context -> {
                                            executeDownload(context.getSource(), getArgument(context, DIMENSION_ARG));
                                            return Command.SINGLE_SUCCESS;
                                        }
                                )
                                .then(listNameNode()
                                        .executes(
                                                context -> {
                                                    executeDownload(
                                                            context.getSource(),
                                                            getArgument(context, DIMENSION_ARG),
                                                            getString(context, LIST_NAME_ARG)
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                        )
                                        .then(waypointNameNode()
                                                .executes(
                                                        context -> {
                                                            executeDownload(
                                                                    context.getSource(),
                                                                    getArgument(context, DIMENSION_ARG),
                                                                    getString(context, LIST_NAME_ARG),
                                                                    getString(context, WAYPOINT_NAME_ARG)
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        }
                                                )
                                        )
                                )
                        )
                )
                .then((ArgumentBuilder<Object, ?>) listCommandNode())
                .then(literal(RELOAD_COMMAND)
                        .requires(source -> hasReloadPermission((S) source))
                        .executes(
                                context -> {
                                    executeReload((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                }
                        )
                )
                .build();
    }

    private void runIfPlayerExists(S source, Consumer<P> playerAction) {
        P player = getPlayer(source);
        if (player != null) {
            playerAction.accept(player);
        }
    }

    /**
     * pass a non-empty not null WaypointFileManager
     */
    private void runWithSelectorTarget(S source, D dimensionArgument, Consumer<@NotNull WaypointFileManager> foundAction) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (isDimensionValid(source, dimensionArgument)) {
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
            } else {
                foundAction.accept(fileManager);
            }
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void runWithSelectorTarget(S source, D dimensionArgument, String listName, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> foundAction, BiConsumer<@NotNull WaypointFileManager, @NotNull WaypointList> foundEmptyAction) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            WaypointList waypointList = fileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
            } else if (waypointList.isEmpty()) {
                foundEmptyAction.accept(fileManager, waypointList);
            } else {
                foundAction.accept(fileManager, waypointList);
            }
        });
    }

    private void runWithSelectorTarget(S source, D dimensionArgument, String listName, String name, TriConsumer<@NotNull WaypointFileManager, @NotNull WaypointList, @NotNull SimpleWaypoint> action) {
        runWithSelectorTarget(source, dimensionArgument, listName, (fileManager, waypointList) -> {
            SimpleWaypoint waypoint = waypointList.getWaypointByName(name);
            if (waypoint == null) {
                this.sender.sendError(source, translatable("waypoint.nonexist.waypoint", text(name)));
            } else {
                action.accept(fileManager, waypointList, waypoint);
            }
        }, (waypointList, waypoint) ->
                this.sender.sendError(source, translatable("waypoint.empty.list", text(listName))));
    }

    private void sendDimensionError(S source, String dimensionName) {
        this.sender.sendError(source, translatable("argument.dimension.invalid", dimensionNameWithColor(dimensionName)));
    }

    private void sendPosArgumentError(S source) {
        this.sender.sendError(source, translatable("argument.pos.invalid"));
    }

    private void sendHexColorCodeError(S source, String hexColorCode) {
        this.sender.sendError(source, translatable("hex_color_code.invalid", text(hexColorCode)));
    }

    private void executeAddWaypointList(S source, D dimensionArgument, String listName) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (isDimensionValid(source, dimensionArgument)) {
            this.waypointServer.addWaypointList(dimensionName, listName,
                    (fileManager) -> {
                this.sender.broadcastWaypointModification(source, new WaypointModificationBuffer(dimensionName, listName, null, null, ADD_LIST, SERVER_N));
                this.sender.sendMessage(source, translatable("waypoint.add.list.success", text(listName), dimensionNameWithColor(dimensionName)));
                saveChanges(source, fileManager);
                },
                    () -> this.sender.sendError(source, translatable("waypoint.add.list.exists", text(listName))));
        }
    }

    private void addWaypointDirectly(S source, String dimensionName, String listName, String name, String initials, WaypointPos waypointPos, int yaw, int rgb, boolean global) {
        SimpleWaypoint newWaypoint = new SimpleWaypoint(name, initials, waypointPos, rgb, yaw, global);
        this.waypointServer.addWaypoint(dimensionName, listName, newWaypoint,
                // success
                (fileManager, waypointList) -> {
                    saveChanges(source, fileManager);
                    this.sender.broadcastWaypointModification(source, new WaypointModificationBuffer(
                            dimensionName,
                            listName,
                            name,
                            newWaypoint,
                            WaypointModificationType.ADD,
                            waypointList.getSyncNum()
                    ));
                    this.sender.sendMessage(
                            source,
                            translatable("waypoint.add.success",
                                    waypointTextWithTp(newWaypoint, dimensionName, listName),
                                    text(listName)
                            )
                    );
                },
                // found duplicate
                (waypointFound) -> this.sender.sendMessage(source, translatable("waypoint.add.exists", waypointTextWithTp(waypointFound, dimensionName, listName), TextButton.replaceButton(dimensionName, listName, newWaypoint)))
        );
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, String hexCode, boolean global) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (isDimensionValid(source, dimensionArgument)) {
            WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
            if (waypointPos == null) {
                sendPosArgumentError(source);
                return;
            }
            int rgb;
            if (RANDOM_COLOR.equals(hexCode)) {
                rgb = randomColor();
            } else {
                rgb = colorNameOrHexCodeToRgb(hexCode, false);
            }
            if (rgb < 0) {
                sendHexColorCodeError(source, hexCode);
                return;
            }
            addWaypointDirectly(source, dimensionName, listName, name, initials, waypointPos, yaw, rgb, global);
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void executeQuickAddWaypoint(S source, B blockPosArgument, String listName, String name) {
        addWaypointDirectly(source, toDimensionName(getSourceDimension(source)), listName, name, WaypointInitials.getDefaultInitials(name), toWaypointPos(source, blockPosArgument), Math.round(getSourceYaw(source)), randomColor(), true);
    }

    private void executeEdit(S source, D dimensionArgument, String listName, String oldName, String newName, String initials, B blockPosArgument, int yaw, String hexCode, boolean global) {
        WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
        if (waypointPos == null) {
            sendPosArgumentError(source);
            return;
        }
        int rgb;
        if (RANDOM_COLOR.equals(hexCode)) {
            rgb = randomColor();
        } else {
            rgb = colorNameOrHexCodeToRgb(hexCode, false);
        }
        if (rgb < 0) {
            sendHexColorCodeError(source, hexCode);
        } else {
            runWithSelectorTarget(source, dimensionArgument, listName, oldName, (fileManager, waypointList, waypoint) ->
                this.waypointServer.updateWaypointProperties(fileManager, waypointList, waypoint, newName, initials, waypointPos, rgb, yaw, global,
                        () -> {
                            waypointList.incSyncNum();
                            saveChanges(source, fileManager);
                            String dimensionName = fileManager.getDimensionName();
                            WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, oldName, waypoint, WaypointModificationType.UPDATE, waypointList.getSyncNum());
//                            LOGGER.info("syncNum: {}", waypointList.getSyncNum());
                            this.sender.broadcastWaypointModification(source, buffer);
                            this.sender.sendMessage(source, translatable("waypoint.edit.success", waypointTextWithTp(waypoint, dimensionName, listName)));
                        },

                        () -> this.sender.sendMessage(source, translatable("waypoint.edit.existing-name", text(newName))),
                        () -> this.sender.sendMessage(source, translatable("waypoint.edit.identical")))
            );
        }
    }

    private void executeRemoveList(S source, D dimensionArgument, String listName) {
//        runWithSelectorTarget(source, dimensionArgument, listName,
//                (fileManager, waypointList) ->
//                        this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", text(listName))),
//                (fileManager, waypointList) -> {
//                    fileManager.removeWaypointListByName(listName);
//                    String dimensionName = fileManager.getDimensionName();
//                    this.sender.broadcastWaypointModification(source, new WaypointModificationBuffer(dimensionName, listName, null, null, REMOVE_LIST, WaypointList.REMOVE_LIST));
//                    this.sender.sendMessage(source, translatable("waypoint.remove.list.success", text(listName)));
//                    saveChanges(source, fileManager);
//                });
        runWithSelectorTarget(source, dimensionArgument, fileManager ->
            this.waypointServer.removeWaypointList(fileManager, listName, fileManager1 -> {
                        fileManager.removeWaypointListByName(listName);
                        String dimensionName = fileManager.getDimensionName();
                        this.sender.broadcastWaypointModification(source, new WaypointModificationBuffer(dimensionName, listName, null, null, REMOVE_LIST, WaypointList.REMOVE_LIST));
                        this.sender.sendMessage(source, translatable("waypoint.remove.list.success", text(listName)));
                        saveChanges(source, fileManager);
                    },
                    () -> this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName))),
                    () -> this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", text(listName))))
        );
    }

    private void executeRemoveWaypoint(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
            this.waypointServer.removeWaypoint(fileManager, waypointList, waypoint);
            saveChanges(source, fileManager);
            String dimensionName = fileManager.getDimensionName();
            WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, name, waypoint, WaypointModificationType.REMOVE, waypointList.getSyncNum());
            this.sender.broadcastWaypointModification(source, buffer);
            this.sender.sendMessage(source, translatable("waypoint.remove.success", waypointTextNoTp(waypoint, dimensionName), restoreButton(dimensionName, listName, waypoint)));
        });
    }

    private void executeTp(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                runIfPlayerExists(source, player -> {
                    teleportPlayer(source, player, dimensionArgument, waypoint.pos(), waypoint.yaw());
                    this.sender.sendPlayerMessage(player, translatable("waypoint.tp", text(getPlayerName(player)), waypointTextWithTp(waypoint, fileManager.getDimensionName(), listName)));
                }));
    }

    private void executeDownload(S source) {
        WorldWaypointBuffer buffer = this.waypointServer.toWorldWaypointBuffer();
        if (buffer == null) {
            this.sender.sendMessage(source, translatable("waypoint.no.waypoints"));
            return;
        }
        this.sender.sendMessage(source, translatable("waypoint.download.all"));
        this.sender.sendPacket(source, buffer);
    }

    private void executeDownload(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                return;
            }
            this.sender.sendMessage(source, translatable("waypoint.download.dimension", dimensionNameWithColor(dimensionName)));
            this.sender.sendPacket(source, fileManager.toDimensionWaypoint());
        });
    }

    private void executeDownload(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> {
                    this.sender.sendMessage(source, translatable("waypoint.download.list", text(listName)));
                    this.sender.sendPacket(source, new WaypointListBuffer(fileManager.getDimensionName(), waypointList));
                }, (fileManager, waypointList) ->
                        this.sender.sendError(source, translatable("waypoint.empty.list", text(listName)))
        );
    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
            String dimensionName = fileManager.getDimensionName();
            this.sender.sendMessage(source, translatable("waypoint.download.waypoint", waypointTextWithTp(waypoint, dimensionName, listName)));
            this.sender.sendPacket(source, new WaypointModificationBuffer(dimensionName, listName, name, waypoint, WaypointModificationType.ADD, waypointList.getSyncNum()));
        });
    }

    private void executeList(
            CommandContext<S> context,
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            String fixedListName
    ) {
        S source = context.getSource();
        ListOptions options = new ListOptions(
                getOptionalString(context, SEARCH_QUERY_ARG, ""),
                sortMode,
                reversed,
                getOptionalInteger(context, PAGE_NUMBER_ARG, 1),
                getOptionalInteger(context, PAGE_LIMIT_ARG, CONFIG.defaultPageLimit())
        );
        if (scope == ListScope.ALL_DIMENSIONS) {
            WaypointQueryEngine.Query query = createListQuery(source, options);
            sendListQueryResult(
                    source,
                    new ListTarget(true, null, null),
                    options,
                    this.waypointQueryEngine.queryAll(query)
            );
            return;
        }

        D dimensionArgument = scope == ListScope.CURRENT_DIMENSION
                ? getSourceDimension(source)
                : getArgument(context, DIMENSION_ARG);
        String listName = fixedListName != null
                ? fixedListName
                : scope == ListScope.WAYPOINT_LIST ? getString(context, LIST_NAME_ARG) : null;
        executeListDimension(source, dimensionArgument, listName, options);
    }

    private String getOptionalString(CommandContext<S> context, String name, String defaultValue) {
        try {
            return getString(context, name);
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private int getOptionalInteger(CommandContext<S> context, String name, int defaultValue) {
        try {
            return getInteger(context, name);
        } catch (IllegalArgumentException ignored) {
            return defaultValue;
        }
    }

    private WaypointQueryEngine.Query createListQuery(S source, ListOptions options) {
        return new WaypointQueryEngine.Query(
                options.filterText(),
                options.sortMode(),
                getSourcePosition(source),
                toDimensionName(getSourceDimension(source)),
                options.reversed()
        );
    }

    private void executeListDimension(
            S source,
            D dimensionArgument,
            String listName,
            ListOptions options
    ) {
        runWithSelectorTarget(source, dimensionArgument, fileManager -> {
            String dimensionName = fileManager.getDimensionName();
            if (listName != null && fileManager.getWaypointListByName(listName) == null) {
                this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
                return;
            }
            WaypointQueryEngine.Query query = createListQuery(source, options);
            WaypointQueryEngine.QueryResult result = listName == null
                    ? this.waypointQueryEngine.queryDimension(dimensionName, query)
                    : this.waypointQueryEngine.queryList(dimensionName, listName, query);
            sendListQueryResult(
                    source,
                    new ListTarget(false, dimensionName, listName),
                    options,
                    result
            );
        });
    }

    private void sendListQueryResult(
            S source,
            ListTarget target,
            ListOptions options,
            WaypointQueryEngine.QueryResult result
    ) {
        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(result, true);
        if (result.waypointCount() == 0) {
            if (!options.filterText().trim().isEmpty()) {
                this.sender.sendMessage(
                        source,
                        translatable("waypoint.search.no_results", text(options.filterText()))
                );
                return;
            }
            if (target.listName() != null) {
                this.sender.sendMessage(source, translatable("waypoint.empty.list", text(target.listName())));
                return;
            }
            if (target.allDimensions() && result.listCount() > 0) {
                this.sender.sendMessage(source, getListDisplayText(source, display, false));
                return;
            }
            if (target.allDimensions()) {
                this.sender.sendMessage(source, translatable("waypoint.no.waypoints"));
                return;
            }
            this.sender.sendMessage(
                    source,
                    translatable("waypoint.empty.dimension", dimensionNameWithColor(target.dimensionName()))
            );
            return;
        }

        WaypointListPage.Page page = WaypointListPage.paginate(display, options.pageNumber(), options.pageLimit());
        if (page.pageNumber() != options.pageNumber()) {
            this.sender.sendError(
                    source,
                    translatable(
                            "waypoint.list.page.invalid",
                            text(options.pageNumber()),
                            text(page.totalPages())
                    )
            );
            return;
        }

        Component listText = getListDisplayText(source, page.display(), target.listName() != null);
        listText = listText.append(getListSortControls(target, options));
        if (page.totalPages() > 1) {
            listText = listText.append(getPageNavigation(target, options, page));
        }
        this.sender.sendMessage(source, listText);
    }

    private Component getListDisplayText(
            S source,
            WaypointListDisplayModel.Display display,
            boolean listOnly
    ) {
        boolean withEdit = hasEditPermission(source);
        boolean withRemove = hasRemovePermission(source);
        boolean withTp = hasTpPermission(source);
        if (listOnly) {
            WaypointListDisplayModel.DisplayList list = display.lists().get(0);
            return getWaypointListText(
                    list.sourceList(),
                    list.waypoints(),
                    list.dimensionName(),
                    0,
                    false,
                    withEdit,
                    withRemove,
                    withTp
            );
        }

        Component listText = text("").appendNewline();
        for (WaypointListDisplayModel.DisplayDimension dimension : display.dimensions()) {
            listText = listText.append(dimensionNameWithColor(dimension.dimensionName())).appendNewline();
            for (WaypointListDisplayModel.DisplayList list : dimension.lists()) {
                listText = listText.append(getWaypointListText(
                        list.sourceList(),
                        list.waypoints(),
                        dimension.dimensionName(),
                        1,
                        true,
                        withEdit,
                        withRemove,
                        withTp
                ));
            }
        }
        return listText;
    }

    private Component getListSortControls(ListTarget target, ListOptions options) {
        Component controls = translatable("waypoint.list.sort.label", NamedTextColor.GRAY);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            boolean selected = options.sortMode() == sortMode;
            controls = controls.appendSpace().append(listSortButton(
                    translatable(sortModeTranslationKey(sortMode)),
                    listPageCmd(
                            target.allDimensions(),
                            target.dimensionName(),
                            target.listName(),
                            options.filterText(),
                            sortMode,
                            false,
                            1,
                            options.pageLimit()
                    ),
                    selected,
                    true,
                    "button.sort." + sortMode.name().toLowerCase(Locale.ROOT)
            ));
        }

        boolean orderEnabled = options.sortMode() != WaypointSorting.SortMode.DEFAULT;
        controls = controls.appendSpace().append(text("·", NamedTextColor.GRAY)).appendSpace()
                .append(listSortButton(
                        text("↑"),
                        listPageCmd(
                                target.allDimensions(),
                                target.dimensionName(),
                                target.listName(),
                                options.filterText(),
                                options.sortMode(),
                                false,
                                1,
                                options.pageLimit()
                        ),
                        orderEnabled && !options.reversed(),
                        orderEnabled,
                        "button.sort.ascending"
                ))
                .appendSpace()
                .append(listSortButton(
                        text("↓"),
                        listPageCmd(
                                target.allDimensions(),
                                target.dimensionName(),
                                target.listName(),
                                options.filterText(),
                                options.sortMode(),
                                true,
                                1,
                                options.pageLimit()
                        ),
                        orderEnabled && options.reversed(),
                        orderEnabled,
                        "button.sort.descending"
                ));
        return controls.appendNewline()
                .decoration(TextDecoration.BOLD, false)
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component listSortButton(
            Component label,
            String command,
            boolean selected,
            boolean enabled,
            String hoverTranslationKey
    ) {
        NamedTextColor color = !enabled
                ? NamedTextColor.DARK_GRAY
                : selected ? NamedTextColor.GOLD : NamedTextColor.AQUA;
        Component button = text("[")
                .append(label)
                .append(text("]"))
                .color(color)
                .decoration(TextDecoration.BOLD, selected)
                .decoration(TextDecoration.ITALIC, false);
        if (!enabled) {
            return button.hoverEvent(HoverEvent.showText(translatable("button.sort.order.unavailable")));
        }
        if (selected) {
            return button;
        }
        return button.clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(translatable(hoverTranslationKey)));
    }

    private static String sortModeTranslationKey(WaypointSorting.SortMode sortMode) {
        return switch (sortMode) {
            case DEFAULT -> "waypoint.sort.default";
            case NAME -> "waypoint.sort.name";
            case DISTANCE -> "waypoint.sort.distance";
            case COLOR -> "waypoint.sort.color";
        };
    }

    private Component getPageNavigation(
            ListTarget target,
            ListOptions options,
            WaypointListPage.Page page
    ) {
        Component previous = page.hasPrevious()
                ? pageButton(
                        "←",
                        listPageCmd(
                                target.allDimensions(),
                                target.dimensionName(),
                                target.listName(),
                                options.filterText(),
                                options.sortMode(),
                                options.reversed(),
                                page.pageNumber() - 1,
                                options.pageLimit()
                        ),
                        "button.page.previous"
                )
                : text("[←]", NamedTextColor.DARK_GRAY);
        Component next = page.hasNext()
                ? pageButton(
                        "→",
                        listPageCmd(
                                target.allDimensions(),
                                target.dimensionName(),
                                target.listName(),
                                options.filterText(),
                                options.sortMode(),
                                options.reversed(),
                                page.pageNumber() + 1,
                                options.pageLimit()
                        ),
                        "button.page.next"
                )
                : text("[→]", NamedTextColor.DARK_GRAY);
        Component pageText = translatable(
                "waypoint.list.page",
                text(page.pageNumber()),
                text(page.totalPages()),
                text(page.limit()),
                text(page.totalWaypoints())
        ).color(NamedTextColor.GRAY);
        return previous.appendSpace().append(pageText).appendSpace().append(next)
                .decoration(TextDecoration.BOLD, false);
    }

    private Component pageButton(String symbol, String command, String hoverTranslationKey) {
        return text("[" + symbol + "]", NamedTextColor.AQUA)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(translatable(hoverTranslationKey)));
    }

    private enum ListScope {
        CURRENT_DIMENSION,
        ALL_DIMENSIONS,
        DIMENSION,
        WAYPOINT_LIST
    }

    private record ListTarget(boolean allDimensions, String dimensionName, String listName) {
    }

    private record ListOptions(
            String filterText,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            int pageNumber,
            int pageLimit
    ) {
    }

    private void executeReload(S source) {
        executeByServer(source, () -> {
            this.waypointServer.reload();
            List<String> lang = getExternalLoadedLanguages();
            this.sender.sendMessage(source, translatable("waypoint.loaded.languages",
                    text(lang.size()), text(String.join(", ", lang))));
        });
        this.sender.sendMessage(source, translatable("waypoint.reload"));
    }

    private void saveChanges(S source, WaypointFileManager fileManager) {
        executeByServer(source, () -> {
            try {
                fileManager.saveDimension();
            } catch (IOException e) {
                this.sender.sendError(source, translatable("waypoint.save.failed", text(fileManager.getDimensionFile().toString())));
                throw new RuntimeException(e);
            }
        });
    }

    public void register(@NotNull CommandDispatcher<S> dispatcher) {
        dispatcher.getRoot().addChild(build());
    }

    private D getDefaultDimension(CommandContext<S> context) {
        try {
            return getArgument(context, DIMENSION_ARG);
        } catch (Exception e) {
            return getSourceDimension(context.getSource());
        }
    }

    private @NotNull String stripOuterQuotes(String string) {
        if (string.startsWith("\"") || string.startsWith("'")) {
            string = string.substring(1);
        }
        if (string.endsWith("\"") || string.endsWith("'")) {
            string = string.substring(0, string.length() - 1);
        }
        return string;
    }

    private @NotNull String warpQuotes(String string) {
        return "\"" + string + "\"";
    }

    private class WaypointListSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            D dimension = getDefaultDimension(context.getLastChild());
            WaypointFileManager fileManager = CoreWaypointCommand.this.waypointServer.getWaypointFileManager(toDimensionName(dimension));
            if (fileManager == null) {
                return Suggestions.empty();
            } else {
                String currentInput = stripOuterQuotes(builder.getRemaining());
                for (String listName : fileManager.getWaypointListMap().keySet()) {
                    if (listName.startsWith(currentInput)) {
                        builder.suggest(escapeListName(listName));
                    }
                }
            }
            return builder.buildFuture();
        }
    }

    private class WaypointNameSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            CommandContext<S> currentContext = context.getLastChild();
            D dimension = getDefaultDimension(currentContext);
            WaypointFileManager fileManager = CoreWaypointCommand.this.waypointServer.getWaypointFileManager(toDimensionName(dimension));
            if (fileManager == null) {
                return Suggestions.empty();
            }
            WaypointList waypointList = fileManager.getWaypointListByName(getString(currentContext, LIST_NAME_ARG));
            if (waypointList == null) {
                return Suggestions.empty();
            } else {
                String currentInput = stripOuterQuotes(builder.getRemaining());
                for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                    String name = waypoint.name();
                    if (name.startsWith(currentInput)) {
                        if (name.matches(SINGLE_WORD_REGEX)) {
                            builder.suggest(name);
                        } else {
                            builder.suggest("\"%s\"".formatted(name));
                        }
                    }
                }
                return builder.buildFuture();
            }
        }
    }

    private CompletableFuture<Suggestions> getInitialsSuggestions(CommandContext<S> context, SuggestionsBuilder builder, String argName) {
        String name = getString(context.getLastChild(), argName);
        List<String> initials = WaypointInitials.getInitialsCandidatesFromName(name);
        if (initials.isEmpty()) {
            return Suggestions.empty();
        }
        for (String initial : initials) {
            if (initial.matches(SINGLE_WORD_REGEX)) {
                builder.suggest(initial);
            } else {
                builder.suggest(warpQuotes(initial));
            }
        }
        return builder.buildFuture();
    }

    private class NameInitialsSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            return getInitialsSuggestions(context, builder, WAYPOINT_NAME_ARG);
        }
    }

    private class NewNameInitialsSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            return getInitialsSuggestions(context, builder, NEW_WAYPOINT_NAME_ARG);
        }
    }

    public class PlayerYawSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            float yaw = getSourceYaw(context.getSource());
            builder.suggest(Math.round(yaw));
            if (yaw != 0f) {
                builder.suggest(0);
            }
            return builder.buildFuture();
        }
    }

    public class HexColorCodeSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            String currentInput = stripOuterQuotes(builder.getRemaining());
            if (currentInput.isEmpty()) {
                vanillaColorSuggestions(builder);
                builder.suggest(RANDOM_COLOR, getMessageFromComponent(text("🎲")));
                builder.suggest("39C5BB", getMessageFromComponent(text("Miku♪", TextColor.color(0x39C5BB))));
            } else {
                for (int i = 0; i < VANILLA_COLOR_NAMES.length; i++) {
                    String colorName = VANILLA_COLOR_NAMES[i];
                    if (colorName.startsWith(currentInput)) {
                        builder.suggest(colorName, getHexColorCodeTooltip(VANILLA_COLOR_CODES[i], VANILLA_COLORS[i]));
                    }
                }
                if (RANDOM_COLOR.startsWith(currentInput)) {
                    builder.suggest(RANDOM_COLOR, getMessageFromComponent(text("🎲")));
                }
                if ("39C5BB".startsWith(currentInput)) {
                    builder.suggest("39C5BB", getMessageFromComponent(text("miku", TextColor.color(0x39C5BB))));
                }
                int length = currentInput.length();
                if (length < 6) {
                    try {
                        int lengthRemain = 6 - length;
                        int rgb = Integer.parseInt(currentInput, 16) << lengthRemain * 4;
                        String hexCode = currentInput.toUpperCase() + "0".repeat(lengthRemain);
                        builder.suggest("%s".formatted(hexCode), getHexColorCodeTooltip("#" + hexCode, rgb));
                    } catch (NumberFormatException e) {
                        return builder.buildFuture();
                    }
                } else if (length == 6) {
                    try {
                        int rgb = Integer.parseInt(currentInput, 16);
                        String hexCode = currentInput.toUpperCase();
                        builder.suggest("%s ".formatted(hexCode), getHexColorCodeTooltip("#" + hexCode, rgb));
                    } catch (NumberFormatException e) {
                        return builder.buildFuture();
                    }
                }
            }
            return builder.buildFuture();
        }

        private Message getHexColorCodeTooltip(String hexCode, int rgb) {
            return getMessageFromComponent(text("⬛", TextColor.color(rgb))
                    .appendSpace()
                    .append(text(hexCode, NamedTextColor.WHITE)));
        }

        private void vanillaColorSuggestions(SuggestionsBuilder builder) {
            for (int i = 0; i < VANILLA_COLOR_NAMES.length; i++) {
                builder.suggest(VANILLA_COLOR_NAMES[i], getHexColorCodeTooltip(VANILLA_COLOR_CODES[i], VANILLA_COLORS[i]));
            }
        }
    }

}
