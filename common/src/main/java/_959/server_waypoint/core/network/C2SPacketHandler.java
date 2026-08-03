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
import _959.server_waypoint.core.network.upload.UploadCoordinator;
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
            this.sender.sendPlayerPacket(player, new ServerHandshakeBuffer(CONFIG.getServerId()));
        } else {
            this.sender.sendPlayerMessage(player, translatable("waypoint.incompatible.client",
                    text(ProtocolVersion.COMPATIBLE_VERSION).color(NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED).clickEvent(ClickEvent.openUrl(ModInfo.DOWNLOAD_URL))));
            this.sender.sendPlayerPacket(player, new ServerHandshakeBuffer(CONFIG.getServerId()));
            LOGGER.warn("client version mismatch: {}", clientVersion);
        }
    }

    public void onClientUpdateRequest(P player, ClientUpdateRequestBuffer buffer) {
        UpdatesBundleBuffer updatesBundle = new UpdatesBundleBuffer();
        Map<String, WaypointFileManager> serverManagers =
                new HashMap<>(this.waypointServer.getFileManagerMap());
        List<String> allDimensionsOnServer = new ArrayList<>(serverManagers.keySet());
        // iterating all dimensions from client and compare with server
        for (DimensionSyncIdentifier dimensionSyncId : buffer.dimensionSyncIds()) {
            String dimensionOnClient = dimensionSyncId.dimensionName();
            WaypointFileManager fileManager = serverManagers.get(dimensionOnClient);
            if (fileManager == null) {
                // tell client to remove
                updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, List.of()));
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
                    updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, listUpdates));
                }
                allDimensionsOnServer.remove(dimensionOnClient);
            }
        }
        // add the rest of dimensions on server that client does not have
        for (String dimensionName : allDimensionsOnServer) {
            WaypointFileManager waypointFileManager = serverManagers.get(dimensionName);
            if (!waypointFileManager.isEmpty()) {
                updatesBundle.add(waypointFileManager.toDimensionWaypoint());
            }
        }

        this.sender.sendPlayerPacket(player, updatesBundle);
        if (!updatesBundle.isEmpty()) {
            this.sender.sendPlayerMessage(player, translatable("waypoint.updates.sent"));
        }
    }

    public void onWaypointEditRequest(P player, WaypointEditRequestBuffer request) {
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
                            WaypointModificationBuffer modification = new WaypointModificationBuffer(
                                    request.dimensionName(),
                                    request.listIdentifier(),
                                    list.displayName(),
                                    request.waypointIdentifier(),
                                    result.afterSnapshot(),
                                    _959.server_waypoint.core.waypoint.WaypointModificationType.UPDATE,
                                    result.syncNum()
                            );
                            this.sender.broadcastPacketFromPlayer(player, modification);
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
        } catch (RuntimeException exception) {
            LOGGER.warn("Rejected malformed waypoint edit request", exception);
            this.sendEditResult(player, request, EditResultStatus.MALFORMED_REQUEST, null, 0);
        }
    }

    private void sendEditResult(
            P player,
            WaypointEditRequestBuffer request,
            EditResultStatus status,
            _959.server_waypoint.core.waypoint.SimpleWaypoint waypoint,
            int revision
    ) {
        this.sender.sendPlayerPacket(player, new WaypointEditResultBuffer(
                request.requestId(),
                status,
                request.dimensionName(),
                request.listIdentifier(),
                request.waypointIdentifier(),
                waypoint,
                revision
        ));
    }

    public void onUploadChunk(P player, UploadChunkBuffer buffer) {
        this.uploadCoordinator.onUploadChunk(player, buffer);
    }
}
