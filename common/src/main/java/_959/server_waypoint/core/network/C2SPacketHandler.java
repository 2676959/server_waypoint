package _959.server_waypoint.core.network;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.edit.EditTarget;
import _959.server_waypoint.navigation.NavigationService;
import _959.server_waypoint.navigation.NavigationTarget;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveLimits;
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class C2SPacketHandler<S, K, P> {
    private static final int MAX_SERVERBOUND_IDENTIFIER_BYTES = 1_024;
    private static final int MAX_UPDATE_REQUEST_DIMENSIONS = 1_024;
    private static final int MAX_UPDATE_REQUEST_LISTS = 16_384;
    private static final ReceiveLimits UPDATE_REQUEST_LIMITS = new ReceiveLimits(
            1 * 1_024 * 1_024,
            20_000
    );
    private static final ReceiveLimits EDIT_REQUEST_LIMITS = new ReceiveLimits(
            64 * 1_024,
            64
    );
    private static final ReceiveLimits UPLOAD_REQUEST_LIMITS = new ReceiveLimits(
            16 * 1_024 * 1_024,
            8_192
    );

    private final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;
    private final PermissionManager<S, K, P> permissionManager;
    private final NavigationService<P> navigationService;
    private final UploadCoordinator<P> uploadCoordinator;
    private final ChunkedMessageManager<P> uploadChunkedMessages = new ChunkedMessageManager<>();
    private final AtomicReference<UUID> activeUploadTransportRequest = new AtomicReference<>();

    public C2SPacketHandler(
            PlatformMessageSender<S, P> messageSender,
            WaypointServerCore waypointServerCore,
            PermissionManager<S, K, P> permissionManager,
            NavigationService<P> navigationService,
            UploadCoordinator<P> uploadCoordinator
    ) {
        this.sender = messageSender;
        this.waypointServer = waypointServerCore;
        this.permissionManager = permissionManager;
        this.navigationService = navigationService;
        this.uploadCoordinator = uploadCoordinator;
    }

    public void onClientHandshake(P player, ClientHandshakeBuffer buffer) {
        int clientVersion = buffer.version();
        boolean compatible = clientVersion == ProtocolVersion.PROTOCOL_VERSION;
        this.sender.setChunkedMessageCapable(player, compatible);
        LOGGER.info("client join with protocol version: {}", clientVersion);

        if (compatible) {
            this.sender.sendPlayerPacket(player, new ServerHandshakeBuffer(
                    CONFIG.getServerId(),
                    CONFIG.Features().compressChunkedMessages()
            ));
        } else {
            this.sender.sendPlayerMessage(player, translatable("waypoint.incompatible.client",
                    text(ProtocolVersion.COMPATIBLE_VERSION).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(ModInfo.DOWNLOAD_URL))));
            this.sender.sendPlayerPacket(player, new ServerHandshakeBuffer(
                    CONFIG.getServerId(),
                    CONFIG.Features().compressChunkedMessages()
            ));
            LOGGER.warn("client version mismatch: {}", clientVersion);
        }
    }

    public void onClientUpdateRequest(P player, ClientUpdateRequestMessage buffer) {
        List<DimensionWaypointData> updates = new ArrayList<>();
        Map<String, WaypointFileManager> serverManagers =
                new HashMap<>(this.waypointServer.getFileManagerMap());
        List<String> allDimensionsOnServer = new ArrayList<>(serverManagers.keySet());
        // iterating all dimensions from client and compare with server
        for (DimensionSyncIdentifier dimensionSyncId : buffer.dimensionSyncIds()) {
            String dimensionOnClient = dimensionSyncId.dimensionName();
            WaypointFileManager fileManager = serverManagers.get(dimensionOnClient);
            if (fileManager == null) {
                // tell client to remove
                updates.add(new DimensionWaypointData(dimensionOnClient, List.of()));
            } else {
                // prepare updates in that dimension for client
                Map<String, WaypointList> serverLists = fileManager.getWaypointListMap();
                List<String> allListsOnServer = new ArrayList<>(serverLists.keySet());
                List<WaypointList> listUpdates = new ArrayList<>(dimensionSyncId.listSyncIds().size() + allListsOnServer.size());
                // iterating all lists from client and compare
                for (WaypointListSyncIdentifier listSyncId : dimensionSyncId.listSyncIds()) {
                    String listOnClient = listSyncId.listName();
                    WaypointList waypointList = serverLists.get(listOnClient);
                    if (waypointList == null) {
                        // tell client to remove
                        listUpdates.add(WaypointList.build(listOnClient, WaypointList.REMOVE_LIST));
                    } else {
                        // updates of list for client
                        int serverSyncNum = waypointList.getSyncNum();
                        if (serverSyncNum != listSyncId.syncNum()) {
                            listUpdates.add(waypointList);
                        }
                        allListsOnServer.remove(listOnClient);
                    }
                }
                // add the rest of lists that client does not have
                for (String listName : allListsOnServer) {
                    listUpdates.add(serverLists.get(listName));
                }
                if (!listUpdates.isEmpty()) {
                    updates.add(new DimensionWaypointData(dimensionOnClient, listUpdates));
                }
                allDimensionsOnServer.remove(dimensionOnClient);
            }
        }
        // add the rest of dimensions on server that client does not have
        for (String dimensionName : allDimensionsOnServer) {
            WaypointFileManager waypointFileManager = serverManagers.get(dimensionName);
            if (!waypointFileManager.isEmpty()) {
                updates.add(waypointFileManager.toDimensionWaypointData());
            }
        }

        this.sender.sendPlayerChunkedMessage(player, WaypointData.updates(updates));
        if (!updates.isEmpty()) {
            this.sender.sendPlayerMessage(player, translatable("waypoint.updates.sent"));
        }
    }

    public void onWaypointEditRequest(P player, WaypointEditRequestMessage request) {
        if (!this.uploadCoordinator.tryBeginEditRequest()) {
            this.sendEditResult(player, request, EditResultStatus.UPLOAD_BUSY, null, 0);
            return;
        }
        try {
            this.onAdmittedWaypointEditRequest(player, request);
        } finally {
            this.uploadCoordinator.finishEditRequest();
        }
    }

    private void onAdmittedWaypointEditRequest(P player, WaypointEditRequestMessage request) {
        if (!this.permissionManager.checkPlayerPermission(
                player,
                this.permissionManager.keys.edit(),
                CONFIG.CommandPermission().edit()
        )) {
            this.sendEditResult(player, request, EditResultStatus.PERMISSION_DENIED, null, 0);
            return;
        }
        try {
            this.waypointServer.updateWaypoint(
                    EditTarget.waypoint(
                            request.dimensionName(),
                            request.listIdentifier(),
                            request.waypointIdentifier()
                    ),
                    request.expectedListRevision(),
                    request.patch(),
                    result -> {
                        if (result.status() != EditResultStatus.SUCCESS) {
                            return;
                        }
                        ChunkedMessageManager.validateEncodable(
                                this.toModificationMessage(request, result)
                        );
                        ChunkedMessageManager.validateEncodable(
                                this.toEditResultMessage(request, result)
                        );
                    },
                    result -> {
                        if (result.status() == EditResultStatus.SUCCESS) {
                            WaypointFileManager fileManager = Objects.requireNonNull(result.fileManager());
                            try {
                                this.waypointServer.saveWaypointFile(fileManager);
                            } catch (IOException exception) {
                                LOGGER.error("Failed to persist waypoint edit", exception);
                            }
                            WaypointList list = Objects.requireNonNull(result.listSnapshot());
                            this.navigationService.refreshTarget(
                                    new NavigationTarget(
                                            request.dimensionName(),
                                            list,
                                            Objects.requireNonNull(result.beforeSnapshot())
                                    ),
                                    new NavigationTarget(
                                            request.dimensionName(),
                                            list,
                                            Objects.requireNonNull(result.afterSnapshot())
                                    )
                            );
                            WaypointModificationMessage modification =
                                    this.toModificationMessage(request, result);
                            this.sender.broadcastChunkedMessageFromPlayer(player, modification);
                        }
                        this.sendEditResult(
                                player,
                                request,
                                result.status(),
                                result.afterSnapshot(),
                                result.syncNum()
                        );
                    }
            );
        } catch (MessageEncodingException exception) {
            LOGGER.warn(
                    "Rejected waypoint edit because its result could not be encoded within the {}-byte logical-message budget",
                    ChunkedMessageManager.MAX_MESSAGE_BYTES,
                    exception
            );
            this.sender.sendPlayerMessage(
                    player,
                    translatable("waypoint.network.encoding_failed")
            );
            this.sendEditResult(player, request, EditResultStatus.ENCODING_FAILED, null, 0);
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected malformed waypoint edit request", exception);
            this.sendEditResult(player, request, EditResultStatus.MALFORMED_REQUEST, null, 0);
        }
    }

    private WaypointModificationMessage toModificationMessage(
            WaypointEditRequestMessage request,
            _959.server_waypoint.core.edit.WaypointEditResult result
    ) {
        WaypointList list = Objects.requireNonNull(result.listSnapshot());
        return new WaypointModificationMessage(
                request.dimensionName(),
                list.name(),
                list.displayName(),
                request.waypointIdentifier(),
                Objects.requireNonNull(result.afterSnapshot()),
                _959.server_waypoint.core.waypoint.WaypointModificationType.UPDATE,
                result.syncNum()
        );
    }

    private WaypointEditResultMessage toEditResultMessage(
            WaypointEditRequestMessage request,
            _959.server_waypoint.core.edit.WaypointEditResult result
    ) {
        return new WaypointEditResultMessage(
                request.requestId(),
                result.status(),
                request.dimensionName(),
                request.listIdentifier(),
                request.waypointIdentifier(),
                result.afterSnapshot(),
                result.syncNum()
        );
    }

    private void sendEditResult(
            P player,
            WaypointEditRequestMessage request,
            EditResultStatus status,
            _959.server_waypoint.core.waypoint.SimpleWaypoint waypoint,
            int revision
    ) {
        this.sender.sendPlayerChunkedMessage(player, new WaypointEditResultMessage(
                request.requestId(),
                status,
                request.dimensionName(),
                request.listIdentifier(),
                request.waypointIdentifier(),
                waypoint,
                revision
        ));
    }

    public void onMessageChunk(P player, MessageChunkBuffer buffer) {
        if (!this.sender.canSendChunkedMessage(player)) {
            return;
        }
        ReceiveLimits limits = generalServerboundLimits(buffer.messageTypeId());
        if (limits == null) {
            LOGGER.warn(
                    "Ignoring disallowed serverbound chunked-message type {}",
                    buffer.messageTypeId()
            );
            return;
        }
        try {
            for (ChunkedMessage message : this.sender.receiveChunkedMessage(
                    player,
                    buffer,
                    () -> this.recoverFromChunkedMessageFailure(player),
                    limits
            )) {
                if (message instanceof ClientUpdateRequestMessage updateRequest) {
                    validateClientUpdateRequest(updateRequest);
                    this.onClientUpdateRequest(player, updateRequest);
                } else if (message instanceof WaypointEditRequestMessage editRequest) {
                    validateWaypointEditRequest(editRequest);
                    this.onWaypointEditRequest(player, editRequest);
                } else {
                    throw new IllegalArgumentException(
                            "Decoded a disallowed serverbound chunked-message type"
                    );
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected malformed chunked-message transfer", exception);
        }
    }

    public void onUploadChunk(P player, UploadChunkBuffer buffer) {
        if (!this.sender.canSendChunkedMessage(player)
                || !this.uploadCoordinator.acceptsUploadChunk(player, buffer.requestId())) {
            return;
        }
        if (buffer.messageChunk().messageTypeId() != ChunkedMessageRegistry.WAYPOINT_DATA.id()) {
            this.failUploadTransport(player);
            return;
        }
        UUID activeRequest = this.activeUploadTransportRequest.get();
        if (!buffer.requestId().equals(activeRequest)
                && this.activeUploadTransportRequest.compareAndSet(activeRequest, buffer.requestId())) {
            this.uploadChunkedMessages.clearAll();
        }
        try {
            for (ChunkedMessage message : this.uploadChunkedMessages.receive(
                    player,
                    buffer.messageChunk(),
                    () -> this.failUploadTransport(player),
                    UPLOAD_REQUEST_LIMITS
            )) {
                if (!(message instanceof WaypointData data)
                        || data.type() != WaypointData.Type.UPLOAD
                        || !data.uploadData().requestId().equals(buffer.requestId())) {
                    this.failUploadTransport(player);
                    return;
                }
                try {
                    this.uploadCoordinator.onUpload(player, data);
                } finally {
                    this.uploadChunkedMessages.clear(player);
                    this.activeUploadTransportRequest.compareAndSet(buffer.requestId(), null);
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected malformed upload transfer", exception);
            this.failUploadTransport(player);
        }
    }

    private void failUploadTransport(P player) {
        this.uploadChunkedMessages.clear(player);
        this.activeUploadTransportRequest.set(null);
        this.uploadCoordinator.onDisconnect(player);
        this.sender.sendPlayerMessage(player, translatable("waypoint.upload.request.invalid"));
    }

    private void recoverFromChunkedMessageFailure(P player) {
        LOGGER.warn("Serverbound chunked message delivery failed; resynchronizing the client");
        this.sender.sendPlayerMessage(
                player,
                translatable("waypoint.network.resynchronizing")
        );
        WaypointData waypointData = this.waypointServer.toWorldWaypointData();
        this.sender.sendPlayerChunkedMessage(
                player,
                waypointData == null ? WaypointData.world(List.of()) : waypointData
        );
    }

    public void onDisconnect(P player) {
        this.sender.disconnectChunkedMessages(player);
        this.uploadChunkedMessages.clear(player);
        this.uploadCoordinator.onDisconnect(player);
    }

    public void tickUploadTransport() {
        this.uploadChunkedMessages.tick();
    }

    private static ReceiveLimits generalServerboundLimits(int messageTypeId) {
        if (messageTypeId == ChunkedMessageRegistry.CLIENT_UPDATE_REQUEST.id()) {
            return UPDATE_REQUEST_LIMITS;
        }
        if (messageTypeId == ChunkedMessageRegistry.WAYPOINT_EDIT_REQUEST.id()) {
            return EDIT_REQUEST_LIMITS;
        }
        return null;
    }

    private static void validateClientUpdateRequest(ClientUpdateRequestMessage request) {
        if (request.dimensionSyncIds().size() > MAX_UPDATE_REQUEST_DIMENSIONS) {
            throw new IllegalArgumentException("Client update request exceeds dimension limit");
        }
        Set<String> dimensions = new HashSet<>();
        int listCount = 0;
        for (DimensionSyncIdentifier dimension : request.dimensionSyncIds()) {
            validateServerboundIdentifier(dimension.dimensionName(), false);
            if (!dimensions.add(dimension.dimensionName())) {
                throw new IllegalArgumentException("Client update request contains a duplicate dimension");
            }
            listCount = Math.addExact(listCount, dimension.listSyncIds().size());
            if (listCount > MAX_UPDATE_REQUEST_LISTS) {
                throw new IllegalArgumentException("Client update request exceeds waypoint-list limit");
            }
            Set<String> lists = new HashSet<>();
            for (WaypointListSyncIdentifier list : dimension.listSyncIds()) {
                validateServerboundIdentifier(list.listName(), true);
                if (!lists.add(list.listName())) {
                    throw new IllegalArgumentException(
                            "Client update request contains a duplicate waypoint list"
                    );
                }
            }
        }
    }

    private static void validateWaypointEditRequest(WaypointEditRequestMessage request) {
        validateServerboundIdentifier(request.dimensionName(), false);
        validateServerboundIdentifier(request.listIdentifier(), true);
        validateServerboundIdentifier(request.waypointIdentifier(), true);
    }

    private static void validateServerboundIdentifier(String value, boolean allowEmpty) {
        if ((!allowEmpty && value.isEmpty())
                || value.getBytes(StandardCharsets.UTF_8).length > MAX_SERVERBOUND_IDENTIFIER_BYTES
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid serverbound identifier");
        }
    }
}
