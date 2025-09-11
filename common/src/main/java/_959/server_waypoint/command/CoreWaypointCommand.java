package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.WaypointListBuffer;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.network.buffer.WorldWaypointBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.TextButton;
import _959.server_waypoint.util.TriConsumer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.text.TextButton.*;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.translation.LanguageFilesManager.getLoadedLanguages;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public abstract class CoreWaypointCommand<S, K, P, D, B, C> {
    protected final PlatformMessageSender<S, P> sender;
    private final PermissionManager<S, K, P> permissionManager;
    private final PermissionKeys<K> permissionKeys;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final Supplier<ArgumentType<B>> blockPosArgumentProvider;
    private final Supplier<ArgumentType<C>> colorArgumentProvider;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();
    private boolean enabled = true;
    private static final String WAYPOINT_COMMAND;
    private static final String ADD_COMMAND;
    private static final String EDIT_COMMAND;
    private static final String REMOVE_COMMAND;
    private static final String LIST_COMMAND;
    private static final String DOWNLOAD_COMMAND;
    private static final String TP_COMMAND;
    private static final String RELOAD_COMMAND;
    private static final String DIMENSION_ARG;
    private static final String LIST_NAME_ARG;
    private static final String WAYPOINT_NAME_ARG;
    private static final String INITIALS_ARG;
    private static final String POS_ARG;
    private static final String YAW_ARG;
    private static final String COLOR_ARG;
    private static final String VISIBILITY_ARG;

    public CoreWaypointCommand(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissionManager, Supplier<ArgumentType<D>> dimensionArgument, Supplier<ArgumentType<B>> blockPositionArgument, Supplier<ArgumentType<C>> colorArgument) {
        this.dimensionArgumentProvider = dimensionArgument;
        this.blockPosArgumentProvider = blockPositionArgument;
        this.colorArgumentProvider = colorArgument;
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.permissionKeys = permissionManager.keys;
    }

    protected abstract String toDimensionName(D dimensionArgument);
    protected abstract WaypointPos toWaypointPos(S source, B blockPositionArgument);
    protected abstract int toColorIdx(C colorArgument);
    protected abstract boolean isDimensionValid(S source, D dimensionArgument);
    protected abstract void executeByServer(S source, Runnable task);
    protected abstract D getSourceDimension(S source);
    protected abstract float getSourceYaw(S source);
    protected abstract P getPlayer(S source);
    protected abstract String getPlayerName(P player);
    protected abstract void teleportPlayer(S source, P player, D dimensionArgument, WaypointPos pos, int yaw);

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
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
        return (CommandNode<S>) argument(INITIALS_ARG, string())
                .suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)
                .then(argument(POS_ARG, blockPosArgumentProvider.get())
                        .then(argument(COLOR_ARG, colorArgumentProvider.get())
                                .then(argument(YAW_ARG, integer())
                                        .suggests((SuggestionProvider<Object>) PLAYER_YAW_SUGGESTION)
                                        .then(argument(VISIBILITY_ARG, bool())
                                                .executes((Command<Object>) command)
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

    @SuppressWarnings("unchecked")
    public @NotNull LiteralCommandNode<S> build() {
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
                .requires(source -> this.enabled)
                .then(literal(ADD_COMMAND)
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.add(), CONFIG.CommandPermission().add()))
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
                                                                .then(argument(COLOR_ARG, colorArgumentProvider.get())
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
                                                .then(argument(INITIALS_ARG, string())
                                                        .suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)
                                                        .then(argument(COLOR_ARG, colorArgumentProvider.get())
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
                                                                                                    getArgument(context, COLOR_ARG),
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
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.edit(), CONFIG.CommandPermission().edit()))
                        .then((CommandNode<Object>)
                                selectorArguments(
                                        propertiesArguments(context -> {
                                            executeEdit(
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
                .then(literal(REMOVE_COMMAND)
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.remove(), CONFIG.CommandPermission().remove()))
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
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.tp(), CONFIG.CommandPermission().tp()))
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
                .then(literal(LIST_COMMAND)
                        .executes(
                                context -> {
                                    executeList((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                }
                        )
                )
                .then(literal(RELOAD_COMMAND)
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.reload(), CONFIG.CommandPermission().reload()))
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
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                this.sender.sendError(source, Component.translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
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
                this.sender.sendError(source, Component.translatable("waypoint.nonexist.list", Component.text(listName)));
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
                this.sender.sendError(source, Component.translatable("waypoint.nonexist.waypoint", Component.text(name)));
            } else {
                action.accept(fileManager, waypointList, waypoint);
            }
        }, (waypointList, waypoint) ->
                this.sender.sendError(source, Component.translatable("waypoint.empty.list", Component.text(listName))));
    }

    private void sendDimensionError(S source, String dimensionName) {
        this.sender.sendError(source, Component.translatable("argument.dimension.invalid", dimensionNameWithColor(dimensionName)));
    }

    private void sendPosArgumentError(S source) {
        this.sender.sendError(source, Component.translatable("argument.pos.invalid"));
    }

    private void executeAddWaypointList(S source, D dimensionArgument, String listName) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (isDimensionValid(source, dimensionArgument)) {
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                fileManager = WaypointServerCore.INSTANCE.addWaypointFileManager(dimensionName);
            }
            WaypointList foundList = fileManager.getWaypointListByName(listName);
            if (foundList == null) {
                fileManager.addWaypointList(WaypointList.build(listName));
                this.sender.sendMessage(source, Component.translatable("waypoint.add.list.success", Component.text(listName), dimensionNameWithColor(dimensionName)));
                saveChanges(source, fileManager);
                return;
            }
            this.sender.sendError(source, Component.translatable("waypoint.add.list.exists", Component.text(listName)));
        }
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, C color, boolean global) {
        int colorIdx = toColorIdx(color);
        String dimensionName = toDimensionName(dimensionArgument);
        if  (isDimensionValid(source, dimensionArgument)) {
            WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
            if (waypointPos == null) {
                sendPosArgumentError(source);
                return;
            }
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
            WaypointList waypointList;
            if (fileManager == null) {
                fileManager = WaypointServerCore.INSTANCE.addWaypointFileManager(dimensionName);
                waypointList = WaypointList.build(listName);
                fileManager.addWaypointList(waypointList);
            } else {
                waypointList = fileManager.getWaypointListByName(listName);
                if (waypointList == null) {
                    waypointList = WaypointList.build(listName);
                    fileManager.addWaypointList(waypointList);
                }
            }
            SimpleWaypoint newWaypoint = new SimpleWaypoint(
                    name,
                    initials,
                    waypointPos,
                    colorIdx,
                    yaw,
                    global
            );
            SimpleWaypoint waypointFound = waypointList.getWaypointByName(name);
            if (waypointFound == null) {
                waypointList.add(newWaypoint);
            } else {
                this.sender.sendMessage(source,
                        Component.translatable("waypoint.add.exists",
                                defaultWaypointText(waypointFound, dimensionName, listName),
                                TextButton.replaceButton(dimensionName, listName, newWaypoint)
                        )
                );
                return;
            }
            saveChanges(source, fileManager);
            this.sender.broadcastWaypointModification(source, new WaypointModificationBuffer(
                    dimensionName,
                    listName,
                    newWaypoint,
                    WaypointModificationType.ADD,
                    WaypointServerCore.EDITION
            ));
            this.sender.sendMessage(
                    source,
                    Component.translatable("waypoint.add.success",
                            defaultWaypointText(newWaypoint, dimensionName, listName),
                            Component.text(listName)
                    )
            );
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void executeEdit(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, C color, boolean global) {
        WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
        if (waypointPos == null) {
            sendPosArgumentError(source);
            return;
        }
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
            int colorIdx = toColorIdx(color);
            if (waypoint.compareProperties(initials, waypointPos, colorIdx, yaw, global)) {
                this.sender.sendMessage(source, Component.translatable("waypoint.edit.identical", Component.text(name)));
                return;
            } else {
                waypoint.setInitials(initials);
                waypoint.setPos(waypointPos);
                waypoint.setColorIdx(colorIdx);
                waypoint.setYaw(yaw);
                waypoint.setGlobal(global);
            }
            saveChanges(source, fileManager);
            String dimensionName = fileManager.getDimensionName();
            WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.UPDATE, WaypointServerCore.EDITION);
            this.sender.broadcastWaypointModification(source, buffer);
            this.sender.sendMessage(source, Component.translatable("waypoint.edit.success", defaultWaypointText(waypoint, dimensionName, listName)));
        });
    }

    private void executeRemoveList(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) ->
                        this.sender.sendError(source, Component.translatable("waypoint.remove.list.nonempty", Component.text(listName))),
                (fileManager, waypointList) -> {
                    fileManager.removeWaypointListByName(listName);
                    this.sender.sendMessage(source, Component.translatable("waypoint.remove.list.success", Component.text(listName)));
                    saveChanges(source, fileManager);
                });
    }

    private void executeRemoveWaypoint(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
           waypointList.remove(waypoint);
           saveChanges(source, fileManager);
           String dimensionName = fileManager.getDimensionName();
            WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.REMOVE, WaypointServerCore.EDITION);
            this.sender.broadcastWaypointModification(source, buffer);
            this.sender.sendMessage(source, Component.translatable("waypoint.remove.success", defaultWaypointText(waypoint, dimensionName, listName), restoreButton(dimensionName, listName, waypoint)));
        });
    }

    private void executeTp(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                runIfPlayerExists(source, player -> {
                    teleportPlayer(source, player, dimensionArgument, waypoint.pos(), waypoint.yaw());
                    this.sender.sendPlayerMessage(player, Component.translatable("waypoint.tp", Component.text(getPlayerName(player)), defaultWaypointText(waypoint, fileManager.getDimensionName(), listName)));
                }));
    }

    private void executeDownload(S source) {
        WorldWaypointBuffer buffer = WaypointServerCore.INSTANCE.toWorldWaypointBuffer();
        if (buffer == null) {
            this.sender.sendMessage(source, Component.translatable("waypoint.no.waypoints"));
            return;
        }
        this.sender.sendMessage(source, Component.translatable("waypoint.download.all"));
        this.sender.sendPacket(source, buffer);
    }

    private void executeDownload(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendError(source, Component.translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                return;
            }
            this.sender.sendMessage(source, Component.translatable("waypoint.download.dimension", dimensionNameWithColor(dimensionName)));
            this.sender.sendPacket(source, fileManager.toDimensionWaypoint());
        });
    }

    private void executeDownload(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> {
                    this.sender.sendMessage(source, Component.translatable("waypoint.download.list", Component.text(listName)));
                    this.sender.sendPacket(source, new WaypointListBuffer(fileManager.getDimensionName(), waypointList));
                }, (fileManager, waypointList) ->
                        this.sender.sendError(source, Component.translatable("waypoint.empty.list", Component.text(listName)))
        );
    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
            String dimensionName = fileManager.getDimensionName();
            this.sender.sendMessage(source, Component.translatable("waypoint.download.waypoint", defaultWaypointText(waypoint, dimensionName, listName)));
            this.sender.sendPacket(source, new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.ADD, WaypointServerCore.EDITION));
        });
    }

    private void executeList(S source) {
        Map<String, WaypointFileManager> fileManagerMap = WaypointServerCore.INSTANCE.getFileManagerMap();
        Component listMsg = Component.text("");
        listMsg = listMsg.appendNewline();
        boolean empty = true;
        for (String dimensionName : fileManagerMap.keySet()) {
            // Dimension header
            WaypointFileManager waypointFileManager = fileManagerMap.get(dimensionName);
            if (waypointFileManager == null) {
                continue;
            }
            if (waypointFileManager.isEmpty()) {
                continue;
            }
            listMsg = listMsg.append(dimensionNameWithColor(dimensionName)).appendNewline();
            Map<String, WaypointList> lists = waypointFileManager.getWaypointListMap();
            for (Map.Entry<String, WaypointList> listEntry : lists.entrySet()) {
                String listName = listEntry.getKey();
                Component listNameText = Component.text("  " + listName);
                listMsg = listMsg.append(listNameText).appendNewline();
                // Waypoints
                WaypointList list = listEntry.getValue();
                for (SimpleWaypoint waypoint : list.simpleWaypoints()) {
                    Component waypointText = Component.text("    ")
                            .append(editButton(dimensionName, listName, waypoint))
                            .append(Component.text(" "))
                            .append(removeButton(dimensionName, listName, waypoint))
                            .append(Component.text(" "))
                            .append(defaultWaypointText(waypoint, dimensionName, listName));
                    listMsg = listMsg.append(waypointText).appendNewline();
                }
            }
            empty = false;
        }
        if (empty) {
            this.sender.sendMessage(source, Component.translatable("waypoint.no.waypoints"));
        } else {
            this.sender.sendMessage(source, listMsg);
        }
    }

    private void executeReload(S source) {
        executeByServer(source, () -> {
            WaypointServerCore.INSTANCE.reload();
            String[] lang = getLoadedLanguages().toArray(new String[0]);
            this.sender.sendMessage(source, Component.translatable("waypoint.loaded.languages",
                    Component.text(lang.length), Component.text(String.join(" ", lang))));
        });
        this.sender.sendMessage(source, Component.translatable("waypoint.reload"));
    }

    private void saveChanges(S source, WaypointFileManager fileManager) {
        WaypointServerCore.EDITION++;
        executeByServer(source, () -> {
            try {
                fileManager.saveDimension();
            } catch (IOException e) {
                this.sender.sendError(source, Component.translatable("waypoint.save.failed", Component.text(fileManager.getDimensionFile().toString())));
                throw new RuntimeException(e);
            }
            try {
                WaypointServerCore.INSTANCE.saveEdition();
            } catch (IOException e) {
                this.sender.sendError(source, Component.translatable("edition.save.failed"));
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

    private class WaypointListSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            D dimension = getDefaultDimension(context.getLastChild());
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(toDimensionName(dimension));
            if (fileManager == null) {
                return Suggestions.empty();
            } else {
                for (String listName : fileManager.getWaypointListMap().keySet()) {
                    builder.suggest(listName);
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
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(toDimensionName(dimension));
            if (fileManager == null) {
                return Suggestions.empty();
            }
            WaypointList waypointList = fileManager.getWaypointListByName(getString(currentContext, LIST_NAME_ARG));
            if (waypointList == null) {
                return Suggestions.empty();
            } else {
                for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                    builder.suggest(waypoint.name());
                }
                return builder.buildFuture();
            }
        }
    }

    private class NameInitialsSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            String name = getString(context.getLastChild(), WAYPOINT_NAME_ARG);
            if (name.isEmpty()) {
                return Suggestions.empty();
            }
            builder.suggest(name.toUpperCase().substring(0, 1));
            if (name.length() > 1) {
                builder.suggest(name.substring(0, 2).toUpperCase());
            }
            return builder.buildFuture();
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

    static {
        WAYPOINT_COMMAND = "wp";
        ADD_COMMAND = "add";
        EDIT_COMMAND = "edit";
        REMOVE_COMMAND = "remove";
        LIST_COMMAND = "list";
        DOWNLOAD_COMMAND = "download";
        TP_COMMAND = "tp";
        RELOAD_COMMAND = "reload";
        DIMENSION_ARG = "dimension";
        LIST_NAME_ARG = "list name";
        WAYPOINT_NAME_ARG = "waypoint name";
        INITIALS_ARG = "initials";
        POS_ARG = "position";
        YAW_ARG = "yaw";
        COLOR_ARG = "color";
        VISIBILITY_ARG = "global";
    }
}
