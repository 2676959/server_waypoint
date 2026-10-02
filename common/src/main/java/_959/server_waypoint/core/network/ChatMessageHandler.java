package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.util.Pair;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.text.feedback.SharingPrompt;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.util.XaerosMapHelper.*;

public abstract class ChatMessageHandler<S, K, P> {
    private final PlatformMessageSender<S, P> sender;
    private final PermissionManager<S, K, P> permissionManager;

    public ChatMessageHandler(PlatformMessageSender<S, P> sender, PermissionManager<S, K, P> permissionManager) {
        this.sender = sender;
        this.permissionManager = permissionManager;
    }

    protected abstract boolean isDimensionValid(String dimensionName);

    public void onChatMessage(P player, String message) {
        Config config = CONFIG;
        if (config.Features().addWaypointFromChatSharing() &&
                this.permissionManager.checkPlayerPermission(player, this.permissionManager.keys.add(), config.CommandPermission().add())) {
            String[] args = message.split(XAEROS_SEPARATOR);
            if (isValidXaerosSharingMessage(args)) {
                LOGGER.info("Found chat shared waypoint");
                Pair<SimpleWaypoint, String> waypointWithDim;
                try {
                    waypointWithDim = toSimpleWaypoint(args);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Malformed xaero waypoint sharing message, ignoring", e);
                    return;
                }
                SimpleWaypoint waypoint = waypointWithDim.left();
                String dimensionName = waypointWithDim.right();
                WaypointServerCore waypointServer = WaypointServerCore.INSTANCE;
                WaypointFileManager waypointFileManager = waypointServer.getWaypointFileManager(dimensionName);
                DimensionStyle dims = DimensionStyle.local(this.viewer(player), Map.of());
                if (waypointFileManager != null) {
                    this.sender.sendPlayerMessage(player, SharingPrompt.found(dims, dimensionName, waypoint,
                            waypointFileManager.getWaypointLists()));
                } else if (isDimensionValid(dimensionName)) {
                    LOGGER.info("dimension {} not found, add new dimension", dimensionName);
                    waypointServer.addWaypointFileManager(dimensionName);
                    this.sender.sendPlayerMessage(player, SharingPrompt.found(dims, dimensionName, waypoint, List.of()));
                } else {
                    this.sender.sendPlayerMessage(player, SharingPrompt.unknownDimension(dims, dimensionName, waypoint));
                }
            }
        }
    }

    /** The sharing player, who may add waypoints; whether they may teleport decides the initials' click. */
    private Viewer viewer(P player) {
        Set<Viewer.Permission> permissions = EnumSet.of(Viewer.Permission.ADD);
        if (this.permissionManager.checkPlayerPermission(player, this.permissionManager.keys.tp(),
                CONFIG.CommandPermission().tp())) {
            permissions.add(Viewer.Permission.TP);
        }
        return new Viewer(permissions, false, false, null, null, 0F);
    }
}
