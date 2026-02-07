package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.util.Pair;
import net.kyori.adventure.text.Component;

import java.util.Iterator;
import java.util.Set;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.text.TextButton.addListButton;
import static _959.server_waypoint.text.TextButton.addWaypointButton;
import static _959.server_waypoint.text.WaypointTextHelper.*;
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
        if (CONFIG.Features().addWaypointFromChatSharing() &&
                this.permissionManager.checkPlayerPermission(player, this.permissionManager.keys.add(), CONFIG.CommandPermission().add())) {
            String[] args = message.split(XAEROS_SEPARATOR);
            if (isValidXaerosSharingMessage(args)) {
                LOGGER.info("Found chat shared waypoint");
                Pair<SimpleWaypoint, String> waypointWithDim = toSimpleWaypoint(args);
                SimpleWaypoint waypoint = waypointWithDim.left();
                String dimensionName = waypointWithDim.right();
                WaypointFileManager waypointFileManager = WaypointServerCore.INSTANCE.getWaypointFileManager(dimensionName);
                if (waypointFileManager != null) {
                    Set<String> listNames = waypointFileManager.getWaypointListMap().keySet();
                    if (listNames.isEmpty()) {
                        promptNoWaypointList(player, dimensionName);
                    } else {
                        Component feedback = Component.translatable("waypoint.xaeros.sharing.found",
                                waypointTextNoTp(waypoint, dimensionName),
                                dimensionNameWithColor(dimensionName));
                        Component waypointLists = Component.text("");
                        for (Iterator<String> iterator = listNames.iterator(); iterator.hasNext();) {
                            String listName = iterator.next();
                            Component listItem = addWaypointButton(dimensionName, listName, waypoint)
                                    .append(Component.text(" ").style(DEFAULT_STYLE))
                                    .append(Component.text(listName).style(DEFAULT_STYLE));
                            waypointLists = waypointLists.append(listItem);
                            if (iterator.hasNext()) {
                                waypointLists = waypointLists.appendNewline();
                            }
                        }
                        Component listSelector = Component.translatable("waypoint.sharing.add.to.list", waypointLists);
                        feedback = feedback.appendNewline().append(listSelector);
                        this.sender.sendPlayerMessage(player, feedback);
                    }
                } else if (isDimensionValid(dimensionName)) {
                    LOGGER.info("dimension {} not found, add new dimension", dimensionName);
                    WaypointServerCore.INSTANCE.addWaypointListManager(dimensionName);
                    promptNoWaypointList(player, dimensionName);
                } else {
                    this.sender.sendPlayerMessage(player, Component.translatable("waypoint.xaeros.sharing.invalid.dimension",
                            waypointTextNoTp(waypoint, dimensionName),
                            dimensionNameWithColor(dimensionName)));
                }
            }
        }
    }

    private void promptNoWaypointList(P player, String dimString) {
        Component feedback = Component.translatable("waypoint.xaeros.sharing.no.list", addListButton(dimString,""));
        this.sender.sendPlayerMessage(player, feedback);
    }
}
