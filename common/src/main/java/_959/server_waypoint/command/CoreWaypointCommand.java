package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.buffer.WaypointModificationBuffer;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.TextButton;
import _959.server_waypoint.util.TriConsumer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.text.TextButton.restoreButton;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.builder.LiteralArgumentBuilder.literal;
import static com.mojang.brigadier.builder.RequiredArgumentBuilder.argument;

public abstract class CoreWaypointCommand<S, C, P, D, K> {
    private static final String WAYPOINT_COMMAND = "wp";
    private static final String ADD_COMMAND = "add";
    private static final String EDIT_COMMAND = "edit";
    private static final String REMOVE_COMMAND = "remove";
    private static final String LIST_COMMAND = "list";
    private static final String DOWNLOAD_COMMAND = "download";
    private static final String TP_COMMAND = "tp";
    private static final String DIMENSION_ARG = "dimension";
    private static final String LIST_NAME_ARG = "list";
    private static final String WAYPOINT_NAME_ARG = "name";
    private static final String INITIALS_ARG = "initials";
    private static final String POS_ARG = "pos";
    private static final String YAW_ARG = "yaw";
    private static final String COLOR_ARG = "color";
    private static final String VISIBILITY_ARG = "global";
    protected final PlatformMessageSender<S, ?, ?> sender;
    private final PermissionManager<S, K> permissionManager;
    private final PermissionKeys<K> permissionKeys;
    private final Supplier<ArgumentType<C>> colorArgumentProvider;
    private final Supplier<ArgumentType<P>> blockPosArgumentProvider;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();

    public CoreWaypointCommand(PlatformMessageSender<S, ?, ?> sender, PermissionManager<S, K> permissionManager, Supplier<ArgumentType<D>> dimensionArgument, Supplier<ArgumentType<P>> blockPositionArgument, Supplier<ArgumentType<C>> colorArgument) {
        this.dimensionArgumentProvider = dimensionArgument;
        this.blockPosArgumentProvider = blockPositionArgument;
        this.colorArgumentProvider = colorArgument;
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.permissionKeys = permissionManager.keys;
    }

    protected abstract String toDimensionName(D dimensionArgument);
    protected abstract WaypointPos toWaypointPos(S source, P blockPositionArgument);
    protected abstract int toColorIdx(C colorArgument);
    protected abstract boolean isDimensionValid(S source, D dimensionArgument);
    protected abstract void executeByServer(S source, Runnable task);
    protected abstract D getSourceDimension(S source);
    protected abstract float getPlayerYaw(S source);

    @SuppressWarnings("unchecked")
    private <T> T getArgument(CommandContext<S> context, String name) {
        return context.getArgument(name, (Class<T>) Object.class);
    }

    @SuppressWarnings("unchecked")
    private CommandNode<S> selectorArguments(Command<S> command) {
        return (CommandNode<S>) argument(DIMENSION_ARG, this.dimensionArgumentProvider.get())
                .then(argument(LIST_NAME_ARG, string())
                        .suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)
                        .then(argument(WAYPOINT_NAME_ARG, string())
                                .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                .executes((Command<Object>) command)
                        )
                ).build();
    }

    @SuppressWarnings("unchecked")
    private CommandNode<S> selectorArguments(CommandNode<S> node) {
        return (CommandNode<S>) argument(DIMENSION_ARG, this.dimensionArgumentProvider.get())
                .then(argument(LIST_NAME_ARG, string())
                        .suggests((SuggestionProvider<Object>) WAYPOINT_LIST_SUGGESTION)
                        .then(argument(WAYPOINT_NAME_ARG, string())
                                .suggests((SuggestionProvider<Object>) WAYPOINT_NAME_SUGGESTION)
                                .then((CommandNode<Object>) node)
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

    @SuppressWarnings("unchecked")
    public @NotNull LiteralCommandNode<S> buildCore() {
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
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
                                                                                            return 1;
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
                                            return 1;
                                        })
                                )
                        )
                )
                .then(literal(REMOVE_COMMAND)
                        .requires(source -> this.permissionManager.hasPermission((S) source, permissionKeys.remove(), CONFIG.CommandPermission().remove()))
                        .then((CommandNode<Object>)
                                selectorArguments(
                                        context -> {
                                            executeRemove(
                                                    context.getSource(),
                                                    getArgument(context, DIMENSION_ARG),
                                                    getString(context, LIST_NAME_ARG),
                                                    getString(context, WAYPOINT_NAME_ARG)
                                            );
                                            return 1;
                                        }
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
                                            return 1;
                                        }
                                )
                        )
                )
                .then(literal(DOWNLOAD_COMMAND)
                        .then((CommandNode<Object>)
                                selectorArguments(
                                        context -> {
                                            executeDownload(
                                                    context.getSource(),
                                                    getArgument(context, DIMENSION_ARG),
                                                    getString(context, LIST_NAME_ARG),
                                                    getString(context, WAYPOINT_NAME_ARG)
                                            );
                                            return 1;
                                        }
                                )
                        )
                )
                .then(literal(LIST_COMMAND)
                        .executes(
                                context -> {
                                    executeList((S) context.getSource());
                                    return 1;
                                }
                        )
                )
                .build();
    }

    private void sendDimensionError(S source, String dimensionName) {
        this.sender.sendError(source, Component.translatable("argument.dimension.invalid", Component.text(dimensionName)));
    }

    private void sendPosArgumentError(S source) {
        this.sender.sendError(source, Component.translatable("argument.pos.invalid"));
    }

    private void executeAddWaypointList(S source, D dimensionArgument, String listName) {
        String dimensionName = toDimensionName(dimensionArgument);
        WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            this.sender.sendError(source, Component.translatable("argument.waypoint.invalid"));
        } else {
            if (fileManager.getWaypointListByName(listName) != null) {
                source.sendError(text("List: %s already exists.".formatted(listName)));
                return;
            }
            fileManager.addWaypointList(WaypointList.build(listName));
            source.sendFeedback(() -> {
                MutableText feedback = text("Add waypoint list %s under dimension: ".formatted(listName));
                feedback.append(text(dimString).setStyle(Style.EMPTY.withColor(getDimensionColor(dimKey))));
                return feedback;
            }, true);
            saveChanges(source, fileManager);
        }
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, P blockPosArgument, int yaw, C color, boolean global) {
        int colorIdx = toColorIdx(color);
        String dimensionName = toDimensionName(dimensionArgument);
        if  (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
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
                            defaultWaypointText(waypointFound, dimensionName),
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
                        defaultWaypointText(newWaypoint, dimensionName),
                        Component.text(listName)
                )
        );
    }

    private boolean runWithSelectorTarget(S source, D dimensionArgument, String listName, Consumer<WaypointFileManager> action) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return false;
        }
        WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            this.sender.sendError(source, Component.translatable("waypoint.nonexist.dimension", Component.text(dimensionName)));
            return false;
        } else if (fileManager.isEmpty()) {
            this.sender.sendError(source, Component.translatable("waypoint.edit.empty.dimension", Component.text(dimensionName)));
            return false;
        } else {
            action.accept(fileManager);
            return true;
        }
    }

    private boolean runWithSelectorTarget(S source, D dimensionArgument, String listName, BiConsumer<WaypointFileManager, WaypointList> action) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return false;
        }
        WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            this.sender.sendError(source, Component.translatable("waypoint.nonexist.dimension", Component.text(dimensionName)));
            return false;
        } else if (fileManager.isEmpty()) {
            this.sender.sendError(source, Component.translatable("waypoint.edit.empty.dimension", Component.text(dimensionName)));
            return false;
        } else {
            WaypointList waypointList = fileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                this.sender.sendError(source, Component.translatable("waypoint.nonexist.list", Component.text(listName)));
                return false;
            } else if (waypointList.isEmpty()) {
                this.sender.sendError(source, Component.translatable("waypoint.edit.empty.list", Component.text(listName)));
                return false;
            } else {
                action.accept(fileManager, waypointList);
                return true;
            }
        }
    }

    private boolean runWithSelectorTarget(S source, D dimensionArgument, String listName, String name, TriConsumer<WaypointFileManager, WaypointList, SimpleWaypoint> action) {
        String dimensionName = toDimensionName(dimensionArgument);
        if  (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return false;
        }
        WaypointFileManager fileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
        if (fileManager == null) {
            this.sender.sendError(source, Component.translatable("waypoint.nonexist.dimension", Component.text(dimensionName)));
            return false;
        } else if (fileManager.isEmpty()) {
            this.sender.sendError(source, Component.translatable("waypoint.edit.empty.dimension", Component.text(dimensionName)));
            return false;
        } else {
            WaypointList waypointList = fileManager.getWaypointListByName(listName);
            if (waypointList == null) {
                this.sender.sendError(source, Component.translatable("waypoint.nonexist.list", Component.text(listName)));
                return false;
            } else if (waypointList.isEmpty()) {
                this.sender.sendError(source, Component.translatable("waypoint.edit.empty.list", Component.text(listName)));
                return false;
            } else {
                SimpleWaypoint waypoint = waypointList.getWaypointByName(name);
                if (waypoint == null) {
                    this.sender.sendError(source, Component.translatable("waypoint.nonexist.waypoint", Component.text(name)));
                    return false;
                } else {
                    action.accept(fileManager, waypointList, waypoint);
                    return true;
                }
            }
        }
    }

    private void executeEdit(S source, D dimensionArgument, String listName, String name, String initials, P blockPosArgument, int yaw, C color, boolean global) {
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
            this.sender.sendMessage(source, Component.translatable("waypoint.edit.success", defaultWaypointText(waypoint, dimensionName)));
        });
    }

    private void executeRemove(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
           waypointList.remove(waypoint);
           saveChanges(source, fileManager);
           String dimensionName = fileManager.getDimensionName();
            WaypointModificationBuffer buffer = new WaypointModificationBuffer(dimensionName, listName, waypoint, WaypointModificationType.REMOVE, WaypointServerCore.EDITION);
            this.sender.broadcastWaypointModification(source, buffer);
            this.sender.sendMessage(source, Component.translatable("waypoint.remove.success", defaultWaypointText(waypoint, dimensionName), restoreButton(dimensionName, listName, waypoint)));
        });
    }

    private void executeTp(S source, D dimensionArgument, String listName, String name) {

    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {

    }

    private void executeList(S source) {

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
        dispatcher.getRoot().addChild(buildCore());
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
            float yaw = getPlayerYaw(context.getSource());
            builder.suggest(Math.round(yaw));
            if (yaw != 0f) {
                builder.suggest(0);
            }
            return builder.buildFuture();
        }
    }
}
