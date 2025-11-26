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
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.text.TextButton.*;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.ColorUtils.*;
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
    private final PermissionKeys<K> permissionKeys;
    private final PermissionManager<S, K, P> permissionManager;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final Supplier<ArgumentType<B>> blockPosArgumentProvider;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();
    private final SuggestionProvider<S> HEX_COLOR_CODE_SUGGESTION = new HexColorCodeSuggestion();
    private boolean enabled = true;
    public static final String SINGLE_WORD_REGEX = "^[a-zA-Z0-9+._-]+$";
    public static final String WAYPOINT_COMMAND = "wp";
    public static final String ADD_COMMAND = "add";
    public static final String EDIT_COMMAND = "edit";
    public static final String REMOVE_COMMAND = "remove";
    public static final String LIST_COMMAND = "list";
    public static final String DOWNLOAD_COMMAND = "download";
    public static final String TP_COMMAND = "tp";
    public static final String RELOAD_COMMAND = "reload";
    public static final String DIMENSION_ARG = "dimension";
    public static final String LIST_NAME_ARG = "list name";
    public static final String WAYPOINT_NAME_ARG = "waypoint name";
    public static final String INITIALS_ARG = "initials";
    public static final String POS_ARG = "position";
    public static final String YAW_ARG = "yaw";
    public static final String COLOR_ARG = "color";
    public static final String VISIBILITY_ARG = "global";

    public CoreWaypointCommand(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissionManager, Supplier<ArgumentType<D>> dimensionArgument, Supplier<ArgumentType<B>> blockPositionArgument) {
        this.dimensionArgumentProvider = dimensionArgument;
        this.blockPosArgumentProvider = blockPositionArgument;
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.permissionKeys = permissionManager.keys;
    }

    protected abstract String toDimensionName(D dimensionArgument);
    protected abstract WaypointPos toWaypointPos(S source, B blockPositionArgument);
    protected abstract boolean isDimensionValid(S source, D dimensionArgument);
    protected abstract void executeByServer(S source, Runnable task);
    protected abstract D getSourceDimension(S source);
    protected abstract float getSourceYaw(S source);
    protected abstract P getPlayer(S source);
    protected abstract String getPlayerName(P player);
    protected abstract void teleportPlayer(S source, P player, D dimensionArgument, WaypointPos pos, int yaw);
    protected abstract Message getMessageFromComponent(Component component);

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }

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
        return (CommandNode<S>) argument(INITIALS_ARG, string())
                .suggests((SuggestionProvider<Object>) NAME_INITIALS_SUGGESTION)
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
                .then(literal(LIST_COMMAND)
                        .then(literal("all")
                                .executes(
                                        context -> {
                                            executeListAll((S) context.getSource());
                                            return Command.SINGLE_SUCCESS;
                                        }
                                )
                        )
                        .then((ArgumentBuilder<Object, ?>) dimensionNode(
                                )
                                .then(listNameNode()
                                        .executes(
                                                context -> {
                                                    executeListWaypointList(
                                                            context.getSource(),
                                                            getArgument(context, DIMENSION_ARG),
                                                            getString(context, LIST_NAME_ARG)
                                                    );
                                                    return Command.SINGLE_SUCCESS;
                                                }
                                        )
                                )
                                .executes(
                                        context -> {
                                            executeListDimension(
                                                    context.getSource(),
                                                    getArgument(context, DIMENSION_ARG)
                                            );
                                            return Command.SINGLE_SUCCESS;
                                        }
                                )
                        )
                        .executes(
                                context -> {
                                    executeListCurrentDimension((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                }
                        )
                )
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
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
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
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                fileManager = WaypointServerCore.INSTANCE.addWaypointFileManager(dimensionName);
            }
            WaypointList foundList = fileManager.getWaypointListByName(listName);
            if (foundList == null) {
                fileManager.addWaypointList(WaypointList.build(listName));
                this.sender.sendMessage(source, translatable("waypoint.add.list.success", text(listName), dimensionNameWithColor(dimensionName)));
                saveChanges(source, fileManager);
                return;
            }
            this.sender.sendError(source, translatable("waypoint.add.list.exists", text(listName)));
        }
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, String hexCode, boolean global) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (isDimensionValid(source, dimensionArgument)) {
            WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
            if (waypointPos == null) {
                sendPosArgumentError(source);
                return;
            }
            int rgb = colorNameOrHexCodeToRgb(hexCode, false);
            if (rgb < 0) {
                sendHexColorCodeError(source, hexCode);
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
                    rgb,
                    yaw,
                    global
            );
            SimpleWaypoint waypointFound = waypointList.getWaypointByName(name);
            if (waypointFound == null) {
                waypointList.add(newWaypoint);
            } else {
                this.sender.sendMessage(source,
                        translatable("waypoint.add.exists",
                                waypointTextWithTp(waypointFound, dimensionName, listName),
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
                    translatable("waypoint.add.success",
                            waypointTextWithTp(newWaypoint, dimensionName, listName),
                            text(listName)
                    )
            );
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void executeEdit(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, String hexCode, boolean global) {
        WaypointPos waypointPos = toWaypointPos(source, blockPosArgument);
        if (waypointPos == null) {
            sendPosArgumentError(source);
        }
        int rgb = colorNameOrHexCodeToRgb(hexCode, false);
        if (rgb < 0) {
            sendHexColorCodeError(source, hexCode);
        } else {
            runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
                if (waypoint.compareProperties(initials, waypointPos, rgb, yaw, global)) {
                    this.sender.sendMessage(source, translatable("waypoint.edit.identical", text(name)));
                    return;
                } else {
                    waypoint.setInitials(initials);
                    waypoint.setPos(waypointPos);
                    waypoint.setRgb(rgb);
                    waypoint.setYaw(yaw);
                    waypoint.setGlobal(global);
                }
                saveChanges(source, fileManager);
                String dimensionName = fileManager.getDimensionName();
                WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.UPDATE, WaypointServerCore.EDITION);
                this.sender.broadcastWaypointModification(source, buffer);
                this.sender.sendMessage(source, translatable("waypoint.edit.success", waypointTextWithTp(waypoint, dimensionName, listName)));
            });
        }
    }

    private void executeRemoveList(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) ->
                        this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", text(listName))),
                (fileManager, waypointList) -> {
                    fileManager.removeWaypointListByName(listName);
                    this.sender.sendMessage(source, translatable("waypoint.remove.list.success", text(listName)));
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
        WorldWaypointBuffer buffer = WaypointServerCore.INSTANCE.toWorldWaypointBuffer();
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
            this.sender.sendPacket(source, new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.ADD, WaypointServerCore.EDITION));
        });
    }

    private void executeListAll(S source) {
        Map<String, WaypointFileManager> fileManagerMap = WaypointServerCore.INSTANCE.getFileManagerMap();
        Component listMsg = text("");
        listMsg = listMsg.appendNewline();
        boolean empty = true;
        boolean withEdit = hasEditPermission(source);
        boolean withRemove = hasRemovePermission(source);
        boolean withTp = hasTpPermission(source);
        for (String dimensionName : fileManagerMap.keySet()) {
            // Dimension header
            WaypointFileManager waypointFileManager = fileManagerMap.get(dimensionName);
            if (waypointFileManager == null) {
                continue;
            }
            if (waypointFileManager.isEmpty()) {
                continue;
            }
            listMsg = listMsg.append(getDimensionListText(waypointFileManager, withEdit, withRemove, withTp));
            empty = false;
        }
        if (empty) {
            this.sender.sendMessage(source, translatable("waypoint.no.waypoints"));
        } else {
            this.sender.sendMessage(source, listMsg);
        }
    }

    private void executeListCurrentDimension(S source) {
         executeListDimension(source, getSourceDimension(source));
    }

    private void executeListDimension(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendMessage(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
            } else {
                this.sender.sendMessage(source,
                        getDimensionListText(fileManager,
                                hasEditPermission(source), hasRemovePermission(source), hasTpPermission(source)));
            }
        });
    }

    private void executeListWaypointList(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> {
                    String dimensionName = fileManager.getDimensionName();
                    this.sender.sendMessage(source,
                            getWaypointListText(waypointList, dimensionName, 0,
                                    hasEditPermission(source),
                                    hasRemovePermission(source),
                                    hasTpPermission(source)));
                },
                (fileManager, waypointList) ->
                    this.sender.sendMessage(source, translatable("waypoint.empty.list", text(listName)))
                );
    }

    private void executeReload(S source) {
        executeByServer(source, () -> {
            WaypointServerCore.INSTANCE.reload();
            List<String> lang = getExternalLoadedLanguages();
            this.sender.sendMessage(source, translatable("waypoint.loaded.languages",
                    text(lang.size()), text(String.join(", ", lang))));
        });
        this.sender.sendMessage(source, translatable("waypoint.reload"));
    }

    private void saveChanges(S source, WaypointFileManager fileManager) {
        WaypointServerCore.EDITION++;
        executeByServer(source, () -> {
            try {
                fileManager.saveDimension();
            } catch (IOException e) {
                this.sender.sendError(source, translatable("waypoint.save.failed", text(fileManager.getDimensionFile().toString())));
                throw new RuntimeException(e);
            }
            try {
                WaypointServerCore.INSTANCE.saveEdition();
            } catch (IOException e) {
                this.sender.sendError(source, translatable("edition.save.failed"));
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
                    if (listName.matches(SINGLE_WORD_REGEX)) {
                        builder.suggest(listName);
                    } else {
                        builder.suggest("\"%s\"".formatted(listName));
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
            WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(toDimensionName(dimension));
            if (fileManager == null) {
                return Suggestions.empty();
            }
            WaypointList waypointList = fileManager.getWaypointListByName(getString(currentContext, LIST_NAME_ARG));
            if (waypointList == null) {
                return Suggestions.empty();
            } else {
                for (SimpleWaypoint waypoint : waypointList.simpleWaypoints()) {
                    String name = waypoint.name();
                    if (name.matches(SINGLE_WORD_REGEX)) {
                        builder.suggest(name);
                    } else {
                        builder.suggest("\"%s\"".formatted(name));
                    }
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
            if (name.matches(SINGLE_WORD_REGEX)) {
                builder.suggest(name.toUpperCase().substring(0, 1));
                if (name.length() > 1) {
                    builder.suggest(name.substring(0, 2).toUpperCase());
                }
            } else  {
                builder.suggest("\"%s\"".formatted(name.substring(0, 1)));
                if (name.length() > 1) {
                    builder.suggest("\"%s\"".formatted(name.substring(0, 2)));
                }
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

    public class HexColorCodeSuggestion implements SuggestionProvider<S> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            String currentInput = builder.getRemaining();
            boolean startWithSingleQuote = currentInput.startsWith("'");
            boolean startWithDoubleQuote = currentInput.startsWith("\"");
            if (startWithSingleQuote || startWithDoubleQuote) {
                currentInput = currentInput.substring(1);
                if (currentInput.endsWith("'") || currentInput.endsWith("\"")) {
                    currentInput = currentInput.substring(0, currentInput.length() - 1);
                }
            }
            if (currentInput.isEmpty()) {
                vanillaColorSuggestions(builder);
                builder.suggest("39C5BB", getMessageFromComponent(text("miku", TextColor.color(0x39C5BB))));
            } else {
                for (int i = 0; i < VANILLA_COLOR_NAMES.length; i++) {
                    String colorName = VANILLA_COLOR_NAMES[i];
                    if (colorName.startsWith(currentInput)) {
                        builder.suggest(colorName, getHexColorCodeTooltip(VANILLA_COLOR_CODES[i], VANILLA_COLORS[i]));
                    }
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
