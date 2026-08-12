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
import _959.server_waypoint.core.network.message.*;
import _959.server_waypoint.core.network.upload.UploadCoordinator;
import _959.server_waypoint.core.network.data.DimensionWaypointData;
import _959.server_waypoint.core.network.data.WaypointData;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;
import java.io.IOException;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class C2SPacketHandler<S, K, P> {
    private final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;
    private final PermissionManager<S, K, P> permissionManager;
    private final NavigationService<P> navigationService;
    private final UploadCoordinator<P> uploadCoordinator;

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
        LOGGER.info("client join with protocol version: {}", clientVersion);

        if (clientVersion == ProtocolVersion.PROTOCOL_VERSION) {
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
        try {
            for (ChunkedMessage message : this.sender.receiveChunkedMessage(
                    player,
                    buffer,
                    () -> this.recoverFromOrderedMessageFailure(player)
            )) {
                if (message instanceof ClientUpdateRequestMessage updateRequest) {
                    this.onClientUpdateRequest(player, updateRequest);
                } else if (message instanceof WaypointEditRequestMessage editRequest) {
                    this.onWaypointEditRequest(player, editRequest);
                } else if (message instanceof WaypointData data) {
                    if (data.type() == WaypointData.Type.UPLOAD) {
                        this.uploadCoordinator.onUpload(player, data);
                    } else {
                        LOGGER.warn(
                                "Ignoring non-upload waypoint data received from client: {}",
                                data.type()
                        );
                    }
                } else {
                    LOGGER.warn(
                            "Ignoring serverbound chunked message type {}",
                            message.getClass().getSimpleName()
                    );
                }
            }
        } catch (IllegalArgumentException | IllegalStateException exception) {
            LOGGER.warn("Rejected malformed chunked-message transfer", exception);
        }
    }

    private void recoverFromOrderedMessageFailure(P player) {
        LOGGER.warn("Serverbound ordered message delivery failed; resynchronizing the client");
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
        this.uploadCoordinator.onDisconnect(player);
    }
}
