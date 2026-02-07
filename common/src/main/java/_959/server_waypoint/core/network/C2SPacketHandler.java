package _959.server_waypoint.core.network;

import _959.server_waypoint.ModInfo;
import _959.server_waypoint.ProtocolVersion;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.network.buffer.*;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.*;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public class C2SPacketHandler<S, P> {
    private final PlatformMessageSender<S, P> sender;
    private final WaypointServerCore waypointServer;

    public C2SPacketHandler(PlatformMessageSender<S, P> messageSender, WaypointServerCore waypointServerCore) {
        this.sender = messageSender;
        this.waypointServer = waypointServerCore;
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
        List<String> allDimensionsOnServer = new ArrayList<>(this.waypointServer.getFileManagerMap().keySet());
        // iterating all dimensions from client and compare with server
        for (DimensionSyncIdentifier dimensionSyncId : buffer.dimensionSyncIds()) {
            String dimensionOnClient = dimensionSyncId.dimensionName();
            WaypointFileManager fileManager = this.waypointServer.getWaypointFileManager(dimensionOnClient);
            if (fileManager == null) {
                // tell client to remove
                updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, new ArrayList<>()));
            } else {
                // prepare updates in that dimension for client
                List<String> allListsOnServer = new ArrayList<>(fileManager.getWaypointListMap().keySet());
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
                        }
                        allListsOnServer.remove(listOnClient);
                    }
                }
                // add the rest of lists that client does not have
                for (String listName : allListsOnServer) {
                    listUpdates.add(fileManager.getWaypointListByName(listName));
                }
                if (!listUpdates.isEmpty()) {
                    updatesBundle.add(new DimensionWaypointBuffer(dimensionOnClient, listUpdates));
                }
                allDimensionsOnServer.remove(dimensionOnClient);
            }
        }
        // add the rest of dimensions on server that client does not have
        for (String dimensionName : allDimensionsOnServer) {
            WaypointFileManager waypointFileManager = this.waypointServer.getWaypointFileManager(dimensionName);
            // should always be nonnull, but check just in case; the empty ones on server should not be sent to client
            if (waypointFileManager != null && !waypointFileManager.isEmpty()) {
                updatesBundle.add(waypointFileManager.toDimensionWaypoint());
            }
        }

        this.sender.sendPlayerPacket(player, updatesBundle);
        if (!updatesBundle.isEmpty()) {
            this.sender.sendPlayerMessage(player, translatable("waypoint.updates.sent"));
        }
    }
}
