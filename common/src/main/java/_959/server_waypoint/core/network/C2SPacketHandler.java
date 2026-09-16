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
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveException;
import _959.server_waypoint.core.network.codec.ChunkedMessageManager.ReceiveFailure;
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
    private final AtomicReference<UploadTransportSession<P>> activeUploadTransportSession =
            new AtomicReference<>();

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
        WaypointEditResultMessage message = new WaypointEditResultMessage(
                request.requestId(),
                status,
                request.dimensionName(),
                request.listIdentifier(),
                request.waypointIdentifier(),
                waypoint,
                revision
        );
        this.sender.sendPlayerChunkedMessageTracked(player, message)
                .completion()
                .whenComplete((result, exception) -> {
                    if (exception == null && result != null && result.delivered()) {
                        return;
                    }
                    LOGGER.warn(
                            "Failed to deliver waypoint edit result for request {} to {}: {}",
                            request.requestId(),
                            player,
                            exception == null ? result : exception.getClass().getSimpleName()
                    );
                });
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
            this.sender.receiveChunkedMessage(
                    player,
                    buffer,
                    limits,
                    message -> {
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
            );
        } catch (ReceiveException exception) {
            LOGGER.warn(
                    "Rejected serverbound chunked-message type {}: {}",
                    exception.messageTypeId(),
                    exception.reason(),
                    exception
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected decoded serverbound chunked message", exception);
        }
    }

    public void onUploadChunk(P player, UploadChunkBuffer buffer) {
        if (!this.sender.canSendChunkedMessage(player)
                || !this.uploadCoordinator.acceptsUploadChunk(player, buffer.requestId())) {
            return;
        }
        UploadTransportSession<P> session = this.bindUploadTransportSession(player, buffer);
        if (session == null) {
            LOGGER.warn(
                    "Ignoring upload chunk for a second or mismatched transfer {}",
                    buffer.messageChunk().transferId()
            );
            return;
        }
        if (buffer.messageChunk().messageTypeId() != ChunkedMessageRegistry.WAYPOINT_DATA.id()) {
            LOGGER.warn(
                    "Ignoring disallowed upload chunked-message type {}",
                    buffer.messageChunk().messageTypeId()
            );
            this.failUploadTransport(session, "disallowed message type");
            return;
        }
        try {
            boolean applied = this.uploadChunkedMessages.receiveAndApply(
                    player,
                    buffer.messageChunk(),
                    UPLOAD_REQUEST_LIMITS,
                    message -> {
                        if (!(message instanceof WaypointData data)
                                || data.type() != WaypointData.Type.UPLOAD
                                || !data.uploadData().requestId().equals(buffer.requestId())) {
                            throw new InvalidUploadMessageException(
                                    "Invalid decoded upload transport message"
                            );
                        }
                        this.uploadCoordinator.onUpload(player, data);
                    }
            );
            if (applied) {
                this.uploadChunkedMessages.clear(player);
                this.activeUploadTransportSession.compareAndSet(session, null);
            }
        } catch (ReceiveException exception) {
            LOGGER.warn(
                    "Rejected upload chunked-message type {}: {}",
                    exception.messageTypeId(),
                    exception.reason(),
                    exception
            );
            UUID failedTransferId = exception.transferId().orElse(buffer.messageChunk().transferId());
            if (session.transferId().equals(failedTransferId)) {
                this.failUploadTransport(session, exception.reason().name());
            }
        } catch (InvalidUploadMessageException exception) {
            LOGGER.warn("Ignoring invalid decoded upload transport message");
            this.failUploadTransport(session, "invalid decoded upload");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected decoded upload message", exception);
            this.failUploadTransport(session, "decoded upload handler failure");
        }
    }

    private UploadTransportSession<P> bindUploadTransportSession(P player, UploadChunkBuffer buffer) {
        UploadTransportSession<P> candidate = new UploadTransportSession<>(
                player,
                buffer.requestId(),
                buffer.messageChunk().transferId()
        );
        while (true) {
            UploadTransportSession<P> active = this.activeUploadTransportSession.get();
            if (active != null) {
                return active.equals(candidate) ? active : null;
            }
            if (this.activeUploadTransportSession.compareAndSet(null, candidate)) {
                return candidate;
            }
        }
    }

    private void failUploadTransport(UploadTransportSession<P> session, String reason) {
        if (!this.activeUploadTransportSession.compareAndSet(session, null)) {
            return;
        }
        this.uploadChunkedMessages.clear(session.player());
        this.uploadCoordinator.cancel(session.player(), session.requestId(), reason);
    }

    public void onDisconnect(P player) {
        this.sender.disconnectChunkedMessages(player);
        this.uploadChunkedMessages.clear(player);
        UploadTransportSession<P> session = this.activeUploadTransportSession.get();
        if (session != null && Objects.equals(session.player(), player)) {
            this.activeUploadTransportSession.compareAndSet(session, null);
        }
        this.uploadCoordinator.onDisconnect(player);
    }

    public void resetSession() {
        this.uploadChunkedMessages.clearAll();
        this.activeUploadTransportSession.set(null);
        this.uploadCoordinator.resetSession();
    }

    public void tickUploadTransport() {
        List<ReceiveFailure<P>> failures = this.uploadChunkedMessages.tick();
        for (ReceiveFailure<P> failure : failures) {
            LOGGER.warn(
                    "Discarded incomplete upload chunked-message type {} from peer {}: {} (transfer {})",
                    failure.messageTypeId(),
                    failure.peer(),
                    failure.reason(),
                    failure.transferId().map(Object::toString).orElse("unknown")
            );
            this.onUploadTransportFailure(failure);
        }
        this.uploadCoordinator.tick().ifPresent(this::clearExpiredUploadTransport);
    }

    private void clearExpiredUploadTransport(UUID requestId) {
        UploadTransportSession<P> session = this.activeUploadTransportSession.get();
        if (session != null
                && session.requestId().equals(requestId)
                && this.activeUploadTransportSession.compareAndSet(session, null)) {
            this.uploadChunkedMessages.clear(session.player());
        }
    }

    void onUploadTransportFailure(ReceiveFailure<P> failure) {
        if (failure.messageTypeId() != ChunkedMessageRegistry.WAYPOINT_DATA.id()) {
            return;
        }
        UploadTransportSession<P> session = this.activeUploadTransportSession.get();
        if (session != null
                && Objects.equals(session.player(), failure.peer())
                && failure.transferId().filter(session.transferId()::equals).isPresent()) {
            this.failUploadTransport(session, failure.reason().name());
        }
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

    private record UploadTransportSession<P>(P player, UUID requestId, UUID transferId) {
        private UploadTransportSession {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(transferId, "transferId");
        }
    }

    /** Marks a decoded upload payload that violates the upload transport contract. */
    private static final class InvalidUploadMessageException extends IllegalArgumentException {
        private InvalidUploadMessageException(String message) {
            super(message);
        }
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
