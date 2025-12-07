package _959.server_waypoint.core.network;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.Component;

import java.util.*;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;

public class ClientCommunicationHandler<S, P> {
    private final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;

    public ClientCommunicationHandler(PlatformMessageSender<S, P> messageSender, WaypointServerCore waypointServerCore) {
        this.sender = messageSender;
        this.waypointServer = waypointServerCore;
    }

    public void onClientHandshake(P player, ClientHandshakeBuffer buffer) {
        LOGGER.info("received handshake packet: {}", buffer);
        String clientVersion = buffer.version();

        if (clientVersion.equals(ModInfo.MOD_VERSION)) {
            this.sender.sendPlayerPacket(player, new ServerHandshakeBuffer(CONFIG.getServerId()));
        } else {
            LOGGER.warn("client version mismatch: {}", clientVersion);
        }
    }

    public void onClientUpdateRequest(P player, ClientUpdateRequestBuffer buffer) {
        LOGGER.info("received update request packet: {}", buffer.toString());
        UpdatesBundleBuffer updatesBundle = new UpdatesBundleBuffer(CONFIG.getServerId());
        boolean needUpdate = false;
        Set<String> allDimensionsOnServer = this.waypointServer.getFileManagerMap().keySet();
        // iterating all dimensions from client and compare with server
        for (DimensionSyncIdentifier dimensionSyncId : buffer.dimensionSyncIds()) {
            String dimensionOnClient = dimensionSyncId.dimensionName();
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionOnClient);
            if (fileManager == null) {
                // tell client to remove
                updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, new ArrayList<>()));
            } else {
                // prepare updates in that dimension for client
                Set<String> allListsOnServer = fileManager.getWaypointListMap().keySet();
                List<WaypointList> listUpdates = new ArrayList<>();
                // iterating all lists from client and compare
                for (WaypointListSyncIdentifier listSyncId : dimensionSyncId.listSyncIds()) {
                    String listOnClient = listSyncId.listName();
                    WaypointList waypointList = fileManager.getWaypointListByName(listOnClient);
                    if (waypointList == null) {
                        // tell client to remove
                        listUpdates.add(WaypointList.build(listOnClient, WaypointList.REMOVE_LIST));
                    } else {
                        // updates of list for client
                        int serverSyncNum = waypointList.getSyncNum();
                        if (serverSyncNum != listSyncId.syncNum()) {
                            listUpdates.add(waypointList);
                            needUpdate = true;
                        }
                        allListsOnServer.remove(listOnClient);
                    }
                }
                // add the rest of lists that client does not have
                for (String listName : allListsOnServer) {
                    listUpdates.add(fileManager.getWaypointListByName(listName));
                }
                updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, listUpdates));
                allDimensionsOnServer.remove(dimensionOnClient);
            }
        }
        // add the rest of dimensions
        for (String dimensionName : allDimensionsOnServer) {
            WaypointFileManager waypointFileManager = this.waypointServer.getWaypointFileManager(dimensionName);
            // should always be true, but check just in case
            if (waypointFileManager != null) {
                updatesBundle.add(waypointFileManager.toDimensionWaypoint());
                needUpdate = true;
            }
        }

        LOGGER.info("update: {}, updates bundle: {}", needUpdate, updatesBundle);
        if (needUpdate) {
            this.sender.sendPlayerMessage(player, Component.text("sent updates bundle"));
            this.sender.sendPlayerPacket(player, updatesBundle);
        } else {
            this.sender.sendPlayerMessage(player, Component.text("no updates needed"));
        }
    };
}
