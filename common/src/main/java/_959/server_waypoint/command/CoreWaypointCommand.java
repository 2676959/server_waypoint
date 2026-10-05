package _959.server_waypoint.command;

import _959.server_waypoint.text.chat.ChatIcons;
import _959.server_waypoint.core.logging.PlayerActionLog;
import _959.server_waypoint.crossserver.authorization.RemotePermissions;
import _959.server_waypoint.crossserver.handoff.RemoteTeleportInitiator;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.crossserver.pairing.StaticKeyGenerator;

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
import _959.server_waypoint.core.network.ChunkedMessageSendResult;
import _959.server_waypoint.core.network.PlatformMessageSender;
import _959.server_waypoint.core.network.MessageEncodingException;
import _959.server_waypoint.core.network.buffer.UploadRequestBuffer;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.network.message.WaypointListUpdateMessage;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.core.network.upload.UploadConflictPolicy;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.network.upload.UploadScope;
import _959.server_waypoint.core.network.upload.UploadTarget;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.core.waypoint.WaypointSorting;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.navigation.TextDisplayTransformation;
import _959.server_waypoint.core.restore.WaypointRestoreRegistry;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListView;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.DimensionScreens;
import _959.server_waypoint.text.feedback.Errors;
import _959.server_waypoint.text.feedback.Broadcasts;
import _959.server_waypoint.text.feedback.DetailsScreen;
import _959.server_waypoint.text.feedback.NavigationScreens;
import _959.server_waypoint.text.feedback.UploadScreens;
import _959.server_waypoint.text.feedback.WaypointRefs;
import _959.server_waypoint.text.feedback.Results;
import _959.server_waypoint.text.feedback.ListScreen;
import _959.server_waypoint.text.feedback.PickerScreens;
import _959.server_waypoint.text.feedback.HelpScreen;
import _959.server_waypoint.text.feedback.HelpTopics;
import _959.server_waypoint.text.feedback.MenuScreen;
import _959.server_waypoint.text.feedback.PlacedWaypoint;
import _959.server_waypoint.util.TriConsumer;
import _959.server_waypoint.util.WaypointInitials;
import _959.server_waypoint.util.NamespacedId;
import _959.server_waypoint.core.waypoint.WaypointIconPolicy;
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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.*;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.waypoint.WaypointList.SERVER_N;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.ADD_LIST;
import static _959.server_waypoint.core.waypoint.WaypointModificationType.REMOVE_LIST;
import static _959.server_waypoint.text.FormattedTextHelper.*;
import static _959.server_waypoint.translation.LanguageFilesManager.getExternalLoadedLanguages;
import static _959.server_waypoint.util.ColorUtils.*;
import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;
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

public abstract class CoreWaypointCommand<S, K, P, D, B, I> {
    protected final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;
    private final WaypointQueryEngine waypointQueryEngine;
    private final RemoteWaypointCommand<S> remoteCommand;
    private volatile RemoteTeleportInitiator<S> remoteTeleport = (source, selection, feedback) -> feedback.accept(Result.UNSUPPORTED);

    private final PermissionKeys<K> permissionKeys;
    private final PermissionManager<S, K, P> permissionManager;
    private final NavigationService<P> navigationService;
    private final WaypointRestoreRegistry<String> restoreRegistry;
    private final Supplier<ArgumentType<D>> dimensionArgumentProvider;
    private final Supplier<ArgumentType<B>> blockPosArgumentProvider;
    private final Supplier<ArgumentType<I>> iconArgumentProvider;
    private final UploadCoordinator<P> uploadCoordinator;
    private final SuggestionProvider<S> WAYPOINT_NAME_SUGGESTION = new WaypointNameSuggestion();
    private final SuggestionProvider<S> WAYPOINT_LIST_SUGGESTION = new WaypointListSuggestion();
    private final SuggestionProvider<S> NAME_INITIALS_SUGGESTION = new NameInitialsSuggestion();
    private final SuggestionProvider<S> PLAYER_YAW_SUGGESTION = new PlayerYawSuggestion();
    private final SuggestionProvider<S> HEX_COLOR_CODE_SUGGESTION = new HexColorCodeSuggestion();
    private final SuggestionProvider<S> ICON_SUGGESTION = this::suggestIconIds;
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
    public static final String UPLOAD_SOURCE_ARG = "source";
    public static final String TP_COMMAND = "tp";
    public static final String RELOAD_COMMAND = "reload";
    public static final String GENERATE_KEY_COMMAND = "sw-cross-server-keygen";
    public static final String NAVIGATE_COMMAND = "navigate";
    public static final String USE_COMMAND = "use";
    public static final String DISABLE_COMMAND = "disable";
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
    public static final String ICON_ARG = "icon";
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
            Supplier<ArgumentType<B>> blockPositionArgument,
            Supplier<ArgumentType<I>> iconArgument
    ) {
        this.waypointServer = waypointServer;
        this.waypointQueryEngine = new WaypointQueryEngine(waypointServer);
        this.sender = sender;
        var remotePermissions = new RemotePermissions<>(permissionManager, () -> CONFIG.CommandPermission(), this::getPlayer);
        this.remoteCommand = new RemoteWaypointCommand<>(waypointServer::remoteCatalogStore, sender::sendMessage,
                sender::sendError, () -> CONFIG.defaultPageLimit(),
                remotePermissions::canList, remotePermissions::canRequestTeleport,
                (source, selection, feedback) -> {
                    PlayerActionLog.Context actor = actor(source);
                    var key = selection.key();
                    PlayerActionLog.log(actor, "remote_tp", "requested", "server", key.serverId().value(),
                            "dimension", key.dimensionName(), "list", key.listName(), "waypoint", key.waypointName());
                    remoteTeleport.initiate(source, selection, result -> {
                        PlayerActionLog.log(actor, "remote_tp", result.name().toLowerCase(Locale.ROOT),
                                "server", key.serverId().value(), "dimension", key.dimensionName(),
                                "list", key.listName(), "waypoint", key.waypointName());
                        feedback.accept(result);
                    });
                },
                source -> HelpScreen.topic(this.viewer(source), HelpTopics.Topic.REMOTE, false), this::viewer);
        this.permissionManager = permissionManager;
        this.navigationService = Objects.requireNonNull(navigationService, "navigationService");
        this.restoreRegistry = new WaypointRestoreRegistry<>();
        this.uploadCoordinator = Objects.requireNonNull(uploadCoordinator, "uploadCoordinator");
        this.dimensionArgumentProvider = dimensionArgument;
        this.blockPosArgumentProvider = blockPositionArgument;
        this.iconArgumentProvider = iconArgument;
        this.permissionKeys = permissionManager.keys;
    }

    /** Install the connection-scoped source service; the lifecycle owner must close it before replacement. */
    public final void setRemoteTeleportInitiator(RemoteTeleportInitiator<S> initiator) {
        this.remoteTeleport = Objects.requireNonNull(initiator, "initiator");
    }

    protected abstract String toDimensionName(D dimensionArgument);
    protected abstract NamespacedId toIconId(I iconArgument);
    protected abstract CompletableFuture<Suggestions> suggestIconIds(CommandContext<S> context, SuggestionsBuilder builder);
    protected abstract WaypointPos toWaypointPos(S source, B blockPositionArgument);
    protected abstract boolean isDimensionValid(S source, D dimensionArgument);
    protected abstract void executeByServer(S source, Runnable task);
    protected abstract D getSourceDimension(S source);
    protected abstract WaypointPos getSourcePosition(S source);
    protected abstract float getSourceYaw(S source);
    protected abstract @Nullable P getPlayer(S source);
    protected abstract boolean isServerConsoleWithHighestPermission(S source);
    protected abstract String getPlayerName(P player);
    /** The player's display name, such as a nickname or team prefix; {@link #getPlayerName} is the account name. */
    protected abstract Component getPlayerDisplayName(P player);
    protected abstract CompletionStage<Boolean> teleportPlayer(S source, P player, D dimensionArgument, WaypointPos pos, int yaw);
    protected abstract Message getMessageFromComponent(Component component);
    protected abstract List<String> getAvailableDimensionNames(S source);
    /** Each loaded dimension and its dimension type ID, such as minecraft:the_nether. */
    protected abstract Map<String, String> getDimensionTypes(S source);

    protected ChatIcons chatIcons() {
        return ChatIcons.NONE;
    }

    /** The name after the player's head where the platform draws heads; the space belongs to the head. */
    private Component playerName(P player, Component name) {
        Component head = chatIcons().playerHead(this.sender.playerActor(player).playerId());
        return head == null ? name : Component.empty().append(head.color(NamedTextColor.WHITE).appendSpace()).append(name);
    }

    /** Who made a change, as broadcasts name them: the player by account name, otherwise the sender. */
    private Component actorName(S source) {
        P player = getPlayer(source);
        return player == null ? this.sender.getSenderName(source) : playerName(player, text(getPlayerName(player)));
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

    /**
     * Whoever reads this source's feedback, built once per command source (spec 17). What the viewer
     * may do comes from the source the feedback is viewed from; where the command runs, from the
     * source itself.
     */
    protected final Viewer viewer(S source) {
        Set<Viewer.Permission> permissions = permissions(this.sender.viewingSource(source));
        P player = getPlayer(source);
        boolean hasMod = player != null
                && (this.sender.canSendChunkedMessage(player) || usesLocalUpload(source, player));
        return new Viewer(permissions, hasMod, this.sender.isPlainTextReceiver(source),
                toDimensionName(getSourceDimension(source)), getSourcePosition(source), getSourceYaw(source), chatIcons());
    }

    /**
     * What this source may do, without reading where it is: requirement checks run for sources
     * that have no level, such as the one Paper's help map uses at startup.
     */
    private Set<Viewer.Permission> permissions(S source) {
        Set<Viewer.Permission> permissions = EnumSet.noneOf(Viewer.Permission.class);
        if (hasAddPermission(source)) permissions.add(Viewer.Permission.ADD);
        if (hasEditPermission(source)) permissions.add(Viewer.Permission.EDIT);
        if (hasRemovePermission(source)) permissions.add(Viewer.Permission.REMOVE);
        if (hasTpPermission(source)) permissions.add(Viewer.Permission.TP);
        if (hasNavigatePermission(source)) permissions.add(Viewer.Permission.NAVIGATE);
        if (hasReloadPermission(source)) permissions.add(Viewer.Permission.RELOAD);
        if (hasUploadPermission(source)) permissions.add(Viewer.Permission.UPLOAD);
        if (hasUploadDeletePermission(source)) permissions.add(Viewer.Permission.UPLOAD_DELETE);
        if (this.remoteCommand.canList(source)) permissions.add(Viewer.Permission.REMOTE_LIST);
        if (this.remoteCommand.canTeleport(source)) permissions.add(Viewer.Permission.REMOTE_TP);
        return permissions;
    }

    protected final DimensionStyle dimensions(S source, Viewer viewer) {
        return DimensionStyle.local(viewer, getDimensionTypes(source));
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
        LiteralArgumentBuilder<Object> iconNode = literal("icon");
        iconNode.then(RequiredArgumentBuilder.<Object, I>argument(ICON_ARG, iconArgumentProvider.get())
                .suggests((SuggestionProvider<Object>) ICON_SUGGESTION)
                .executes(command));
        visibilityNode.then(iconNode);
        keywordsNode.then(literal("icon").then(RequiredArgumentBuilder.<Object, I>argument(
                ICON_ARG, iconArgumentProvider.get())
                .suggests((SuggestionProvider<Object>) ICON_SUGGESTION).executes(command)));
        descriptionNode.then(literal("icon").then(RequiredArgumentBuilder.<Object, I>argument(
                ICON_ARG, iconArgumentProvider.get())
                .suggests((SuggestionProvider<Object>) ICON_SUGGESTION).executes(command)));
        keywordsNode.then(descriptionNode);
        visibilityNode.then(keywordsNode);
        return visibilityNode;
    }

    private ArgumentBuilder<S, ?> dimensionNode() {
        return argument(DIMENSION_ARG, this.dimensionArgumentProvider.get());
    }

    private RequiredArgumentBuilder<S, String> sourceNode() {
        return RequiredArgumentBuilder.<S, String>argument(UPLOAD_SOURCE_ARG, StringArgumentType.word())
                .suggests((context, builder) -> {
                    builder.suggest("xaero");
                    builder.suggest("voxelmap");
                    return builder.buildFuture();
                });
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
        root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.details", HelpTopics.Topic.DETAILS));
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
        root.executes(context -> executeTargetHint(context.getSource(), "wp.hint_line.edit", HelpTopics.Topic.EDIT));
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
                .executes(context -> executePicker(context, true))
                .then(RequiredArgumentBuilder.<S, String>argument(COLOR_ARG, string())
                        .suggests(this.HEX_COLOR_CODE_SUGGESTION)
                        .executes(context -> {
                            String input = getString(context, COLOR_ARG);
                            int color = RANDOM_COLOR.equals(input) ? randomColor() : colorNameOrHexCodeToRgb(input, false);
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
                .executes(context -> executePicker(context, false))
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
        set.then(LiteralArgumentBuilder.<S>literal("icon")
                .then(RequiredArgumentBuilder.<S, I>argument(VALUE_ARG, iconArgumentProvider.get())
                        .suggests(ICON_SUGGESTION)
                        .executes(context -> {
                            NamespacedId icon = validateIcon(context.getSource(), getArgument(context, VALUE_ARG));
                            if (icon != null) {
                                this.executeWaypointPatch(context.getSource(), this.getArgument(context, DIMENSION_ARG),
                                        getString(context, LIST_NAME_ARG), getString(context, WAYPOINT_NAME_ARG),
                                        patchWithIcon(PatchField.set(icon)));
                            }
                            return Command.SINGLE_SUCCESS;
                        })));

        LiteralArgumentBuilder<S> clear = literal(CLEAR_COMMAND);
        clear.then(this.waypointClearPatchNode("display-name", WaypointPatchProperty.DISPLAY_NAME));
        clear.then(this.waypointClearPatchNode("keywords", WaypointPatchProperty.KEYWORDS));
        clear.then(this.waypointClearPatchNode("description", WaypointPatchProperty.DESCRIPTION));
        clear.then(LiteralArgumentBuilder.<S>literal("icon").executes(context -> {
            this.executeWaypointPatch(context.getSource(), this.getArgument(context, DIMENSION_ARG),
                    getString(context, LIST_NAME_ARG), getString(context, WAYPOINT_NAME_ARG),
                    patchWithIcon(PatchField.clear()));
            return Command.SINGLE_SUCCESS;
        }));
        waypoint.then(set).then(clear);
        list.then(waypoint);
        dimension.then(list);
        target.then(dimension);
        return target;
    }

    /** set color or set yaw without a value: the colour or facing picker. */
    private int executePicker(CommandContext<S> context, boolean color) {
        S source = context.getSource();
        runWithSelectorTarget(source, getArgument(context, DIMENSION_ARG), getString(context, LIST_NAME_ARG),
                getString(context, WAYPOINT_NAME_ARG), (fileManager, list, waypoint) -> {
                    Viewer viewer = viewer(source);
                    DimensionStyle dims = dimensions(source, viewer);
                    this.sender.sendMessage(source, color
                            ? PickerScreens.color(dims, fileManager.getDimensionName(), list, waypoint)
                            : PickerScreens.facing(dims, fileManager.getDimensionName(), list, waypoint));
                });
        return Command.SINGLE_SUCCESS;
    }

    /** /wp add without arguments: the lists of the player's dimension to add a waypoint where they stand. */
    private int executeAddPicker(S source, int page) {
        if (getPlayer(source) == null) {
            this.sender.sendError(source, Errors.playerOnly());
            return 0;
        }
        Viewer viewer = viewer(source);
        String dimension = toDimensionName(getSourceDimension(source));
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimension);
        this.sender.sendMessage(source, PickerScreens.add(dimensions(source, viewer), dimension,
                fileManager == null ? List.of() : fileManager.getWaypointLists(), page, CONFIG.defaultPageLimit()));
        return Command.SINGLE_SUCCESS;
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
                .executes(context -> {
                    executeMenu((S) context.getSource());
                    return Command.SINGLE_SUCCESS;
                })
                .then((ArgumentBuilder<Object, ?>) helpCommandNode())
                .then(literal(ADD_COMMAND)
                        .requires(source -> hasAddPermission((S) source))
                        .executes(context -> executeAddPicker((S) context.getSource(), 1))
                        .then(literal(PAGE_COMMAND)
                                .then(argument(PAGE_NUMBER_ARG, integer(1))
                                        .executes(context -> executeAddPicker((S) context.getSource(),
                                                getInteger(context, PAGE_NUMBER_ARG)))))
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
                                                        .executes(cxt -> {
                                                            CommandContext<S> context = (CommandContext<S>) cxt;
                                                            executeQuickAddWaypoint(
                                                                    context.getSource(),
                                                                    getArgument(context, DIMENSION_ARG),
                                                                    getArgument(context, POS_ARG),
                                                                    getString(context, LIST_NAME_ARG),
                                                                    getString(context, WAYPOINT_NAME_ARG)
                                                            );
                                                            return Command.SINGLE_SUCCESS;
                                                        })
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
                                                                                                    getOptionalString(context, DESCRIPTION_ARG, ""),
                                                                                                    hasArgument(context, ICON_ARG) ? getArgument(context, ICON_ARG) : null
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
                                                                                                    getOptionalString(context, DESCRIPTION_ARG, ""),
                                                                                                    hasArgument(context, ICON_ARG) ? getArgument(context, ICON_ARG) : null
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
                        .executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.remove",
                                HelpTopics.Topic.REMOVE))
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
                        .executes(context -> executeTargetHint((S) context.getSource(), "wp.hint_line.tp",
                                HelpTopics.Topic.TP))
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
                .then((ArgumentBuilder<Object, ?>) remoteCommand.build())
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
        upload.executes(context -> executeUploadPanel(context.getSource()));
        RequiredArgumentBuilder<S, String> source = sourceNode();
        source.executes(context -> executeUploadAndReturn(
                context.getSource(), getString(context, UPLOAD_SOURCE_ARG),
                UploadConflictPolicy.SERVER, false, UploadScope.WORLD, null, null, null
        ));
        source.then(uploadSelectorArguments(UploadConflictPolicy.SERVER, false));

        LiteralArgumentBuilder<S> force = literal(UPLOAD_FORCE_COMMAND);
        LiteralArgumentBuilder<S> server = literal(UPLOAD_SERVER_COMMAND);
        server.executes(context -> executeUploadAndReturn(
                context.getSource(), getString(context, UPLOAD_SOURCE_ARG),
                UploadConflictPolicy.SERVER, false, UploadScope.WORLD, null, null, null
        ));
        server.then(uploadSelectorArguments(UploadConflictPolicy.SERVER, false));
        force.then(server);

        LiteralArgumentBuilder<S> local = literal(UPLOAD_LOCAL_COMMAND);
        local.executes(context -> executeUploadAndReturn(
                context.getSource(), getString(context, UPLOAD_SOURCE_ARG),
                UploadConflictPolicy.LOCAL, false, UploadScope.WORLD, null, null, null
        ));
        local.then(uploadSelectorArguments(UploadConflictPolicy.LOCAL, false));

        LiteralArgumentBuilder<S> delete = literal(UPLOAD_DELETE_COMMAND);
        delete.requires(this::hasUploadDeletePermission);
        delete.executes(context -> executeUploadAndReturn(
                context.getSource(), getString(context, UPLOAD_SOURCE_ARG),
                UploadConflictPolicy.LOCAL, true, UploadScope.WORLD, null, null, null
        ));
        delete.then(uploadSelectorArguments(UploadConflictPolicy.LOCAL, true));
        local.then(delete);
        force.then(local);
        source.then(force);
        upload.then(source);
        return upload;
    }

    private int executeUploadAndReturn(
            S source,
            String uploadSource,
            UploadConflictPolicy conflictPolicy,
            boolean deleteMissing,
            UploadScope scope,
            @Nullable D dimensionArgument,
            @Nullable String listName,
            @Nullable String waypointName
    ) {
        executeUpload(
                source, uploadSource, conflictPolicy, deleteMissing,
                scope, dimensionArgument, listName, waypointName
        );
        return Command.SINGLE_SUCCESS;
    }

    /** /wp upload: the map mods and their modes, for a player whose client has the mod. */
    private int executeUploadPanel(S source) {
        if (getPlayer(source) == null) {
            this.sender.sendError(source, Errors.playerOnly());
            return 0;
        }
        Viewer viewer = viewer(source);
        if (!viewer.hasMod()) {
            this.sender.sendError(source, Errors.of("wp.error.upload.no_mod"));
            return 0;
        }
        this.sender.sendMessage(source, UploadScreens.panel(viewer));
        return Command.SINGLE_SUCCESS;
    }

    /** The menu for players; the help index for plain-text viewers, since the menu is all links. */
    private void executeMenu(S source) {
        Viewer viewer = viewer(source);
        if (viewer.plainText()) {
            this.sender.sendMessage(source, HelpScreen.index(viewer));
            return;
        }
        boolean remoteAvailable = viewer.can(Viewer.Permission.REMOTE_LIST)
                && !this.waypointServer.remoteCatalogStore().snapshot().isEmpty();
        this.sender.sendMessage(source, MenuScreen.menu(viewer, dimensions(source, viewer),
                navigationTarget(source), remoteAvailable));
    }

    private LiteralArgumentBuilder<S> helpCommandNode() {
        LiteralArgumentBuilder<S> help = literal(HELP_COMMAND);
        help.executes(context -> {
            this.sender.sendMessage(context.getSource(), HelpScreen.index(viewer(context.getSource())));
            return Command.SINGLE_SUCCESS;
        });
        for (HelpTopics.Topic topic : HelpTopics.Topic.values()) {
            help.then(LiteralArgumentBuilder.<S>literal(topic.id())
                    .requires(source -> topic.readableBy(new Viewer(permissions(source), false, false, null, null, 0F)))
                    .executes(context -> {
                        this.sender.sendMessage(context.getSource(), HelpScreen.topic(viewer(context.getSource()), topic,
                                this.isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)));
                        return Command.SINGLE_SUCCESS;
                    }));
        }
        return help;
    }

    /** The player's navigation target, if they may navigate and are navigating. */
    private @Nullable PlacedWaypoint navigationTarget(S source) {
        P player = getPlayer(source);
        if (player == null || !hasNavigatePermission(source)) {
            return null;
        }
        NavigationSession session = this.navigationService.status(player).session();
        return session == null ? null : place(session.target());
    }

    /** The live waypoint behind a navigation target, or a stand-in made from the target when it is gone. */
    private PlacedWaypoint place(NavigationTarget target) {
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(target.dimensionName());
        WaypointList list = fileManager == null ? null : fileManager.getWaypointListByName(target.listName());
        SimpleWaypoint waypoint = list == null ? null : list.getWaypointByName(target.waypointName());
        if (list == null || waypoint == null) {
            list = new WaypointList(target.listName(), target.listDisplayName(), 0, List.of());
            waypoint = new SimpleWaypoint(target.waypointName(), target.waypointDisplayName(),
                    WaypointInitials.getDefaultInitials(plainText(target.waypointDisplayName())), target.position(),
                    target.rgb(), 0, true, target.waypointKeywords(), target.waypointDescription());
        }
        return new PlacedWaypoint(target.dimensionName(), list, waypoint);
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
        if (isDimensionValid(source, dimensionArgument)) {
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionName);
            if (fileManager == null) {
                this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
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
                this.sender.sendError(source, Errors.noList(dimensions(source), fileManager.getDimensionName(), listName));
            } else if (waypointList.isEmpty()) {
                foundEmptyAction.accept(fileManager, waypointList);
            } else {
                foundAction.accept(fileManager, waypointList);
            }
        });
    }

    private void runWithSelectorTarget(S source, D dimensionArgument, String listName, String name, TriConsumer<@NotNull WaypointFileManager, @NotNull WaypointList, @NotNull SimpleWaypoint> action) {
        BiConsumer<WaypointFileManager, WaypointList> missing = (fileManager, waypointList) -> this.sender.sendError(source,
                Errors.noWaypoint(dimensions(source), fileManager.getDimensionName(), listName, name));
        runWithSelectorTarget(source, dimensionArgument, listName, (fileManager, waypointList) -> {
            SimpleWaypoint waypoint = waypointList.getWaypointByName(name);
            if (waypoint == null) {
                missing.accept(fileManager, waypointList);
            } else {
                action.accept(fileManager, waypointList, waypoint);
            }
        }, missing);
    }

    private void sendDimensionError(S source, String dimensionName) {
        this.sender.sendError(source, Errors.noDimension(dimensions(source), dimensionName));
    }

    private void sendPosArgumentError(S source) {
        this.sender.sendError(source, Errors.of("wp.error.position"));
    }

    private void sendHexColorCodeError(S source, String hexColorCode) {
        this.sender.sendError(source, Errors.of("wp.error.color", text(hexColorCode)));
    }

    private DimensionStyle dimensions(S source) {
        return dimensions(source, viewer(source));
    }

    /** A player who reads a broadcast: whether they may teleport is known, where they stand isn't. */
    private Viewer recipientViewer(P player) {
        boolean teleport = this.permissionManager.checkPlayerPermission(player, this.permissionKeys.tp(),
                CONFIG.CommandPermission().tp());
        return new Viewer(teleport ? Set.of(Viewer.Permission.TP) : Set.of(), false, false, null, null, 0F, chatIcons());
    }

    /**
     * Sends a change to every client, and a chat line about it to every other player (spec 13). Each
     * line is built for its reader.
     */
    private void broadcast(S source, ChunkedMessage update, Function<DimensionStyle, Component> line) {
        Iterable<? extends P> recipients = this.sender.getBroadcastPlayers(source);
        P actor = getPlayer(source);
        Map<String, String> types = getDimensionTypes(source);
        for (P player : recipients) {
            if (!player.equals(actor)) {
                this.sender.sendPlayerMessage(player, line.apply(DimensionStyle.local(recipientViewer(player), types)));
            }
        }
        this.sender.broadcastChunkedMessage(recipients, update);
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
            this.sender.sendError(source, Errors.of("wp.error.formatted_text"));
            return false;
        }
        if (keywords != null) {
            if (keywords.size() > MAX_KEYWORDS) {
                this.sender.sendError(source, Errors.of("wp.error.keywords.too_many", text(MAX_KEYWORDS)));
                return false;
            }
            if (hasDuplicateKeywords(keywords)) {
                this.sender.sendError(source, Errors.of("wp.error.keywords.duplicate"));
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
        this.sender.sendError(source, Errors.of("wp.error.too_long", text(argument), text(maximum)));
        return false;
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
                property == WaypointPatchProperty.DESCRIPTION ? field : PatchField.unchanged(), PatchField.unchanged());
    }

    private static WaypointPatch patchWithPosition(WaypointPos position) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.set(position), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }

    private static WaypointPatch patchWithColor(int color) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.set(color), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }

    private static WaypointPatch patchWithYaw(int yaw) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.set(yaw),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }

    private static WaypointPatch patchWithVisibility(boolean global) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.set(global), PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged());
    }

    private static WaypointPatch patchWithIcon(PatchField<NamespacedId> icon) {
        return new WaypointPatch(
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(),
                PatchField.unchanged(), PatchField.unchanged(), PatchField.unchanged(), icon
        );
    }

    private @Nullable NamespacedId validateIcon(S source, I value) {
        try {
            NamespacedId icon = WaypointIconPolicy.validate(toIconId(value));
            if (!this.waypointServer.isWaypointIconValid(icon)) {
                throw new IllegalArgumentException("Unknown waypoint icon");
            }
            return icon;
        } catch (IllegalArgumentException invalid) {
            this.sender.sendError(source, Errors.of("wp.error.icon", text(value.toString())));
            return null;
        }
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
                            this.sendEditError(source, result.status(), dimensionName, listIdentifier, null);
                            return;
                        }
                        WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                        WaypointList before = Objects.requireNonNull(result.beforeSnapshot());
                        WaypointList after = Objects.requireNonNull(result.afterSnapshot());
                        saveChanges(source, fileManager, "list_edit", "dimension", dimensionName, "list", before.name(), "new_list", after.name());
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
                        Component actor = actorName(source);
                        this.broadcast(source, update, dims -> Broadcasts.updatedList(dims, actor, dimensionName, after));
                        this.sender.sendMessage(source, DetailsScreen.list(dimensions(source), dimensionName,
                                after, DetailsScreen.updated(patch)));
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
                            this.sendEditError(source, result.status(), dimensionName, listIdentifier, waypointIdentifier);
                            return;
                        }
                        WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                        WaypointList list = Objects.requireNonNull(result.listSnapshot());
                        SimpleWaypoint before = Objects.requireNonNull(result.beforeSnapshot());
                        SimpleWaypoint after = Objects.requireNonNull(result.afterSnapshot());
                        saveChanges(source, fileManager, "edit", "dimension", dimensionName, "list", list.name(), "waypoint", before.name(), "new_waypoint", after.name());
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
                        Component actor = actorName(source);
                        this.broadcast(source, update, dims -> Broadcasts.updated(dims, actor, dimensionName, list, after));
                        this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source), dimensionName,
                                list, after, DetailsScreen.updated(patch)));
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
        this.sender.sendError(source, Errors.of("wp.error.encoding"));
    }

    private void executeListDetails(S source, D dimensionArgument, String listIdentifier) {
        BiConsumer<WaypointFileManager, WaypointList> action = (fileManager, waypointList) -> {
            Viewer viewer = viewer(source);
            this.sender.sendMessage(source, DetailsScreen.list(dimensions(source, viewer), fileManager.getDimensionName(),
                    waypointList, null));
        };
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, action, action);
    }

    private void executeWaypointDetails(
            S source,
            D dimensionArgument,
            String listIdentifier,
            String waypointIdentifier
    ) {
        runWithSelectorTarget(source, dimensionArgument, listIdentifier, waypointIdentifier,
                (fileManager, waypointList, waypoint) -> {
                    Viewer viewer = viewer(source);
                    this.sender.sendMessage(source, DetailsScreen.waypoint(dimensions(source, viewer),
                            fileManager.getDimensionName(), waypointList, waypoint, null));
                });
    }

    private void sendEditError(S source, EditResultStatus status, String dimension, String list, @Nullable String waypoint) {
        this.sender.sendError(source, Errors.edit(dimensions(source), status, dimension, list, waypoint));
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
                                    saveChanges(source, fileManager, "restore", "dimension", entry.dimensionName(), "list", list.name(), "waypoint", waypoint.name());
                                    Component actor = actorName(source);
                                    this.broadcast(source, new WaypointModificationMessage(
                                            entry.dimensionName(),
                                            list.name(),
                                            list.displayName(),
                                            waypoint.name(),
                                            waypoint,
                                            WaypointModificationType.ADD,
                                            result.syncNum()
                                    ), dims -> Broadcasts.restored(dims, actor, entry.dimensionName(), list, waypoint));
                                    this.sender.sendMessage(source, Results.restored(dimensions(source),
                                            entry.dimensionName(), list, waypoint));
                                }
                                case DIMENSION_NOT_FOUND, LIST_NOT_FOUND -> this.sender.sendError(source,
                                        Errors.of("wp.error.restore.list_missing"));
                                case IDENTIFIER_COLLISION -> this.sender.sendError(source,
                                        Errors.of("wp.error.restore.collision"));
                            }
                        }
                ),
                () -> this.sender.sendError(source, Errors.of("wp.error.restore.invalid"))
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
                        WaypointList created = result.waypointList();
                        Component actor = actorName(source);
                        this.broadcast(source, new WaypointModificationMessage(dimensionName, listName, listName, null, null, ADD_LIST, SERVER_N),
                                dims -> Broadcasts.createdList(dims, actor, dimensionName, created));
                        this.sender.sendMessage(source, Results.createdList(dimensions(source), dimensionName, created));
                        saveChanges(source, result.fileManager(), "list_add", "dimension", dimensionName, "list", listName);
                    }
                    case EXISTS -> this.sender.sendError(source,
                            Errors.listExists(dimensions(source), dimensionName, result.waypointList()));
                }
            });
        }
    }

    private void addWaypointDirectly(S source, String dimensionName, String listName, String name, String initials, WaypointPos waypointPos, int yaw, int rgb, boolean global, List<String> keywords, String description, @Nullable NamespacedId icon) {
        if (!validateTextInputs(source, listName, name, keywords, description)) {
            return;
        }
        SimpleWaypoint newWaypoint = new SimpleWaypoint(
                name,
                name,
                initials,
                waypointPos,
                rgb,
                yaw,
                global,
                keywords,
                description,
                icon
        );
        this.waypointServer.addWaypoint(dimensionName, listName, listName, newWaypoint, result -> {
            switch (result.status()) {
                case ADDED -> {
                    saveChanges(source, result.fileManager(), "add", "dimension", dimensionName, "list", listName, "waypoint", name);
                    WaypointList list = result.waypointList();
                    SimpleWaypoint added = result.waypointSnapshot();
                    Component actor = actorName(source);
                    this.broadcast(source, new WaypointModificationMessage(
                            dimensionName,
                            list.name(),
                            list.displayName(),
                            added.name(),
                            added,
                            WaypointModificationType.ADD,
                            result.syncNum()
                    ), dims -> Broadcasts.added(dims, actor, dimensionName, list, added));
                    this.sender.sendMessage(source, Results.added(dimensions(source), dimensionName, list, added));
                }
                case DUPLICATE -> this.sender.sendError(source, Errors.waypointExists(dimensions(source), dimensionName,
                        result.waypointList(), result.waypointSnapshot()));
            }
        });
    }

    private void executeAddWaypoint(S source, D dimensionArgument, String listName, String name, String initials, B blockPosArgument, int yaw, String hexCode, boolean global, List<String> keywords, String description, @Nullable I rawIcon) {
        NamespacedId icon = rawIcon == null ? null : validateIcon(source, rawIcon);
        if (rawIcon != null && icon == null) {
            return;
        }
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
            addWaypointDirectly(source, dimensionName, listName, name, initials, waypointPos, yaw, rgb, global, keywords, description, icon);
        } else {
            sendDimensionError(source, dimensionName);
        }
    }

    private void executeQuickAddWaypoint(S source, B blockPosArgument, String listName, String name) {
        executeQuickAddWaypoint(source, getSourceDimension(source), blockPosArgument, listName, name);
    }

    /** Adds with the defaults: initials from the name, a random colour and the source's facing. */
    private void executeQuickAddWaypoint(S source, D dimensionArgument, B blockPosArgument, String listName, String name) {
        String dimensionName = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            sendDimensionError(source, dimensionName);
            return;
        }
        WaypointPos position = toWaypointPos(source, blockPosArgument);
        if (position == null) {
            sendPosArgumentError(source);
            return;
        }
        addWaypointDirectly(source, dimensionName, listName, name, WaypointInitials.getDefaultInitials(plainText(name)),
                position, Math.round(getSourceYaw(source)), randomColor(), true, List.of(), "", null);
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
                    Component actor = actorName(source);
                    this.broadcast(source, new WaypointModificationMessage(dimensionName, listName, waypointList.displayName(), null, null, REMOVE_LIST, waypointList.getSyncNum() + 1),
                            dims -> Broadcasts.removedList(dims, actor, dimensionName, waypointList));
                    this.sender.sendMessage(source, Results.removedList(dimensions(source), dimensionName, waypointList));
                    saveChanges(source, fileManager, "list_remove", "dimension", dimensionName, "list", listName);
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
                case LIST_NOT_FOUND -> this.sender.sendError(source, Errors.noList(dimensions(source), dimensionName, listName));
                case NON_EMPTY -> this.sender.sendError(source, Errors.listNotEmpty(dimensions(source), dimensionName,
                        Objects.requireNonNull(result.waypointList())));
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
                    saveChanges(source, fileManager, "remove", "dimension", dimensionName, "list", listName, "waypoint", name);
                    WaypointModificationMessage buffer = new WaypointModificationMessage(
                            dimensionName,
                            listName,
                            Objects.requireNonNull(result.waypointList()).displayName(),
                            name,
                            waypoint,
                            WaypointModificationType.REMOVE,
                            result.syncNum()
                    );
                    WaypointList list = Objects.requireNonNull(result.waypointList());
                    Component actor = actorName(source);
                    this.broadcast(source, buffer, dims -> Broadcasts.removed(dims, actor, dimensionName, list, waypoint));
                    String token = this.restoreRegistry.register(
                            this.restoreOwner(source),
                            dimensionName,
                            listName,
                            waypoint
                    );
                    this.sender.sendMessage(source, Results.removed(dimensions(source), dimensionName, list, waypoint, token));
                }
                case DIMENSION_NOT_FOUND -> this.sender.sendError(source, Errors.noLists(dimensions(source), dimensionName));
                case LIST_NOT_FOUND -> this.sender.sendError(source, Errors.noList(dimensions(source), dimensionName, listName));
                case LIST_EMPTY, WAYPOINT_NOT_FOUND -> this.sender.sendError(source,
                        Errors.noWaypoint(dimensions(source), dimensionName, listName, name));
            }
        });
    }

    private void executeTp(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                runIfPlayerExists(source, player -> {
                    PlayerActionLog.Context actor = actor(source);
                    try {
                        teleportPlayer(source, player, dimensionArgument, waypoint.pos(), waypoint.yaw())
                                .whenComplete((success, error) -> PlayerActionLog.log(actor, "tp",
                                        error == null && Boolean.TRUE.equals(success) ? "success" : "failed",
                                        "dimension", fileManager.getDimensionName(), "list", listName, "waypoint", name));
                    } catch (RuntimeException exception) {
                        PlayerActionLog.log(actor, "tp", "failed", "dimension", fileManager.getDimensionName(),
                                "list", listName, "waypoint", name);
                        throw exception;
                    }
                    this.sender.sendPlayerMessage(player, Results.teleported(
                            DimensionStyle.local(recipientViewer(player), getDimensionTypes(source)),
                            playerName(player, getPlayerDisplayName(player)), fileManager.getDimensionName(), waypointList, waypoint));
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
        if (player == null) {
            return;
        }
        NavigationSession previous = this.navigationService.status(player).session();
        NavigationResult result = this.navigationService.disableAll(player);
        if (result.code() == NavigationResult.Code.NAVIGATION_DISABLED) {
            PlayerActionLog.log(actor(source), "navigation_stop", "success",
                    "dimension", previous == null ? null : previous.target().dimensionName(),
                    "list", previous == null ? null : previous.target().listName(),
                    "waypoint", previous == null ? null : previous.target().waypointName());
            this.sender.sendMessage(source, NavigationScreens.stopped(dimensions(source),
                    previous == null ? null : place(previous.target())));
        } else {
            sendNavigationResult(source, result);
        }
    }

    private void executeNavigateDisable(S source, NavigationMethod method) {
        P player = getNavigationPlayer(source);
        if (player != null) {
            sendNavigationResult(source, this.navigationService.disableMethod(player, method));
        }
    }

    /** /wp navigate: the panel while navigating, otherwise where to start. */
    private void executeNavigationPanel(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationResult status = this.navigationService.status(player);
        if (status.session() == null) {
            this.sender.sendMessage(source, NavigationScreens.idle(dimensions(source)));
        } else {
            sendNavigationResult(source, status);
        }
    }

    /** /wp navigate config text_display */
    private void executeTextDisplayPanel(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        NavigationSession session = this.navigationService.status(player).session();
        if (session == null) {
            this.sender.sendError(source, NavigationScreens.notNavigating(viewer(source)));
        } else {
            this.sender.sendMessage(source, NavigationScreens.textDisplay(viewer(source),
                    session.textDisplayTransformation(), null));
        }
    }

    private void executeTextDisplayTransformationReset(S source) {
        P player = getNavigationPlayer(source);
        if (player == null) {
            return;
        }
        sendTextDisplayTransformation(source, this.navigationService.resetTextDisplayTransformation(player),
                translatable("wp.text_display.reset"));
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
        sendTextDisplayTransformation(source, result, translatable("wp.text_display.updated"));
    }

    /** The text display panel with the change on top, or why the change failed. */
    private void sendTextDisplayTransformation(S source, NavigationResult result, Component updated) {
        if (result.code() != NavigationResult.Code.TEXT_DISPLAY_TRANSFORMATION_UPDATED || result.session() == null) {
            sendNavigationResult(source, result);
            return;
        }
        this.sender.sendMessage(source, NavigationScreens.textDisplay(viewer(source),
                result.session().textDisplayTransformation(), updated));
    }

    private @Nullable P getNavigationPlayer(S source) {
        P player = getPlayer(source);
        if (player == null) {
            this.sender.sendError(source, Errors.playerOnly());
        }
        return player;
    }

    private boolean isNavigationMethodSupported(NavigationMethod method) {
        return this.navigationService.supportedNavigationMethods().contains(method);
    }

    private Set<NavigationMethod> supportedNavigationMethods() {
        return this.navigationService.supportedNavigationMethods();
    }

    /** The navigation panel with what changed on top, or the error a failed result calls for (spec 10). */
    private void sendNavigationResult(S source, NavigationResult result) {
        NavigationSession session = result.session();
        switch (result.code()) {
            case NAVIGATION_STARTED, TARGET_CHANGED, SELECTION_REPLACED, METHOD_ENABLED, METHOD_DISABLED, NAVIGATION_DISABLED ->
                    PlayerActionLog.log(actor(source), "navigation", result.code().name().toLowerCase(Locale.ROOT),
                            "dimension", session == null ? null : session.target().dimensionName(),
                            "list", session == null ? null : session.target().listName(),
                            "waypoint", session == null ? null : session.target().waypointName(), "method", result.method());
            default -> { }
        }
        if (result.code() == NavigationResult.Code.NO_ACTIVE_SESSION) {
            this.sender.sendError(source, NavigationScreens.notNavigating(viewer(source)));
            return;
        }
        if (!result.successful()) {
            this.sender.sendError(source, NavigationScreens.failure(result));
            return;
        }
        if (session == null) {
            return;
        }
        Component updated = switch (result.code()) {
            case NAVIGATION_STARTED -> translatable("wp.navigation.started");
            case TARGET_CHANGED -> translatable("wp.navigation.target_changed");
            case SELECTION_REPLACED -> translatable("wp.navigation.selection_replaced");
            case METHOD_ENABLED, METHOD_ALREADY_ENABLED -> translatable("wp.navigation.turned_on",
                    NavigationScreens.methodName(Objects.requireNonNull(result.method())));
            case METHOD_DISABLED, METHOD_ALREADY_DISABLED -> translatable("wp.navigation.turned_off",
                    NavigationScreens.methodName(Objects.requireNonNull(result.method())));
            default -> null;
        };
        this.sender.sendMessage(source, NavigationScreens.panel(dimensions(source), place(session.target()),
                session.enabledMethods(), supportedNavigationMethods(), updated));
    }

    private void executeDownload(S source) {
        WaypointData waypointData = this.waypointServer.toWorldWaypointData();
        if (waypointData == null) {
            this.sender.sendError(source, Errors.of("wp.error.download.nothing"));
            return;
        }
        this.sendDownload(source, waypointData, waypointCount(waypointData));
    }

    private void executeDownload(S source, D dimensionArgument) {
        runWithSelectorTarget(source, dimensionArgument, (fileManager) -> {
            String dimensionName = fileManager.getDimensionName();
            if (fileManager.hasNoWaypoints()) {
                this.sender.sendError(source, Errors.of("wp.error.download.empty", dimensions(source).name(dimensionName)));
                return;
            }
            WaypointData waypointData = WaypointData.dimension(fileManager.toDimensionWaypointData());
            this.sendDownload(source, waypointData, waypointCount(waypointData));
        });
    }

    private void executeDownload(S source, D dimensionArgument, String listName) {
        runWithSelectorTarget(source, dimensionArgument, listName,
                (fileManager, waypointList) -> this.sendDownload(source,
                        WaypointData.waypointList(fileManager.getDimensionName(), waypointList), waypointList.size()),
                (fileManager, waypointList) -> this.sender.sendError(source, Errors.of("wp.error.download.empty",
                        WaypointRefs.label(waypointList.displayName(), waypointList.name()))));
    }

    private void executeDownload(S source, D dimensionArgument, String listName, String name) {
        runWithSelectorTarget(source, dimensionArgument, listName, name, (fileManager, waypointList, waypoint) ->
                this.sendDownload(source, new WaypointModificationMessage(
                        fileManager.getDimensionName(),
                        listName,
                        waypointList.displayName(),
                        name,
                        waypoint,
                        WaypointModificationType.ADD,
                        waypointList.getSyncNum()
                ), 1));
    }

    private static int waypointCount(WaypointData waypointData) {
        return waypointData.dimensions().stream()
                .flatMap(dimension -> dimension.waypointLists().stream())
                .mapToInt(WaypointList::size)
                .sum();
    }

    /** ✔ Sent 12 waypoints to your map mod, once the client has received every chunk. */
    private void sendDownload(S source, ChunkedMessage message, int waypoints) {
        PlayerActionLog.Context actor = actor(source);
        _959.server_waypoint.core.network.ChunkedMessageDelivery delivery =
                this.sender.sendChunkedMessage(source, message);
        if (!delivery.queued()) {
            PlayerActionLog.log(actor, "download", "rejected", "waypoints", waypoints);
            this.sender.sendError(source, Errors.of("wp.error.delivery"));
            return;
        }
        delivery.completion().whenComplete((result, exception) -> {
            if (exception == null && result != null && result.delivered()) {
                PlayerActionLog.log(actor, "download", "success", "waypoints", waypoints);
                this.sender.sendMessage(source, Results.sent(waypoints));
            } else {
                PlayerActionLog.log(actor, "download", "failed", "waypoints", waypoints);
                this.sender.sendError(source, Errors.of("wp.error.delivery"));
            }
        });
    }

    private ArgumentBuilder<S, ?> uploadSelectorArguments(UploadConflictPolicy conflictPolicy, boolean deleteMissing) {
        return dimensionNode()
                .executes(context -> executeUploadAndReturn(
                        context.getSource(), getString(context, UPLOAD_SOURCE_ARG), conflictPolicy, deleteMissing,
                        UploadScope.DIMENSION, getArgument(context, DIMENSION_ARG), null, null
                ))
                .then(listNameNode()
                        .executes(context -> executeUploadAndReturn(
                                context.getSource(), getString(context, UPLOAD_SOURCE_ARG), conflictPolicy, deleteMissing,
                                UploadScope.LIST, getArgument(context, DIMENSION_ARG),
                                getString(context, LIST_NAME_ARG), null
                        ))
                        .then(waypointNameNode()
                                .executes(context -> executeUploadAndReturn(
                                        context.getSource(), getString(context, UPLOAD_SOURCE_ARG), conflictPolicy, deleteMissing,
                                        UploadScope.WAYPOINT, getArgument(context, DIMENSION_ARG),
                                        getString(context, LIST_NAME_ARG), getString(context, WAYPOINT_NAME_ARG)
                                ))
                        )
                );
    }

    /** Whether this player can upload without negotiated network transport. */
    protected boolean usesLocalUpload(S source, P player) {
        return false;
    }

    /** Local implementations must invoke the receiver on the owning server thread. */
    protected CompletionStage<ChunkedMessageSendResult> dispatchUpload(
            S source, P player, UploadRequestBuffer request, Consumer<WaypointData> receiver
    ) {
        return this.sender.sendPlayerPacketTracked(player, request);
    }

    private void executeUpload(
            S source,
            String uploadSource,
            UploadConflictPolicy conflictPolicy,
            boolean deleteMissing,
            UploadScope scope,
            @Nullable D dimensionArgument,
            @Nullable String listName,
            @Nullable String waypointName
    ) {
        UploadTarget target;
        try {
            target = UploadTarget.valueOf(uploadSource.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            this.sender.sendError(source, Errors.of("wp.error.upload.source", text(uploadSource)));
            return;
        }
        P player = getPlayer(source);
        if (player == null) {
            this.sender.sendError(source, Errors.playerOnly());
            return;
        }
        if (!usesLocalUpload(source, player) && !this.sender.canSendChunkedMessage(player)) {
            this.sender.sendError(source, Errors.of("wp.error.upload.no_mod"));
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
            this.sender.sendError(source, Errors.of("wp.error.upload.request"));
            return;
        }

        UploadCoordinator.BeginResult beginResult = this.uploadCoordinator.begin(
                player, target, scope, conflictPolicy, deleteMissing, dimensions, listName, waypointName,
                this.sender.commandSenderActor(source)
        );
        if (beginResult.status() == UploadCoordinator.BeginStatus.BUSY) {
            this.sender.sendError(source, Errors.of("wp.error.upload.busy"));
            return;
        }
        if (beginResult.status() == UploadCoordinator.BeginStatus.COOLDOWN) {
            long remainingSeconds = Math.max(
                    1L,
                    (beginResult.cooldownRemaining().toMillis() + 999L) / 1_000L
            );
            this.sender.sendError(source, Errors.of("wp.error.upload.cooldown", text(remainingSeconds)));
            return;
        }
        UploadRequestBuffer request = Objects.requireNonNull(beginResult.request());
        dispatchUpload(source, player, request, data -> this.uploadCoordinator.onUpload(player, data))
                .whenComplete((result, exception) -> {
            if (exception == null && result != null && result.delivered()) {
                return;
            }
            if (this.uploadCoordinator.cancel(
                    player,
                    request.requestId(),
                    "upload request delivery failed"
            )) {
                this.sender.sendPlayerMessage(player, Errors.of("wp.error.upload.delivery"));
            }
        });
        this.sender.sendMessage(source, UploadScreens.requested(deleteMissing));
    }

    private LiteralArgumentBuilder<S> navigationCommandNode() {
        LiteralArgumentBuilder<S> navigateNode = literal(NAVIGATE_COMMAND);
        navigateNode.requires(this::hasNavigatePermission);
        navigateNode.executes(context -> {
            executeNavigationPanel(context.getSource());
            return Command.SINGLE_SUCCESS;
        });

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
        textDisplayNode.executes(context -> {
            executeTextDisplayPanel(context.getSource());
            return Command.SINGLE_SUCCESS;
        });
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

        LiteralArgumentBuilder<S> dimensionsNode = literal("dimensions");
        dimensionsNode.executes(context -> executeDimensionList(context.getSource(), 1));
        dimensionsNode.then(LiteralArgumentBuilder.<S>literal(PAGE_COMMAND)
                .then(RequiredArgumentBuilder.<S, Integer>argument(PAGE_NUMBER_ARG, integer(1))
                        .executes(context -> executeDimensionList(context.getSource(), getInteger(context, PAGE_NUMBER_ARG)))));
        listNode.then(dimensionsNode);

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
        new ListCommandOptions<S>((mode, reversed, view) -> listCommand(scope, mode, reversed, view),
                scope == ListScope.DIMENSION ? this::reservedListCommand : null).configure(targetNode);
    }

    private Command<S> listCommand(
            ListScope scope,
            WaypointSorting.SortMode sortMode,
            boolean reversed,
            ListView view
    ) {
        return context -> {
            executeList(context, scope, sortMode, reversed, view, null);
            return Command.SINGLE_SUCCESS;
        };
    }

    private Command<S> reservedListCommand(String listName) {
        return context -> {
            executeList(context, ListScope.WAYPOINT_LIST, WaypointSorting.SortMode.DEFAULT, false,
                    ListView.DEFAULT, listName);
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
            ListView view,
            @Nullable String fixedListName
    ) {
        S source = context.getSource();
        ListQuery query = new ListQuery(getOptionalString(context, SEARCH_QUERY_ARG, ""), sortMode, reversed, view,
                getOptionalInteger(context, PAGE_NUMBER_ARG, 1), optionalLimit(context));
        if (scope == ListScope.ALL_DIMENSIONS) {
            Viewer viewer = viewer(source);
            DimensionStyle dims = dimensions(source, viewer);
            int pageLimit = query.pageLimit(CONFIG.defaultPageLimit());
            this.sender.sendMessage(source, query.searching()
                    ? DimensionScreens.allSearch(dims, this.waypointQueryEngine.queryAll(engineQuery(source, query)),
                    query, pageLimit)
                    : DimensionScreens.all(dims, knownDimensions(source), query, pageLimit));
            return;
        }
        D dimensionArgument = scope == ListScope.CURRENT_DIMENSION
                ? getSourceDimension(source)
                : getArgument(context, DIMENSION_ARG);
        String listName = fixedListName != null
                ? fixedListName
                : scope == ListScope.WAYPOINT_LIST ? getString(context, LIST_NAME_ARG) : null;
        this.sender.sendMessage(source, listScreen(source, dimensionArgument, listName, query));
    }

    private int executeDimensionList(S source, int page) {
        Viewer viewer = viewer(source);
        this.sender.sendMessage(source, DimensionScreens.dimensionList(dimensions(source, viewer),
                knownDimensions(source), page, CONFIG.defaultPageLimit()));
        return Command.SINGLE_SUCCESS;
    }

    /** The loaded dimensions and those with waypoint files, each with its lists. */
    private List<DimensionScreens.DimensionLists> knownDimensions(S source) {
        Set<String> ids = new java.util.LinkedHashSet<>(getDimensionTypes(source).keySet());
        ids.addAll(this.waypointServer.getFileManagerMap().keySet());
        List<DimensionScreens.DimensionLists> dimensions = new java.util.ArrayList<>();
        for (String id : ids) {
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(id);
            dimensions.add(new DimensionScreens.DimensionLists(id,
                    fileManager == null ? List.of() : fileManager.getWaypointLists()));
        }
        return dimensions;
    }

    private @Nullable Integer optionalLimit(CommandContext<S> context) {
        try {
            return getInteger(context, PAGE_LIMIT_ARG);
        } catch (IllegalArgumentException missing) {
            return null;
        }
    }

    /** A dimension's or a list's screen, or the error that explains why there is none. */
    private Component listScreen(S source, D dimensionArgument, @Nullable String listName, ListQuery query) {
        Viewer viewer = viewer(source);
        DimensionStyle dims = dimensions(source, viewer);
        String dimension = toDimensionName(dimensionArgument);
        if (!isDimensionValid(source, dimensionArgument)) {
            return Errors.noDimension(dims, dimension);
        }
        int pageLimit = query.pageLimit(CONFIG.defaultPageLimit());
        WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimension);
        WaypointQueryEngine.Query engineQuery = engineQuery(source, query);
        if (listName == null) {
            List<WaypointList> lists = fileManager == null ? List.of() : fileManager.getWaypointLists();
            ListScreen.Totals totals = new ListScreen.Totals(lists.size(),
                    lists.stream().mapToInt(WaypointList::size).sum());
            return ListScreen.dimension(dims, dimension, totals,
                    this.waypointQueryEngine.queryDimension(dimension, engineQuery), query, pageLimit);
        }
        WaypointList list = fileManager == null ? null : fileManager.getWaypointListByName(listName);
        if (list == null) {
            return Errors.noList(dims, dimension, listName);
        }
        return ListScreen.list(dims, dimension, list,
                this.waypointQueryEngine.queryList(dimension, listName, engineQuery), query, pageLimit);
    }

    /** The search and sort of a list query, measured from the source's position. */
    private WaypointQueryEngine.Query engineQuery(S source, ListQuery query) {
        return new WaypointQueryEngine.Query(query.search(), query.sort(), getSourcePosition(source),
                toDimensionName(getSourceDimension(source)), query.descending());
    }

    /** /wp tp, remove, edit and details without a target: this dimension's list and what to click in it. */
    private int executeTargetHint(S source, String hintKey, HelpTopics.Topic plainTopic) {
        Viewer viewer = viewer(source);
        if (viewer.plainText()) {
            this.sender.sendMessage(source, HelpScreen.topic(viewer, plainTopic,
                    isNavigationMethodSupported(NavigationMethod.TEXT_DISPLAY)));
            return Command.SINGLE_SUCCESS;
        }
        this.sender.sendMessage(source, new ChatLines()
                .add(translatable(hintKey, NamedTextColor.GRAY))
                .add(listScreen(source, getSourceDimension(source), null, ListQuery.DEFAULT))
                .build());
        return Command.SINGLE_SUCCESS;
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

    private void executeReload(S source) {
        PlayerActionLog.Context actor = actor(source);
        executeByServer(source, () -> {
            this.waypointServer.reload();
            PlayerActionLog.log(actor, "reload", "completed");
            this.sender.sendMessage(source, Results.reloaded(getExternalLoadedLanguages()));
        });
    }

    private PlayerActionLog.Context actor(S source) {
        P player = getPlayer(source);
        PlayerActionLog.Actor executor = player == null
                ? new PlayerActionLog.Actor(null, this.sender.getSenderName(source) instanceof net.kyori.adventure.text.TextComponent text
                        ? text.content() : "server")
                : this.sender.playerActor(player);
        return new PlayerActionLog.Context(executor, this.sender.commandSenderActor(source));
    }

    private void saveChanges(S source, WaypointFileManager fileManager, String action, Object... fields) {
        PlayerActionLog.Context actor = actor(source);
        executeByServer(source, () -> {
            try {
                this.waypointServer.saveWaypointFile(fileManager);
                PlayerActionLog.log(actor, action, "success", fields);
            } catch (IOException e) {
                PlayerActionLog.log(actor, action, "save_failed", fields);
                this.sender.sendError(source, Errors.of("wp.error.save", text(fileManager.getDimensionFile().toString())));
                throw new RuntimeException(e);
            }
        });
    }

    public void register(@NotNull CommandDispatcher<S> dispatcher) {
        dispatcher.getRoot().addChild(build());
        dispatcher.getRoot().addChild(buildKeyGenerationCommand());
    }

    public LiteralCommandNode<S> buildKeyGenerationCommand() {
        return LiteralArgumentBuilder.<S>literal(GENERATE_KEY_COMMAND)
                .requires(this::isServerConsoleWithHighestPermission)
                .executes(context -> {
                    S source = context.getSource();
                    try {
                        var publicFile = StaticKeyGenerator.generate(waypointServer.configDirectory());
                        sender.sendMessage(source, Results.keyGenerated(publicFile.toString()));
                        return Command.SINGLE_SUCCESS;
                    } catch (IOException | IllegalArgumentException exception) {
                        sender.sendError(source, Errors.of("wp.error.key",
                                text(String.valueOf(exception.getMessage()))));
                        return 0;
                    }
                })
                .build();
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
