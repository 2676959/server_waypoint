package _959.server_waypoint.command;

import _959.server_waypoint.command.permission.PermissionKeys;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.network.ChunkedMessage;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.message.WaypointListUpdateMessage;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointListDisplayModel;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.network.upload.UploadConflictPolicy;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.network.upload.UploadScope;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.core.restore.WaypointRestoreRegistry;
import _959.server_waypoint.text.TextButtonBuilder;
import _959.server_waypoint.util.StringCommandBuilder.ListOptions;
import _959.server_waypoint.util.StringCommandBuilder.ListTarget;
import _959.server_waypoint.util.TriConsumer;
import _959.server_waypoint.util.WaypointInitials;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.waypoint.WaypointList.SERVER_N;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.ADD_LIST;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.REMOVE_LIST;
import static _959.server_waypoint.text.TextButtonBuilder.*;
import static _959.server_waypoint.text.WaypointTextHelper.*;
import static _959.server_waypoint.text.FormattedTextHelper.*;
import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.ColorUtils.*;
import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;
import static _959.server_waypoint.util.StringCommandBuilder.listDimensionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.listWaypointListCmd;
import static _959.server_waypoint.util.WaypointInitials.SINGLE_WORD_REGEX;
import static com.mojang.brigadier.arguments.BoolArgumentType.bool;
import static com.mojang.brigadier.arguments.BoolArgumentType.getBool;
import static com.mojang.brigadier.arguments.FloatArgumentType.floatArg;
import static com.mojang.brigadier.arguments.FloatArgumentType.getFloat;
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
    private final NavigationService<P> navigationService;
    private final WaypointRestoreRegistry<String> restoreRegistry;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final Supplier<ArgumentType<B>> blockPosArgumentProvider;
    private final UploadCoordinator<P> uploadCoordinator;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();
    private final SuggestionProvider<S> HEX_COLOR_CODE_SUGGESTION = new HexColorCodeSuggestion();
    public static final String WAYPOINT_COMMAND = "wp";
    public static final String HELP_COMMAND = "help";
    public static final String ADD_COMMAND = "add";
    public static final String EDIT_COMMAND = "edit";
    public static final String DETAILS_COMMAND = "details";
    public static final String RESTORE_COMMAND = "restore";
    public static final String SET_COMMAND = "set";
    public static final String CLEAR_COMMAND = "clear";
    public static final String LIST_TARGET = "list";
    public static final String WAYPOINT_TARGET = "waypoint";
    public static final String REMOVE_COMMAND = "remove";
    public static final String LIST_COMMAND = "list";
    public static final String DOWNLOAD_COMMAND = "download";
    public static final String UPLOAD_COMMAND = "upload";
    public static final String UPLOAD_FORCE_COMMAND = "force";
    public static final String UPLOAD_SERVER_COMMAND = "server";
    public static final String UPLOAD_LOCAL_COMMAND = "local";
    public static final String UPLOAD_DELETE_COMMAND = "delete";
    public static final String TP_COMMAND = "tp";
    public static final String RELOAD_COMMAND = "reload";
    public static final String NAVIGATE_COMMAND = "navigate";
    public static final String USE_COMMAND = "use";
    public static final String DISABLE_COMMAND = "disable";
    public static final String STATUS_COMMAND = "status";
    public static final String TRANSFORMATION_COMMAND = "transformation";
    public static final String RESET_COMMAND = "reset";
    public static final String SEARCH_COMMAND = "search";
    public static final String SORT_COMMAND = "sort";
    public static final String ORDER_COMMAND = "order";
    public static final String PAGE_COMMAND = "page";
    public static final String LIMIT_COMMAND = "limit";
    public static final String VIEW_COMMAND = "view";
    public static final String TREE_VIEW = "tree";
    public static final String FLAT_VIEW = "flat";
    public static final String CONFIG_LITERAL_NODE = "config";
    public static final String DIMENSION_ARG = "dimension";
    public static final String LIST_NAME_ARG = "list identifier";
    public static final String WAYPOINT_NAME_ARG = "waypoint identifier";
    public static final String VALUE_ARG = "value";
    public static final String TOKEN_ARG = "token";
    public static final String INITIALS_ARG = "initials";
    public static final String POS_ARG = "position";
    public static final String YAW_ARG = "yaw";
    public static final String COLOR_ARG = "color";
    public static final String VISIBILITY_ARG = "global";
    public static final String KEYWORDS_ARG = "keywords";
    public static final String DESCRIPTION_ARG = "description";
    public static final String SEARCH_QUERY_ARG = "search query";
    public static final String PAGE_NUMBER_ARG = "page number";
    public static final String PAGE_LIMIT_ARG = "page limit";
    public static final String TRANSLATION_X_ARG = "translation x";
    public static final String TRANSLATION_Y_ARG = "translation y";
    public static final String TRANSLATION_Z_ARG = "translation z";
    public static final String ROTATION_X_ARG = "rotation x";
    public static final String ROTATION_Y_ARG = "rotation y";
    public static final String ROTATION_Z_ARG = "rotation z";
    public static final String SCALE_X_ARG = "scale x";
    public static final String SCALE_Y_ARG = "scale y";
    public static final String SCALE_Z_ARG = "scale z";
    public static final String RANDOM_COLOR = "random";
    public static final int MAX_PAGE_LIMIT = Config.MAX_PAGE_LIMIT;

    public CoreWaypointCommand(
            WaypointServerCore waypointServer,
            PlatformMessageSender<S, P> sender,
            PermissionManager<S, K, P> permissionManager,
            NavigationService<P> navigationService,
            UploadCoordinator<P> uploadCoordinator,
            Supplier<ArgumentType<D>> dimensionArgument,
            Supplier<ArgumentType<B>> blockPositionArgument
    ) {
        this.waypointServer = waypointServer;
        this.waypointQueryEngine = new WaypointQueryEngine(waypointServer);
        this.sender = sender;
        this.permissionManager = permissionManager;
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.restoreRegistry = new WaypointRestoreRegistry<>();
        this.uploadCoordinator = Objects.requireNonNull(uploadCoordinator, "uploadCoordinator");
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
    protected abstract @Nullable P getPlayer(S source);
    protected abstract String getPlayerName(P player);
    protected abstract void teleportPlayer(S source, P player, D dimensionArgument, WaypointPos pos, int yaw);
    protected abstract Message getMessageFromComponent(Component component);
    protected abstract List<String> getAvailableDimensionNames(S source);

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

    private boolean hasNavigatePermission(S source) {
        return this.permissionManager.hasPermission(
                source,
                this.permissionKeys.navigate(),
                CONFIG.CommandPermission().navigate()
        );
    }

    private boolean hasUploadPermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.upload(), CONFIG.CommandPermission().upload());
    }

    private boolean hasUploadDeletePermission(S source) {
        return this.permissionManager.hasPermission(source, this.permissionKeys.uploadDelete(), CONFIG.CommandPermission().uploadDelete());
    }

    @SuppressWarnings("unchecked")
    private <T> T getArgument(CommandContext<S> context, String name) {
        return context.getArgument(name, (Class<T>) Object.class);
    }

    private boolean hasArgument(CommandContext<S> context, String name) {
        try {
            getArgument(context, name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
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

    private RequiredArgumentBuilder<Object, Boolean> extraInfoArguments(Command<Object> command) {
        RequiredArgumentBuilder<Object, Boolean> visibilityNode = argument(VISIBILITY_ARG, bool());
        visibilityNode.executes(command);
        RequiredArgumentBuilder<Object, String> keywordsNode = argument(KEYWORDS_ARG, string());
        keywordsNode.executes(command);
        RequiredArgumentBuilder<Object, String> descriptionNode = argument(DESCRIPTION_ARG, string());
        descriptionNode.executes(command);
        keywordsNode.then(descriptionNode);
        visibilityNode.then(keywordsNode);
        return visibilityNode;
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

    private LiteralArgumentBuilder<S> detailsCommandNode() {
        LiteralArgumentBuilder<S> root = literal(DETAILS_COMMAND);
        RequiredArgumentBuilder<S, D> listDimension = argument(
                DIMENSION_ARG,
                this.dimensionArgumentProvider.get()
        );
        listDimension.then(this.stringArgument(LIST_NAME_ARG, this.WAYPOINT_LIST_SUGGESTION)
                .executes(context -> {
                    this.executeListDetails(
                            context.getSource(),
                            this.getArgument(context, DIMENSION_ARG),
                            getString(context, LIST_NAME_ARG)
                    );
                    return Command.SINGLE_SUCCESS;
                }));
        root.then(LiteralArgumentBuilder.<S>literal(LIST_TARGET).then(listDimension));

        RequiredArgumentBuilder<S, D> waypointDimension = argument(
                DIMENSION_ARG,
                this.dimensionArgumentProvider.get()
        );
        RequiredArgumentBuilder<S, String> list = this.stringArgument(
                LIST_NAME_ARG,
                this.WAYPOINT_LIST_SUGGESTION
        );
        list.then(this.stringArgument(WAYPOINT_NAME_ARG, this.WAYPOINT_NAME_SUGGESTION)
                .executes(context -> {
                    this.executeWaypointDetails(
                            context.getSource(),
                            this.getArgument(context, DIMENSION_ARG),
                            getString(context, LIST_NAME_ARG),
                            getString(context, WAYPOINT_NAME_ARG)
                    );
                    return Command.SINGLE_SUCCESS;
                }));
        waypointDimension.then(list);
        root.then(LiteralArgumentBuilder.<S>literal(WAYPOINT_TARGET).then(waypointDimension));
        return root;
    }

    private LiteralArgumentBuilder<S> editCommandNode() {
        LiteralArgumentBuilder<S> root = literal(EDIT_COMMAND);
        root.requires(this::hasEditPermission);
        root.then(this.listEditTargetNode());
        root.then(this.waypointEditTargetNode());
        return root;
    }

    private LiteralArgumentBuilder<S> listEditTargetNode() {
        LiteralArgumentBuilder<S> target = literal(LIST_TARGET);
        RequiredArgumentBuilder<S, D> dimension = argument(
                DIMENSION_ARG,
                this.dimensionArgumentProvider.get()
        );
        RequiredArgumentBuilder<S, String> list = this.stringArgument(
                LIST_NAME_ARG,
                this.WAYPOINT_LIST_SUGGESTION
        );
        LiteralArgumentBuilder<S> set = literal(SET_COMMAND);
        set.then(this.listStringPatchNode("identifier", false));
        set.then(this.listStringPatchNode("display-name", true));
        LiteralArgumentBuilder<S> clear = literal(CLEAR_COMMAND);
        clear.then(LiteralArgumentBuilder.<S>literal("display-name").executes(context -> {
            this.executeListPatch(
                    context.getSource(),
                    this.getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    new WaypointListPatch(PatchField.unchanged(), PatchField.clear())
            );
            return Command.SINGLE_SUCCESS;
        }));
        list.then(set).then(clear);
        dimension.then(list);
        target.then(dimension);
        return target;
    }

    private LiteralArgumentBuilder<S> listStringPatchNode(String property, boolean displayName) {
        LiteralArgumentBuilder<S> propertyNode = literal(property);
        propertyNode.then(RequiredArgumentBuilder.<S, String>argument(VALUE_ARG, string()).executes(context -> {
            PatchField<String> value = PatchField.set(getString(context, VALUE_ARG));
            this.executeListPatch(
                    context.getSource(),
                    this.getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    displayName
                            ? new WaypointListPatch(PatchField.unchanged(), value)
                            : new WaypointListPatch(value, PatchField.unchanged())
            );
            return Command.SINGLE_SUCCESS;
        }));
        return propertyNode;
    }

    private LiteralArgumentBuilder<S> waypointEditTargetNode() {
        LiteralArgumentBuilder<S> target = literal(WAYPOINT_TARGET);
        RequiredArgumentBuilder<S, D> dimension = argument(
                DIMENSION_ARG,
                this.dimensionArgumentProvider.get()
        );
        RequiredArgumentBuilder<S, String> list = this.stringArgument(
                LIST_NAME_ARG,
                this.WAYPOINT_LIST_SUGGESTION
        );
        RequiredArgumentBuilder<S, String> waypoint = this.stringArgument(
                WAYPOINT_NAME_ARG,
                this.WAYPOINT_NAME_SUGGESTION
        );
        LiteralArgumentBuilder<S> set = literal(SET_COMMAND);
        set.then(this.waypointStringPatchNode("identifier", WaypointPatchProperty.IDENTIFIER));
        set.then(this.waypointStringPatchNode("display-name", WaypointPatchProperty.DISPLAY_NAME));
        set.then(this.waypointStringPatchNode("initials", WaypointPatchProperty.INITIALS));
        set.then(LiteralArgumentBuilder.<S>literal("position")
                .then(RequiredArgumentBuilder.<S, B>argument(POS_ARG, this.blockPosArgumentProvider.get())
                        .executes(context -> {
                            WaypointPos position = this.toWaypointPos(
                                    context.getSource(),
                                    this.getArgument(context, POS_ARG)
                            );
                            if (position == null) {
                                this.sendPosArgumentError(context.getSource());
                            } else {
                                this.executeWaypointPatch(
                                        context.getSource(),
                                        this.getArgument(context, DIMENSION_ARG),
                                        getString(context, LIST_NAME_ARG),
                                        getString(context, WAYPOINT_NAME_ARG),
                                        patchWithPosition(position)
                                );
                            }
                            return Command.SINGLE_SUCCESS;
                        })));
        set.then(LiteralArgumentBuilder.<S>literal("color")
                .then(RequiredArgumentBuilder.<S, String>argument(COLOR_ARG, string())
                        .suggests(this.HEX_COLOR_CODE_SUGGESTION)
                        .executes(context -> {
                            String input = getString(context, COLOR_ARG);
                            int color = colorNameOrHexCodeToRgb(input, false);
                            if (color < 0) {
                                this.sendHexColorCodeError(context.getSource(), input);
                            } else {
                                this.executeWaypointPatch(
                                        context.getSource(),
                                        this.getArgument(context, DIMENSION_ARG),
                                        getString(context, LIST_NAME_ARG),
                                        getString(context, WAYPOINT_NAME_ARG),
                                        patchWithColor(color)
                                );
                            }
                            return Command.SINGLE_SUCCESS;
                        })));
        set.then(LiteralArgumentBuilder.<S>literal("yaw")
                .then(RequiredArgumentBuilder.<S, Integer>argument(YAW_ARG, integer()).executes(context -> {
                    this.executeWaypointPatch(
                            context.getSource(),
                            this.getArgument(context, DIMENSION_ARG),
                            getString(context, LIST_NAME_ARG),
                            getString(context, WAYPOINT_NAME_ARG),
                            patchWithYaw(getInteger(context, YAW_ARG))
                    );
                    return Command.SINGLE_SUCCESS;
                })));
        LiteralArgumentBuilder<S> visibility = literal("visibility");
        visibility.then(LiteralArgumentBuilder.<S>literal("global").executes(context -> this.executeVisibilityPatch(context, true)));
        visibility.then(LiteralArgumentBuilder.<S>literal("local").executes(context -> this.executeVisibilityPatch(context, false)));
        set.then(visibility);
        set.then(this.waypointStringPatchNode("keywords", WaypointPatchProperty.KEYWORDS));
        set.then(this.waypointStringPatchNode("description", WaypointPatchProperty.DESCRIPTION));

        LiteralArgumentBuilder<S> clear = literal(CLEAR_COMMAND);
        clear.then(this.waypointClearPatchNode("display-name", WaypointPatchProperty.DISPLAY_NAME));
        clear.then(this.waypointClearPatchNode("keywords", WaypointPatchProperty.KEYWORDS));
        clear.then(this.waypointClearPatchNode("description", WaypointPatchProperty.DESCRIPTION));
        waypoint.then(set).then(clear);
        list.then(waypoint);
        dimension.then(list);
        target.then(dimension);
        return target;
    }

    private int executeVisibilityPatch(CommandContext<S> context, boolean global) {
        this.executeWaypointPatch(
                context.getSource(),
                this.getArgument(context, DIMENSION_ARG),
                getString(context, LIST_NAME_ARG),
                getString(context, WAYPOINT_NAME_ARG),
                patchWithVisibility(global)
        );
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<S> waypointStringPatchNode(
            String property,
            WaypointPatchProperty patchProperty
    ) {
        LiteralArgumentBuilder<S> propertyNode = literal(property);
        propertyNode.then(RequiredArgumentBuilder.<S, String>argument(VALUE_ARG, string()).executes(context -> {
            this.executeWaypointPatch(
                    context.getSource(),
                    this.getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    getString(context, WAYPOINT_NAME_ARG),
                    patchWithString(patchProperty, getString(context, VALUE_ARG), false)
            );
            return Command.SINGLE_SUCCESS;
        }));
        return propertyNode;
    }

    private LiteralArgumentBuilder<S> waypointClearPatchNode(
            String property,
            WaypointPatchProperty patchProperty
    ) {
        return LiteralArgumentBuilder.<S>literal(property).executes(context -> {
            this.executeWaypointPatch(
                    context.getSource(),
                    this.getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    getString(context, WAYPOINT_NAME_ARG),
                    patchWithString(patchProperty, "", true)
            );
            return Command.SINGLE_SUCCESS;
        });
    }

    private RequiredArgumentBuilder<S, String> stringArgument(
            String name,
            SuggestionProvider<S> suggestions
    ) {
        return RequiredArgumentBuilder.<S, String>argument(name, string()).suggests(suggestions);
    }

    @SuppressWarnings("unchecked")
    public @NotNull LiteralCommandNode<S> build() {
        return (LiteralCommandNode<S>) literal(WAYPOINT_COMMAND)
                .then(literal(HELP_COMMAND)
                        .executes(context -> {
                            executeHelp((S) context.getSource());
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(literal(ADD_COMMAND)
                                .requires(source -> hasAddPermission((S) source))
                                .executes(context -> {
                                    executeAddHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal(EDIT_COMMAND)
                                .requires(source -> hasEditPermission((S) source))
                                .executes(context -> {
                                    executeEditHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal(LIST_COMMAND)
                                .executes(context -> {
                                    executeListHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                        .then(literal(NAVIGATE_COMMAND)
                                .requires(source -> hasNavigatePermission((S) source))
                                .executes(context -> {
                                    executeNavigateHelp((S) context.getSource());
                                    return Command.SINGLE_SUCCESS;
                                })
                        )
                )
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
                                                                                .then(extraInfoArguments(
                                                                                        cxt -> {
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
                                                                                                    getBool(context, VISIBILITY_ARG),
                                                                                                    parseKeywords(getOptionalString(context, KEYWORDS_ARG, "")),
                                                                                                    getOptionalString(context, DESCRIPTION_ARG, "")
                                                                                            );
                                                                                            return Command.SINGLE_SUCCESS;
                                                                                        }
                                                                                ))
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
                                                                        .then(extraInfoArguments(
                                                                                cxt -> {
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
                                                                                                    getBool(context, VISIBILITY_ARG),
                                                                                                    parseKeywords(getOptionalString(context, KEYWORDS_ARG, "")),
                                                                                                    getOptionalString(context, DESCRIPTION_ARG, "")
                                                                                            );
                                                                                            return Command.SINGLE_SUCCESS;
                                                                                        }
                                                                        ))
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                )
                .then((ArgumentBuilder<Object, ?>) editCommandNode())
                .then((ArgumentBuilder<Object, ?>) detailsCommandNode())
                .then(literal(RESTORE_COMMAND)
                        .requires(source -> hasAddPermission((S) source))
                        .then(argument(TOKEN_ARG, string())
                                .executes(context -> {
                                    executeRestore((S) context.getSource(), getString(context, TOKEN_ARG));
                                    return Command.SINGLE_SUCCESS;
                                })
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
                .then((ArgumentBuilder<Object, ?>) (ArgumentBuilder<?, ?>) uploadCommandNode())
                .then((ArgumentBuilder<Object, ?>) listCommandNode())
                .then((ArgumentBuilder<Object, ?>) navigationCommandNode())
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

    private LiteralArgumentBuilder<S> uploadCommandNode() {
        LiteralArgumentBuilder<S> upload = literal(UPLOAD_COMMAND);
        upload.requires(this::hasUploadPermission);
        upload.executes(context -> executeUploadAndReturn(
                context.getSource(), UploadConflictPolicy.SERVER, false, UploadScope.WORLD, null, null, null
        ));
        upload.then(uploadSelectorArguments(UploadConflictPolicy.SERVER, false));

        LiteralArgumentBuilder<S> force = literal(UPLOAD_FORCE_COMMAND);
        LiteralArgumentBuilder<S> server = literal(UPLOAD_SERVER_COMMAND);
        server.executes(context -> executeUploadAndReturn(
                context.getSource(), UploadConflictPolicy.SERVER, false, UploadScope.WORLD, null, null, null
        ));
        server.then(uploadSelectorArguments(UploadConflictPolicy.SERVER, false));
        force.then(server);

        LiteralArgumentBuilder<S> local = literal(UPLOAD_LOCAL_COMMAND);
        local.executes(context -> executeUploadAndReturn(
                context.getSource(), UploadConflictPolicy.LOCAL, false, UploadScope.WORLD, null, null, null
        ));
        local.then(uploadSelectorArguments(UploadConflictPolicy.LOCAL, false));

        LiteralArgumentBuilder<S> delete = literal(UPLOAD_DELETE_COMMAND);
        delete.requires(this::hasUploadDeletePermission);
        delete.executes(context -> executeUploadAndReturn(
                context.getSource(), UploadConflictPolicy.LOCAL, true, UploadScope.WORLD, null, null, null
        ));
        delete.then(uploadSelectorArguments(UploadConflictPolicy.LOCAL, true));
        local.then(delete);
        force.then(local);
        upload.then(force);
        return upload;
    }

    private int executeUploadAndReturn(
            S source,
            UploadConflictPolicy conflictPolicy,
            boolean deleteMissing,
            UploadScope scope,
            @Nullable D dimensionArgument,
            @Nullable String listName,
            @Nullable String waypointName
    ) {
        executeUpload(source, conflictPolicy, deleteMissing, scope, dimensionArgument, listName, waypointName);
        return Command.SINGLE_SUCCESS;
    }

    private void executeHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.mainMenu(
                hasAddPermission(source),
                hasEditPermission(source),
                hasRemovePermission(source),
                hasNavigatePermission(source),
                hasTpPermission(source),
                hasReloadPermission(source),
                hasUploadPermission(source)
        ));
    }

    private void executeAddHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.addHelp());
    }

    private void executeEditHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.editHelp());
    }

    private void executeListHelp(S source) {
        this.sender.sendMessage(source, WaypointCommandHelp.listHelp());
    }

    private void executeNavigateHelp(S source) {
        this.sender.sendMessage(
                source,
                WaypointCommandHelp.navigateHelp(
                        this.isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)
                )
        );
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
        }, (fileManager, waypointList) ->
                this.sender.sendError(source, translatable("waypoint.empty.list", parse(waypointList.displayName()))));
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

    private boolean validateTextInputs(
            S source,
            String listName,
            @Nullable String waypointName,
            @Nullable List<String> keywords,
            @Nullable String description
    ) {
        if (description != null
                && !validateLength(source, DESCRIPTION_ARG, description, MAX_DESCRIPTION_LENGTH)) {
            return false;
        }
        if (description != null && !isValidInput(description)) {
            this.sender.sendError(source, translatable("argument.formatted_text.invalid"));
            return false;
        }
        if (keywords != null) {
            if (keywords.size() > MAX_KEYWORDS) {
                this.sender.sendError(
                        source,
                        translatable("argument.keywords.too_many", text(MAX_KEYWORDS))
                );
                return false;
            }
            if (hasDuplicateKeywords(keywords)) {
                this.sender.sendError(source, translatable("argument.keywords.duplicate"));
                return false;
            }
            for (String keyword : keywords) {
                if (!validateLength(source, KEYWORDS_ARG, keyword, MAX_KEYWORD_LENGTH)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean validateLength(S source, String argument, String value, int maximum) {
        if (value.length() <= maximum) {
            return true;
        }
        this.sender.sendError(
                source,
                translatable("argument.text.too_long", text(argument), text(maximum))
        );
        return false;
    }

    private static List<String> parseKeywords(String rawKeywords) {
        if (rawKeywords == null || rawKeywords.trim().isEmpty()) {
            return List.of();
        }
        List<String> keywords = new ArrayList<>();
        for (String keyword : rawKeywords.split(",", -1)) {
            String trimmed = keyword.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        return List.copyOf(keywords);
    }

    private static WaypointPatch patchWithString(
            WaypointPatchProperty property,
            String value,
            boolean clear
    ) {
        PatchField<String> field = clear ? PatchField.clear() : PatchField.set(value);
        return new WaypointPatch(
                property == WaypointPatchProperty.IDENTIFIER ? field : PatchField.unchanged(),
                property == WaypointPatchProperty.DISPLAY_NAME ? field : PatchField.unchanged(),
                property == WaypointPatchProperty.INITIALS ? field : PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                property == WaypointPatchProperty.KEYWORDS
                        ? (clear ? PatchField.clear() : PatchField.set(parseKeywords(value)))
                        : PatchField.unchanged(),
                property == WaypointPatchProperty.DESCRIPTION ? field : PatchField.unchanged()
        );
    }

    private static WaypointPatch patchWithPosition(WaypointPos position) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.set(position), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged()
        );
    }

    private static WaypointPatch patchWithColor(int color) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.set(color), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged()
        );
    }

    private static WaypointPatch patchWithYaw(int yaw) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.set(yaw),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged()
        );
    }

    private static WaypointPatch patchWithVisibility(boolean global) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.set(global), PatchField.unchanged(), PatchField.unchanged()
        );
    }

    private void executeListPatch(
            S source,
            D dimensionArgument,
            String listIdentifier,
            WaypointListPatch patch
    ) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        try {
            this.waypointServer.updateWaypointList(
                    EditTarget.list(dimensionName, listIdentifier),
                    null,
                    patch,
                    result -> {
                        if (result.status() == EditResultStatus.SUCCESS) {
                            ChunkedMessageManager.validateEncodable(new WaypointListUpdateMessage(
                                    dimensionName,
                                    Objects.requireNonNull(result.beforeSnapshot()).name(),
                                    Objects.requireNonNull(result.afterSnapshot())
                            ));
                        }
                    },
                    result -> {
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), listIdentifier);
                            return;
                        }
                        WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                        WaypointList before = Objects.requireNonNull(result.beforeSnapshot());
                        WaypointList after = Objects.requireNonNull(result.afterSnapshot());
                        saveChanges(source, fileManager);
                        this.navigationService.refreshListIdentity(
                                dimensionName,
                                before.name(),
                                after.name(),
                                after.displayName()
                        );
                        WaypointListUpdateMessage update = new WaypointListUpdateMessage(
                                dimensionName,
                                before.name(),
                                after
                        );
                        this.sender.broadcastChunkedMessage(
                                this.sender.getBroadcastPlayers(source),
                                update
                        );
                        this.sender.sendMessage(
                                source,
                                _959.server_waypoint.text.WaypointDetailsTextBuilder.listDetails(
                                        dimensionName,
                                        after,
                                        hasAddPermission(source),
                                        hasEditPermission(source),
                                        hasRemovePermission(source)
                                )
                        );
                    }
            );
        } catch (MessageEncodingException exception) {
            this.reportEncodingFailure(source, WaypointListUpdateMessage.class, exception);
        }
    }

    private void executeWaypointPatch(
            S source,
            D dimensionArgument,
            String listIdentifier,
            String waypointIdentifier,
            WaypointPatch patch
    ) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        try {
            this.waypointServer.updateWaypoint(
                    EditTarget.waypoint(dimensionName, listIdentifier, waypointIdentifier),
                    null,
                    patch,
                    result -> {
                        if (result.status() == EditResultStatus.SUCCESS) {
                            ChunkedMessageManager.validateEncodable(new WaypointModificationMessage(
                                    dimensionName,
                                    Objects.requireNonNull(result.listSnapshot()).name(),
                                    Objects.requireNonNull(result.listSnapshot()).displayName(),
                                    Objects.requireNonNull(result.beforeSnapshot()).name(),
                                    Objects.requireNonNull(result.afterSnapshot()),
                                    WaypointModificationType.UPDATE,
                                    result.syncNum()
                            ));
                        }
                    },
                    result -> {
                        if (result.status() != EditResultStatus.SUCCESS) {
                            this.sendEditError(source, result.status(), waypointIdentifier);
                            return;
                        }
                        WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                        WaypointList list = Objects.requireNonNull(result.listSnapshot());
                        SimpleWaypoint before = Objects.requireNonNull(result.beforeSnapshot());
                        SimpleWaypoint after = Objects.requireNonNull(result.afterSnapshot());
                        saveChanges(source, fileManager);
                        this.navigationService.refreshTarget(
                                new NavigationTarget(dimensionName, list, before),
                                new NavigationTarget(dimensionName, list, after)
                        );
                        WaypointModificationMessage update = new WaypointModificationMessage(
                                dimensionName,
                                list.name(),
                                list.displayName(),
                                before.name(),
                                after,
                                WaypointModificationType.UPDATE,
                                result.syncNum()
                        );
                        this.sender.broadcastChunkedMessage(
                                this.sender.getBroadcastPlayers(source),
                                update
                        );
                        this.sender.sendMessage(
                                source,
                                _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                        dimensionName,
                                        list,
                                        after,
                                        hasEditPermission(source),
                                        hasRemovePermission(source),
                                        hasTpPermission(source),
                                        hasNavigatePermission(source)
                                )
                        );
                    }
            );
        } catch (MessageEncodingException exception) {
            this.reportEncodingFailure(source, WaypointModificationMessage.class, exception);
        }
    }

    private void reportEncodingFailure(
            S source,
            Class<?> messageType,
            MessageEncodingException exception
    ) {
        WaypointServerCore.LOGGER.warn(
                "Rejected waypoint mutation because {} could not be encoded within its {}-byte logical-message budget",
                messageType.getSimpleName(),
                ChunkedMessageManager.MAX_MESSAGE_BYTES,
                exception
        );
        this.sender.sendError(source, translatable("waypoint.network.encoding_failed"));
    }

    private void executeListDetails(S source, D dimensionArgument, String listIdentifier) {
        BiConsumer<WaypointFileManager, WaypointList> action = (fileManager, waypointList) ->
                this.sender.sendMessage(
                        source,
                        _959.server_waypoint.text.WaypointDetailsTextBuilder.listDetails(
                                fileManager.getDimensionName(),
                                waypointList,
                                hasAddPermission(source),
                                hasEditPermission(source),
                                hasRemovePermission(source)
                        )
                );
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, action, action);
    }

    private void executeWaypointDetails(
            S source,
            D dimensionArgument,
            String listIdentifier,
            String waypointIdentifier
    ) {
        runWithSelectorTarget(
                source,
                dimensionArgument,
                listIdentifier,
                waypointIdentifier,
                (fileManager, waypointList, waypoint) -> this.sender.sendMessage(
                        source,
                        _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                fileManager.getDimensionName(),
                                waypointList,
                                waypoint,
                                hasEditPermission(source),
                                hasRemovePermission(source),
                                hasTpPermission(source),
                                hasNavigatePermission(source)
                        )
                )
        );
    }

    private void sendEditError(S source, EditResultStatus status, String identifier) {
        this.sender.sendError(
                source,
                translatable(
                        "waypoint.edit.error." + status.name().toLowerCase(Locale.ROOT),
                        text(identifier)
                )
        );
    }

    private String restoreOwner(S source) {
        P player = getPlayer(source);
        return player == null ? this.sender.getSenderName(source).toString() : getPlayerName(player);
    }

    private void executeRestore(S source, String token) {
        String owner = this.restoreOwner(source);
        this.restoreRegistry.lookup(owner, token).ifPresentOrElse(entry ->
                this.waypointServer.restoreWaypoint(
                        entry.dimensionName(),
                        entry.listIdentifier(),
                        entry.waypoint(),
                        result -> {
                            switch (result.status()) {
                                case RESTORED -> {
                                    this.restoreRegistry.consume(owner, token);
                                    WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                                    WaypointList list = Objects.requireNonNull(result.waypointList());
                                    SimpleWaypoint waypoint = Objects.requireNonNull(result.waypointSnapshot());
                                    saveChanges(source, fileManager);
                                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(
                                            entry.dimensionName(),
                                            list.name(),
                                            list.displayName(),
                                            waypoint.name(),
                                            waypoint,
                                            WaypointModificationType.ADD,
                                            result.syncNum()
                                    ));
                                    this.sender.sendMessage(
                                            source,
                                            _959.server_waypoint.text.WaypointDetailsTextBuilder.waypointDetails(
                                                    entry.dimensionName(),
                                                    list,
                                                    waypoint,
                                                    hasEditPermission(source),
                                                    hasRemovePermission(source),
                                                    hasTpPermission(source),
                                                    hasNavigatePermission(source)
                                            )
                                    );
                                }
                                case DIMENSION_NOT_FOUND, LIST_NOT_FOUND -> this.sender.sendError(
                                        source,
                                        translatable("waypoint.restore.list_missing")
                                );
                                case IDENTIFIER_COLLISION -> this.sender.sendError(
                                        source,
                                        translatable("waypoint.restore.collision")
                                );
                            }
                        }
                ),
                () -> this.sender.sendError(source, translatable("waypoint.restore.invalid"))
        );
    }

    private enum WaypointPatchProperty {
        IDENTIFIER,
        DISPLAY_NAME,
        INITIALS,
        KEYWORDS,
        DESCRIPTION
    }

    private void executeAddWaypointList(S source, D dimensionArgument, String listName) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!validateTextInputs(source, listName, null, null, null)) {
            return;
        }
        if (isDimensionValid(source, dimensionArgument)) {
            this.waypointServer.addWaypointList(
                    dimensionName,
                    listName,
                    listName,
                    result -> {
                switch (result.status()) {
                    case ADDED -> {
                        this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(dimensionName, listName, listName, null, null, ADD_LIST, SERVER_N));
                        this.sender.sendMessage(source, translatable("waypoint.add.list.success", text(listName), dimensionNameWithColor(dimensionName))
                                .appendSpace().append(detailsButton(_959.server_waypoint.util.StringCommandBuilder.detailsListCmd(dimensionName, listName)))
                                .appendSpace().append(suggestCommandButton(
                                        translatable("button.add.waypoint.label"),
                                        NamedTextColor.GREEN,
                                        "/wp add " + dimensionName + " " + _959.server_waypoint.util.StringCommandBuilder.escapeArgument(listName) + " ",
                                        translatable("button.add.waypoint")
                                )));
                        saveChanges(source, result.fileManager());
                    }
                    case EXISTS -> this.sender.sendError(source, translatable("waypoint.add.list.exists", parse(result.waypointList().displayName())));
                }
            });
        }
    }

    private void addWaypointDirectly(S source, String dimensionName, String listName, String name, String initials, WaypointPos waypointPos, int yaw, int rgb, boolean global, List<String> keywords, String description) {
        if (!validateTextInputs(source, listName, name, keywords, description)) {
            return;
        }
        SimpleWaypoint newWaypoint = new SimpleWaypoint(
                name,
                initials,
                waypointPos,
                rgb,
                yaw,
                global,
                keywords,
                description
        );
        this.waypointServer.addWaypoint(dimensionName, listName, listName, newWaypoint, result -> {
            switch (result.status()) {
                case ADDED -> {
                    saveChanges(source, result.fileManager());
                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(
                            dimensionName,
                            result.waypointList().name(),
                            result.waypointList().displayName(),
                            result.waypointSnapshot().name(),
                            result.waypointSnapshot(),
                            WaypointModificationType.ADD,
                            result.syncNum()
                    ));
                    this.sender.sendMessage(
                            source,
                            translatable("waypoint.add.success",
                                    waypointTextWithTp(result.waypointSnapshot(), dimensionName, result.waypointList().name()),
                                    parse(result.waypointList().displayName())
                            ).appendSpace().append(detailsButton(
                                    _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd(
                                            dimensionName,
                                            result.waypointList().name(),
                                            result.waypointSnapshot().name()
                                    )
                            ))
                    );
                }
                case DUPLICATE -> this.sender.sendMessage(
                        source,
                        translatable(
                                "waypoint.add.exists",
                                waypointTextWithTp(result.waypointSnapshot(), dimensionName, result.waypointList().name()),
                                TextButtonBuilder.replaceButton(dimensionName, result.waypointList().name(), newWaypoint)
                        )
                );
            }
        });
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, String hexCode, boolean global, List<String> keywords, String description) {
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
            addWaypointDirectly(source, dimensionName, listName, name, initials, waypointPos, yaw, rgb, global, keywords, description);
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void executeQuickAddWaypoint(S source, B blockPosArgument, String listName, String name) {
        addWaypointDirectly(source, toDimensionName(getSourceDimension(source)), listName, name, WaypointInitials.getDefaultInitials(plainText(name)), toWaypointPos(source, blockPosArgument), Math.round(getSourceYaw(source)), randomColor(), true, List.of(), "");
    }

    private void executeRemoveList(S source, D dimensionArgument, String listName) {
//        runWithSelectorTarget(source, dimensionArgument, listName,
//                (fileManager, waypointList) ->
//                        this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", text(listName))),
//                (fileManager, waypointList) -> {
//                    fileManager.removeWaypointListByName(listName);
//                    String dimensionName = fileManager.getDimensionName();
//                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(dimensionName, listName, null, null, REMOVE_LIST, WaypointList.REMOVE_LIST));
//                    this.sender.sendMessage(source, translatable("waypoint.remove.list.success", text(listName)));
//                    saveChanges(source, fileManager);
//                });
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        this.waypointServer.removeWaypointList(dimensionName, listName, result -> {
            switch (result.status()) {
                case REMOVED -> {
                    WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                    WaypointList waypointList = Objects.requireNonNull(result.waypointList());
                    this.sender.broadcastWaypointModification(source, new WaypointModificationMessage(dimensionName, listName, waypointList.displayName(), null, null, REMOVE_LIST, waypointList.getSyncNum() + 1));
                    this.sender.sendMessage(source, translatable("waypoint.remove.list.success", parse(waypointList.displayName())));
                    saveChanges(source, fileManager);
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                case LIST_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
                case NON_EMPTY -> this.sender.sendError(source, translatable("waypoint.remove.list.nonempty", parse(Objects.requireNonNull(result.waypointList()).displayName())));
            }
        });
    }

    private void executeRemoveWaypoint(S source, D dimensionArgument, String listName, String name) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        this.waypointServer.removeWaypoint(dimensionName, listName, name, result -> {
            switch (result.status()) {
                case REMOVED -> {
                    WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                    SimpleWaypoint waypoint = Objects.requireNonNull(result.waypointSnapshot());
                    saveChanges(source, fileManager);
                    WaypointModificationMessage buffer = new WaypointModificationMessage(
                            dimensionName,
                            listName,
                            Objects.requireNonNull(result.waypointList()).displayName(),
                            name,
                            waypoint,
                            WaypointModificationType.REMOVE,
                            result.syncNum()
                    );
                    this.sender.broadcastWaypointModification(source, buffer);
                    String token = this.restoreRegistry.register(
                            this.restoreOwner(source),
                            dimensionName,
                            listName,
                            waypoint
                    );
                    this.sender.sendMessage(source, translatable(
                            "waypoint.remove.success",
                            waypointTextNoTp(waypoint, dimensionName),
                            restoreTokenButton(token)
                    ));
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                case LIST_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.list", text(listName)));
                case LIST_EMPTY -> this.sender.sendError(source, translatable("waypoint.empty.list", parse(Objects.requireNonNull(result.waypointList()).displayName())));
                case WAYPOINT_NOT_FOUND -> this.sender.sendError(source, translatable("waypoint.nonexist.waypoint", text(name)));
            }
        });
    }

    private void executeTp(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                runIfPlayerExists(source, player -> {
                    teleportPlayer(source, player, dimensionArgument, waypoint.pos(), waypoint.yaw());
                    this.sender.sendPlayerMessage(player, translatable("waypoint.tp", text(getPlayerName(player)), waypointTextWithTp(waypoint, fileManager.getDimensionName(), listName)));
                }));
    }

    private void executeNavigate(
            S source,
            D dimensionArgument,
            String listName,
            String waypointName,
            @Nullable NavigationMethod method
    ) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        runWithSelectorTarget(
                source,
                dimensionArgument,
                listName,
                waypointName,
                (fileManager, waypointList, waypoint) -> {
                    NavigationTarget target = new NavigationTarget(
                            fileManager.getDimensionName(),
                            waypointList,
                            waypoint
                    );
                    NavigationResult result;
                    if (method == null) {
                        NavigationResult status = this.navigationService.status(player);
                        if (status.code() == NavigationResult.Code.NO_ACTIVE_SESSION) {
                            result = this.navigationService.navigate(
                                    player,
                                    target,
                                    CONFIG.defaultNavigationMethods()
                            );
                        } else {
                            result = this.navigationService.retarget(player, target);
                        }
                    } else {
                        result = this.navigationService.navigate(
                                player,
                                target,
                                method
                        );
                    }
                    sendNavigationResult(source, result);
                }
        );
    }

    private void executeNavigate(
            S source,
            D dimensionArgument,
            String listName,
            String waypointName,
            Set<NavigationMethod> methods
    ) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        runWithSelectorTarget(
                source,
                dimensionArgument,
                listName,
                waypointName,
                (fileManager, waypointList, waypoint) -> {
                    NavigationTarget target = new NavigationTarget(
                            fileManager.getDimensionName(),
                            waypointList,
                            waypoint
                    );
                    NavigationResult result;
                    result = this.navigationService.navigate(
                            player,
                            target,
                            methods
                    );
                    sendNavigationResult(source, result);
                }
        );
    }

    private void executeNavigateUse(S source, NavigationMethod method) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.enableMethod(player, method));
        }
    }

    private void executeNavigateDisable(S source) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.disableAll(player));
        }
    }

    private void executeNavigateDisable(S source, NavigationMethod method) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.disableMethod(player, method));
        }
    }

    private void executeNavigateStatus(S source) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.status(player));
        }
    }

    private void executeTextDisplayTransformationReset(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult result = this.navigationService.resetTextDisplayTransformation(player);
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED) {
            sendNavigationResult(source, result);
            return;
        }
        sendTextDisplayTransformation(
                source,
                "waypoint.navigation.text_display.transformation.reset",
                result.session().textDisplayTransformation()
        );
    }

    private void executeTextDisplayTranslation(S source, Vector3f translation) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult result = this.navigationService.updateTextDisplayTranslation(
                player,
                translation
        );
        this.sendTextDisplayTransformationUpdate(source, result);
    }

    private void executeTextDisplayRotation(S source, Vector3f rotation) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult result = this.navigationService.updateTextDisplayRotation(
                player,
                rotation
        );
        this.sendTextDisplayTransformationUpdate(source, result);
    }

    private void executeTextDisplayScale(S source, Vector3f scale) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult result = this.navigationService.updateTextDisplayScale(player, scale);
        this.sendTextDisplayTransformationUpdate(source, result);
    }

    private void sendTextDisplayTransformationUpdate(S source, NavigationResult result) {
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED) {
            sendNavigationResult(source, result);
            return;
        }
        sendTextDisplayTransformation(
                source,
                "waypoint.navigation.text_display.transformation.updated",
                result.session().textDisplayTransformation()
        );
    }

    private void sendTextDisplayTransformation(
            S source,
            String translationKey,
            TextDisplayTransformation transformation
    ) {
        this.sender.sendMessage(
                source,
                translatable(
                        translationKey,
                        transformationVector(transformation.translation()),
                        transformationVector(transformation.rotation()),
                        transformationVector(transformation.scale())
                )
        );
    }

    private static Component transformationVector(Vector3f vector) {
        return text(vector.x() + " " + vector.y() + " " + vector.z());
    }

    private @Nullable P getNavigationPlayer(S source) {
        P player = getPlayer(source);
        if (player == null) {
            this.sender.sendError(source, translatable("waypoint.navigation.player_only"));
        }
        return player;
    }

    private boolean isNavigationMethodSupported(NavigationMethod method) {
        return this.navigationService.supportedNavigationMethods().contains(method);
    }

    private Set<NavigationMethod> supportedNavigationMethods() {
        return this.navigationService.supportedNavigationMethods();
    }

    private void sendNavigationResult(S source, NavigationResult result) {
        NavigationSession session = result.session();
        NavigationMethod method = result.method();
        switch (result.code()) {
            case NAVIGATION_STARTED -> this.sender.sendMessage(
                    source,
                    translatable(
                            "waypoint.navigation.started",
                            navigationTargetName(session.target()),
                            navigationMethods(session.enabledMethods())
                    )
            );
            case TARGET_CHANGED -> this.sender.sendMessage(
                    source,
                    translatable(
                            "waypoint.navigation.target_changed",
                            navigationTargetName(session.target()),
                            navigationMethods(session.enabledMethods())
                    )
            );
            case SELECTION_REPLACED -> this.sender.sendMessage(
                    source,
                    translatable(
                            "waypoint.navigation.selection_replaced",
                            navigationTargetName(session.target()),
                            navigationMethods(session.enabledMethods())
                    )
            );
            case METHOD_ENABLED -> this.sender.sendMessage(
                    source,
                    translatable("waypoint.navigation.method_enabled", text(method.id()))
            );
            case METHOD_ALREADY_ENABLED -> this.sender.sendMessage(
                    source,
                    translatable("waypoint.navigation.method_already_enabled", text(method.id()))
            );
            case METHOD_DISABLED -> this.sender.sendMessage(
                    source,
                    translatable("waypoint.navigation.method_disabled", text(method.id()))
            );
            case METHOD_ALREADY_DISABLED -> this.sender.sendMessage(
                    source,
                    translatable("waypoint.navigation.method_not_enabled", text(method.id()))
            );
            case NAVIGATION_DISABLED -> this.sender.sendMessage(
                    source,
                    translatable("waypoint.navigation.disabled")
            );
            case STATUS -> this.sender.sendMessage(
                    source,
                    translatable(
                            "waypoint.navigation.status",
                            navigationTargetName(session.target()),
                            dimensionNameWithColor(session.target().dimensionName()),
                            parse(session.target().listDisplayName()),
                            navigationMethods(session.enabledMethods())
                    )
            );
            case NO_ACTIVE_SESSION -> this.sender.sendError(
                    source,
                    translatable("waypoint.navigation.no_active")
            );
            case INSUFFICIENT_INVENTORY -> this.sender.sendError(
                    source,
                    translatable(
                            "waypoint.navigation.inventory.insufficient",
                            text(result.requiredSlots()),
                            text(result.availableSlots())
                    )
            );
            case TARGET_UNAVAILABLE -> this.sender.sendError(
                    source,
                    translatable("waypoint.navigation.target_unavailable")
            );
            case INVALID_SELECTION -> this.sender.sendError(
                    source,
                    translatable("waypoint.navigation.invalid_selection")
            );
            case METHOD_UNAVAILABLE, HANDLER_FAILED, PLATFORM_REJECTED -> this.sender.sendError(
                    source,
                    translatable(
                            "waypoint.navigation.method_failed",
                            text(method == null ? "unknown" : method.id())
                    )
            );
            case TEXT_DISPLAY_TRANSFORMATION_UPDATED, SUCCESS -> {
            }
        }
    }

    private Component navigationTargetName(NavigationTarget target) {
        return parse(target.waypointDisplayName()).colorIfAbsent(TextColor.color(target.rgb()));
    }

    private Component navigationMethods(Set<NavigationMethod> methods) {
        if (methods.isEmpty()) {
            return translatable("waypoint.navigation.methods.none");
        }
        return text(methods.stream().map(NavigationMethod::id).sorted().collect(java.util.stream.Collectors.joining(", ")));
    }

    private void executeDownload(S source) {
        WaypointData waypointData = this.waypointServer.toWorldWaypointData();
        if (waypointData == null) {
            this.sender.sendMessage(source, translatable("waypoint.no.waypoints"));
            return;
        }
        this.sendDownload(
                source,
                waypointData,
                translatable("waypoint.download.all")
        );
    }

    private void executeDownload(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendError(source, translatable("waypoint.empty.dimension", dimensionNameWithColor(dimensionName)));
                return;
            }
            this.sendDownload(
                    source,
                    WaypointData.dimension(fileManager.toDimensionWaypointData()),
                    translatable(
                            "waypoint.download.dimension",
                            dimensionNameWithColor(dimensionName)
                    )
            );
        });
    }

    private void executeDownload(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> {
                    this.sendDownload(
                            source,
                            WaypointData.waypointList(
                                    fileManager.getDimensionName(),
                                    waypointList
                            ),
                            translatable(
                                    "waypoint.download.list",
                                    parse(waypointList.displayName())
                            )
                    );
                }, (fileManager, waypointList) ->
                        this.sender.sendError(source, translatable("waypoint.empty.list", parse(waypointList.displayName())))
        );
    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) -> {
            String dimensionName = fileManager.getDimensionName();
            this.sendDownload(
                    source,
                    new WaypointModificationMessage(
                            dimensionName,
                            listName,
                            waypointList.displayName(),
                            name,
                            waypoint,
                            WaypointModificationType.ADD,
                            waypointList.getSyncNum()
                    ),
                    translatable(
                            "waypoint.download.waypoint",
                            waypointTextWithTp(waypoint, dimensionName, listName)
                    )
            );
        });
    }

    private void sendDownload(S source, ChunkedMessage message, Component successMessage) {
        _959.server_waypoint.core.network.ChunkedMessageDelivery delivery =
                this.sender.sendChunkedMessage(source, message);
        if (!delivery.queued()) {
            this.sender.sendError(source, translatable("waypoint.network.delivery_failed"));
            return;
        }
        delivery.completion().whenComplete((result, exception) -> {
            if (exception == null && result != null && result.delivered()) {
                this.sender.sendMessage(source, successMessage);
            } else {
                this.sender.sendError(source, translatable("waypoint.network.delivery_failed"));
            }
        });
    }

    private ArgumentBuilder<S, ?> uploadSelectorArguments(UploadConflictPolicy conflictPolicy, boolean deleteMissing) {
        return dimensionNode()
                .executes(context -> executeUploadAndReturn(
                        context.getSource(), conflictPolicy, deleteMissing,
                        UploadScope.DIMENSION, getArgument(context, DIMENSION_ARG), null, null
                ))
                .then(listNameNode()
                        .executes(context -> executeUploadAndReturn(
                                context.getSource(), conflictPolicy, deleteMissing,
                                UploadScope.LIST, getArgument(context, DIMENSION_ARG),
                                getString(context, LIST_NAME_ARG), null
                        ))
                        .then(waypointNameNode()
                                .executes(context -> executeUploadAndReturn(
                                        context.getSource(), conflictPolicy, deleteMissing,
                                        UploadScope.WAYPOINT, getArgument(context, DIMENSION_ARG),
                                        getString(context, LIST_NAME_ARG), getString(context, WAYPOINT_NAME_ARG)
                                ))
                        )
                );
    }

    private void executeUpload(
            S source,
            UploadConflictPolicy conflictPolicy,
            boolean deleteMissing,
            UploadScope scope,
            @Nullable D dimensionArgument,
            @Nullable String listName,
            @Nullable String waypointName
    ) {
        P player = getPlayer(source);
        if (player == null) {
            this.sender.sendError(source, translatable("waypoint.upload.player-only"));
            return;
        }
        if (!this.sender.canSendChunkedMessage(player)) {
            this.sender.sendError(source, translatable("waypoint.upload.client.incompatible"));
            return;
        }

        List<String> dimensions;
        if (scope == UploadScope.WORLD) {
            dimensions = getAvailableDimensionNames(source);
        } else {
            String dimensionName = toDimensionName(Objects.requireNonNull(dimensionArgument));
            if (!isDimensionValid(source, dimensionArgument)) {
                sendDimensionError(source, dimensionName);
                return;
            }
            dimensions = List.of(dimensionName);
        }
        if (dimensions.isEmpty()) {
            this.sender.sendError(source, translatable("waypoint.upload.request.invalid"));
            return;
        }

        UploadCoordinator.BeginResult beginResult = this.uploadCoordinator.begin(
                player, scope, conflictPolicy, deleteMissing, dimensions, listName, waypointName
        );
        if (beginResult.status() == UploadCoordinator.BeginStatus.BUSY) {
            this.sender.sendError(source, translatable("waypoint.upload.busy"));
            return;
        }
        if (beginResult.status() == UploadCoordinator.BeginStatus.COOLDOWN) {
            long remainingSeconds = Math.max(
                    1L,
                    (beginResult.cooldownRemaining().toMillis() + 999L) / 1_000L
            );
            this.sender.sendError(source, translatable(
                    "waypoint.upload.cooldown",
                    text(remainingSeconds)
            ));
            return;
        }
        UploadRequestBuffer request = Objects.requireNonNull(beginResult.request());
        this.sender.sendPlayerPacketTracked(player, request).whenComplete((result, exception) -> {
            if (exception == null && result != null && result.delivered()) {
                return;
            }
            if (this.uploadCoordinator.cancel(
                    player,
                    request.requestId(),
                    "upload request delivery failed"
            )) {
                this.sender.sendPlayerMessage(
                        player,
                        translatable("waypoint.upload.request.delivery_failed")
                );
            }
        });
        this.sender.sendMessage(source, translatable(deleteMissing
                ? "waypoint.upload.requested.force-delete"
                : "waypoint.upload.requested"));
    }

    private LiteralArgumentBuilder<S> navigationCommandNode() {
        LiteralArgumentBuilder<S> navigateNode = literal(NAVIGATE_COMMAND);
        navigateNode.requires(this::hasNavigatePermission);

        LiteralArgumentBuilder<S> useNode = literal(USE_COMMAND);
        for (NavigationMethod method : this.supportedNavigationMethods()) {
            LiteralArgumentBuilder<S> methodNode = literal(method.id());
            methodNode.executes(context -> {
                executeNavigateUse(context.getSource(), method);
                return Command.SINGLE_SUCCESS;
            });
            useNode.then(methodNode);
        }
        navigateNode.then(useNode);

        LiteralArgumentBuilder<S> disableNode = literal(DISABLE_COMMAND);
        disableNode.executes(context -> {
            executeNavigateDisable(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
        for (NavigationMethod method : this.supportedNavigationMethods()) {
            LiteralArgumentBuilder<S> methodNode = literal(method.id());
            methodNode.executes(context -> {
                executeNavigateDisable(context.getSource(), method);
                return Command.SINGLE_SUCCESS;
            });
            disableNode.then(methodNode);
        }
        navigateNode.then(disableNode);

        LiteralArgumentBuilder<S> statusNode = literal(STATUS_COMMAND);
        statusNode.executes(context -> {
            executeNavigateStatus(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
        navigateNode.then(statusNode);

        LiteralArgumentBuilder<S> configNode = literal(CONFIG_LITERAL_NODE);

        if (this.isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)) {
            configNode.then(this.textDisplayTransformationCommandNode());
        }
        navigateNode.then(configNode);

        RequiredArgumentBuilder<S, D> dimensionNode = argument(
                DIMENSION_ARG,
                this.dimensionArgumentProvider.get()
        );
        RequiredArgumentBuilder<S, String> listNode = argument(LIST_NAME_ARG, string());
        listNode.suggests(this.WAYPOINT_LIST_SUGGESTION);
        RequiredArgumentBuilder<S, String> waypointNode = argument(WAYPOINT_NAME_ARG, string());
        waypointNode.suggests(this.WAYPOINT_NAME_SUGGESTION);
        waypointNode.executes(navigateTargetCommand(null));

        LiteralArgumentBuilder<S> defaultNode = literal("default");
        defaultNode.executes(navigateTargetWithDefaultCommand());
        waypointNode.then(defaultNode);
        LiteralArgumentBuilder<S> allNode = literal("all");
        allNode.executes(navigateTargetWithAllCommand());
        waypointNode.then(allNode);
        for (NavigationMethod method : this.supportedNavigationMethods()) {
            LiteralArgumentBuilder<S> methodNode = literal(method.id());
            methodNode.executes(navigateTargetCommand(method));
            waypointNode.then(methodNode);
        }
        listNode.then(waypointNode);
        dimensionNode.then(listNode);
        navigateNode.then(dimensionNode);
        return navigateNode;
    }

    private LiteralArgumentBuilder<S> textDisplayTransformationCommandNode() {
        LiteralArgumentBuilder<S> textDisplayNode = literal(NavigationMethod.TEXT_DISPLAY.id());
        LiteralArgumentBuilder<S> transformationNode = literal(TRANSFORMATION_COMMAND);
        LiteralArgumentBuilder<S> resetNode = literal(RESET_COMMAND);
        resetNode.executes(context -> {
            executeTextDisplayTransformationReset(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
        transformationNode.then(resetNode);
        transformationNode.then(this.textDisplayTransformationVectorNode(
                "translation",
                TRANSLATION_X_ARG,
                TRANSLATION_Y_ARG,
                TRANSLATION_Z_ARG,
                TextDisplayTransformation.MAX_TRANSLATION,
                this::executeTextDisplayTranslation
        ));
        transformationNode.then(this.textDisplayTransformationVectorNode(
                "rotation",
                ROTATION_X_ARG,
                ROTATION_Y_ARG,
                ROTATION_Z_ARG,
                TextDisplayTransformation.MAX_ROTATION_DEGREES,
                this::executeTextDisplayRotation
        ));
        transformationNode.then(this.textDisplayTransformationVectorNode(
                "scale",
                SCALE_X_ARG,
                SCALE_Y_ARG,
                SCALE_Z_ARG,
                TextDisplayTransformation.MAX_SCALE_MULTIPLIER,
                this::executeTextDisplayScale
        ));
        textDisplayNode.then(transformationNode);
        return textDisplayNode;
    }

    private LiteralArgumentBuilder<S> textDisplayTransformationVectorNode(
            String name,
            String xArgument,
            String yArgument,
            String zArgument,
            float maximum,
            BiConsumer<S, Vector3f> operation
    ) {
        RequiredArgumentBuilder<S, Float> zNode = argument(
                zArgument,
                floatArg(-maximum, maximum)
        );
        zNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationZSuggestion(context, zArgument)));
        zNode.executes(context -> {
            operation.accept(
                    context.getSource(),
                    new Vector3f(
                            getFloat(context, xArgument),
                            getFloat(context, yArgument),
                            getFloat(context, zArgument)
                    )
            );
            return Command.SINGLE_SUCCESS;
        });
        RequiredArgumentBuilder<S, Float> yNode = argument(
                yArgument,
                floatArg(-maximum, maximum)
        );
        yNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationYSuggestion(context, yArgument)));
        yNode.then(zNode);
        RequiredArgumentBuilder<S, Float> xNode = argument(
                xArgument,
                floatArg(-maximum, maximum)
        );
        xNode.suggests(singleFloatSuggestion((context, builder) -> this.getTextDisplayTransformationXSuggestion(context, xArgument)));
        xNode.then(yNode);
        LiteralArgumentBuilder<S> componentNode = literal(name);
        componentNode.then(xNode);
        return componentNode;
    }

    private Command<S> navigateTargetCommand(NavigationMethod method) {
        return context -> {
            executeNavigate(
                    context.getSource(),
                    getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    getString(context, WAYPOINT_NAME_ARG),
                    method
            );
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> navigateTargetWithAllCommand() {
        return context -> {
            executeNavigate(
                    context.getSource(),
                    getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    getString(context, WAYPOINT_NAME_ARG),
                    this.navigationService.supportedNavigationMethods()
            );
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> navigateTargetWithDefaultCommand() {
        return context -> {
            executeNavigate(
                    context.getSource(),
                    getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG),
                    getString(context, WAYPOINT_NAME_ARG),
                    CONFIG.defaultNavigationMethods()
            );
            return Command.SINGLE_SUCCESS;
        };
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
        LiteralArgumentBuilder<S> pageNode = listPageNode(
                scope,
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        LiteralArgumentBuilder<S> limitNode = listLimitNode(
                scope,
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        LiteralArgumentBuilder<S> viewNode = listViewNode(
                scope,
                WaypointSorting.SortMode.DEFAULT,
                false
        );
        if (scope == ListScope.DIMENSION) {
            searchNode.executes(reservedListCommand(SEARCH_COMMAND));
            sortNode.executes(reservedListCommand(SORT_COMMAND));
            pageNode.executes(reservedListCommand(PAGE_COMMAND));
            limitNode.executes(reservedListCommand(LIMIT_COMMAND));
            viewNode.executes(reservedListCommand(VIEW_COMMAND));
        }
        targetNode.then(searchNode);
        targetNode.then(sortNode);
        targetNode.then(pageNode);
        targetNode.then(limitNode);
        targetNode.then(viewNode);
    }

    private LiteralArgumentBuilder<S> listSearchNode(ListScope scope) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(listCommand(scope, WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listSortNode(scope));
        queryNode.then(listPageNode(scope, WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listLimitNode(scope, WaypointSorting.SortMode.DEFAULT, false));
        queryNode.then(listViewNode(scope, WaypointSorting.SortMode.DEFAULT, false));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        return trailingListSearchNode(scope, sortMode, reversed, true);
    }

    private LiteralArgumentBuilder<S> trailingListSearchNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            boolean groupByLists
    ) {
        RequiredArgumentBuilder<S, String> queryNode = argument(SEARCH_QUERY_ARG, string());
        queryNode.executes(listCommand(scope, sortMode, reversed, groupByLists));
        LiteralArgumentBuilder<S> searchNode = literal(SEARCH_COMMAND);
        return searchNode.then(queryNode);
    }

    private LiteralArgumentBuilder<S> listViewNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        LiteralArgumentBuilder<S> treeNode = literal(TREE_VIEW);
        treeNode.executes(listCommand(scope, sortMode, reversed, true));
        treeNode.then(trailingListSearchNode(scope, sortMode, reversed, true));

        LiteralArgumentBuilder<S> flatNode = literal(FLAT_VIEW);
        flatNode.executes(listCommand(scope, sortMode, reversed, false));
        flatNode.then(trailingListSearchNode(scope, sortMode, reversed, false));

        LiteralArgumentBuilder<S> viewNode = literal(VIEW_COMMAND);
        viewNode.then(treeNode);
        viewNode.then(flatNode);
        return viewNode;
    }

    private LiteralArgumentBuilder<S> listSortNode(ListScope scope) {
        LiteralArgumentBuilder<S> sortNode = literal(SORT_COMMAND);
        for (WaypointSorting.SortMode sortMode : WaypointSorting.SortMode.values()) {
            LiteralArgumentBuilder<S> modeNode = literal(sortMode.name().toLowerCase(Locale.ROOT));
            modeNode.executes(listCommand(scope, sortMode, false));
            if (sortMode != WaypointSorting.SortMode.DEFAULT) {
                modeNode.then(listOrderNode(scope, sortMode));
            }
            modeNode.then(trailingListSearchNode(scope, sortMode, false));
            modeNode.then(listPageNode(scope, sortMode, false));
            modeNode.then(listLimitNode(scope, sortMode, false));
            modeNode.then(listViewNode(scope, sortMode, false));
            sortNode.then(modeNode);
        }
        return sortNode;
    }

    private LiteralArgumentBuilder<S> listOrderNode(
            ListScope scope,
            WaypointSorting.SortMode sortMode
    ) {
        LiteralArgumentBuilder<S> orderNode = literal(ORDER_COMMAND);

        LiteralArgumentBuilder<S> ascendingNode = literal("ascending");
        ascendingNode.executes(listCommand(scope, sortMode, false));
        ascendingNode.then(trailingListSearchNode(scope, sortMode, false));
        ascendingNode.then(listPageNode(scope, sortMode, false));
        ascendingNode.then(listLimitNode(scope, sortMode, false));
        ascendingNode.then(listViewNode(scope, sortMode, false));
        orderNode.then(ascendingNode);

        LiteralArgumentBuilder<S> descendingNode = literal("descending");
        descendingNode.executes(listCommand(scope, sortMode, true));
        descendingNode.then(trailingListSearchNode(scope, sortMode, true));
        descendingNode.then(listPageNode(scope, sortMode, true));
        descendingNode.then(listLimitNode(scope, sortMode, true));
        descendingNode.then(listViewNode(scope, sortMode, true));
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
        pageNode.then(trailingListSearchNode(scope, sortMode, reversed));
        pageNode.then(listLimitNode(scope, sortMode, reversed));
        pageNode.then(listViewNode(scope, sortMode, reversed));
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
        limitNode.then(trailingListSearchNode(scope, sortMode, reversed));
        limitNode.then(listViewNode(scope, sortMode, reversed));
        LiteralArgumentBuilder<S> limitLiteral = literal(LIMIT_COMMAND);
        return limitLiteral.then(limitNode);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed
    ) {
        return listCommand(scope, sortMode, reversed, true);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            boolean groupByLists
    ) {
        return context -> {
            WaypointSorting.SortMode resolvedSortMode = !groupByLists
                    && sortMode == WaypointSorting.SortMode.DEFAULT
                    ? WaypointSorting.SortMode.NAME
                    : sortMode;
            executeList(context, scope, resolvedSortMode, reversed, null, groupByLists);
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
                    listName,
                    true
            );
            return Command.SINGLE_SUCCESS;
        };
    }

    private enum ListScope {
        CURRENT_DIMENSION,
        ALL_DIMENSIONS,
        DIMENSION,
        WAYPOINT_LIST
    }

    private void executeList(
            CommandContext<S> context,
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            String fixedListName,
            boolean groupByLists
    ) {
        S source = context.getSource();
        ListOptions options = new ListOptions(
                getOptionalString(context, SEARCH_QUERY_ARG, ""),
                sortMode,
                reversed,
                getOptionalInteger(context, PAGE_NUMBER_ARG, 1),
                getOptionalInteger(context, PAGE_LIMIT_ARG, CONFIG.defaultPageLimit()),
                groupByLists
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
                this.sender.sendError(source, translatable("waypoint.nonexist.list", parse(listName)));
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
        WaypointListDisplayModel.Display display = WaypointListDisplayModel.build(
                result,
                options.groupByLists()
        );
        if (result.waypointCount() == 0) {
            if (!options.filterText().trim().isEmpty()) {
                this.sender.sendMessage(
                        source,
                        translatable("waypoint.search.no_results", text(options.filterText()))
                );
                return;
            }
            if (target.listName() != null) {
                WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(target.dimensionName());
                WaypointList waypointList = fileManager == null
                        ? null
                        : fileManager.getWaypointListByName(target.listName());
                Component listName = waypointList == null
                        ? text(target.listName())
                        : parse(waypointList.displayName());
                this.sender.sendMessage(source, translatable("waypoint.empty.list", listName));
                return;
            }
            if (target.allDimensions() && result.listCount() > 0 && display.groupByLists()) {
                this.sender.sendMessage(source, getListDisplayText(
                        source,
                        display,
                        display.dimensions(),
                        false,
                        target,
                        options
                ));
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

        Component listText = getListDisplayText(
                source,
                page.display(),
                page.dimensions(),
                target.listName() != null,
                target,
                options
        );
        listText = listText.append(getListViewToggleButton(target, options)).appendSpace()
                .append(getListSearchButton(target, options)).appendSpace()
                .append(getListSortControls(target, options));
        if (page.totalPages() > 1) {
            listText = listText.append(getPageNavigation(
                    target,
                    options,
                    page.totalPages(),
                    page.totalWaypoints()
            ));
        }
        this.sender.sendMessage(source, listText);
    }

    private Component getListDisplayText(
            S source,
            WaypointListDisplayModel.Display display,
            List<WaypointListDisplayModel.DisplayDimension> dimensions,
            boolean listOnly,
            ListTarget target,
            ListOptions options
    ) {
        boolean withEdit = hasEditPermission(source);
        boolean withRemove = hasRemovePermission(source);
        boolean withTp = hasTpPermission(source);
        if (!display.groupByLists()) {
            Component listText = text("").appendNewline();
            for (WaypointListDisplayModel.DisplayWaypoint waypoint : display.flatWaypoints()) {
                String dimensionName = waypoint.dimensionName();
                String listName = waypoint.sourceList().name();
                listText = listText.append(dimensionNameWithColor(dimensionName))
                        .append(text(" / ", NamedTextColor.DARK_GRAY))
                        .append(parse(waypoint.sourceList().displayName()).colorIfAbsent(NamedTextColor.GRAY))
                        .append(text(" / ", NamedTextColor.DARK_GRAY))
                        .append(getWaypointText(
                                waypoint.waypoint(),
                                dimensionName,
                                listName,
                                0,
                                withEdit,
                                withRemove,
                                withTp
                        ))
                        .appendNewline();
            }
            return listText;
        }
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
        for (WaypointListDisplayModel.DisplayDimension dimension : dimensions) {
            Component dimensionTitle = dimensionNameWithColor(dimension.dimensionName());
            if (target.allDimensions()) {
                dimensionTitle = dimensionTitle
                        .clickEvent(ClickEvent.runCommand(listDimensionCmd(
                                dimension.dimensionName(),
                                options
                        )))
                        .hoverEvent(HoverEvent.showText(translatable(
                                "button.list.dimension",
                                dimensionNameWithColor(dimension.dimensionName())
                        )));
            }
            listText = listText.append(dimensionTitle).appendNewline();
            if (dimension.lists().isEmpty()) {
                listText = listText.append(text("  ...", NamedTextColor.DARK_GRAY)).appendNewline();
                continue;
            }
            for (WaypointListDisplayModel.DisplayList list : dimension.lists()) {
                listText = listText.append(getWaypointListText(
                        list.sourceList(),
                        list.waypoints(),
                        dimension.dimensionName(),
                        1,
                        true,
                        withEdit,
                        withRemove,
                        withTp,
                        listWaypointListCmd(
                                dimension.dimensionName(),
                                list.sourceList().name(),
                                options
                        )
                ));
            }
        }
        return listText;
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
                this.waypointServer.saveWaypointFile(fileManager);
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
                for (WaypointList list : fileManager.getWaypointListMap().values()) {
                    String listName = list.name();
                    if (listName.startsWith(currentInput)) {
                        builder.suggest(
                                escapeListName(listName),
                                getMessageFromComponent(parse(list.displayName()))
                        );
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
                        builder.suggest(
                                _959.server_waypoint.util.StringCommandBuilder.escapeArgument(name),
                                getMessageFromComponent(parse(waypoint.displayName()))
                        );
                    }
                }
                return builder.buildFuture();
            }
        }
    }

    private CompletableFuture<Suggestions> getInitialsSuggestions(CommandContext<S> context, SuggestionsBuilder builder, String argName) {
        String name = plainText(getString(context.getLastChild(), argName));
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

    private SuggestionProvider<S> singleFloatSuggestion(BiFunction<CommandContext<S>, SuggestionsBuilder, Float> floatProvider) {
        return (context, builder) -> {
            builder.suggest(String.valueOf(floatProvider.apply(context, builder)));
            return builder.buildFuture();
        };
    }

    private float getTextDisplayTransformationXSuggestion(CommandContext<S> context, String transformationArg) {
        P player = getPlayer(context.getSource());
        if (player == null) return 0F;
        NavigationSession session = this.navigationService.status(player).session();
        if (session != null) {
            switch (transformationArg) {
                case TRANSLATION_X_ARG, TRANSLATION_Y_ARG, TRANSLATION_Z_ARG: return session.textDisplayTransformation().translation().x;
                case ROTATION_X_ARG, ROTATION_Y_ARG, ROTATION_Z_ARG: return session.textDisplayTransformation().rotation().x;
                case SCALE_X_ARG, SCALE_Y_ARG, SCALE_Z_ARG: return session.textDisplayTransformation().scale().x;
            }
        }
        return 0F;
    }

    private float getTextDisplayTransformationYSuggestion(CommandContext<S> context, String transformationArg) {
        P player = getPlayer(context.getSource());
        if (player == null) return 0F;
        NavigationSession session = this.navigationService.status(player).session();
        if (session != null) {
            switch (transformationArg) {
                case TRANSLATION_X_ARG, TRANSLATION_Y_ARG, TRANSLATION_Z_ARG: return session.textDisplayTransformation().translation().y;
                case ROTATION_X_ARG, ROTATION_Y_ARG, ROTATION_Z_ARG: return session.textDisplayTransformation().rotation().y;
                case SCALE_X_ARG, SCALE_Y_ARG, SCALE_Z_ARG: return session.textDisplayTransformation().scale().y;
            }
        }
        return 0F;
    }

    private float getTextDisplayTransformationZSuggestion(CommandContext<S> context, String transformationArg) {
        P player = getPlayer(context.getSource());
        if (player == null) return 0F;
        NavigationSession session = this.navigationService.status(player).session();
        if (session != null) {
            switch (transformationArg) {
                case TRANSLATION_X_ARG, TRANSLATION_Y_ARG, TRANSLATION_Z_ARG: return session.textDisplayTransformation().translation().z;
                case ROTATION_X_ARG, ROTATION_Y_ARG, ROTATION_Z_ARG: return session.textDisplayTransformation().rotation().z;
                case SCALE_X_ARG, SCALE_Y_ARG, SCALE_Z_ARG: return session.textDisplayTransformation().scale().z;
            }
        }
        return 0F;
    }
}
