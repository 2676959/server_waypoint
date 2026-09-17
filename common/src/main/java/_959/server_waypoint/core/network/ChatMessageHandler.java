package _959.server_waypoint.core.network;

import _959.server_waypoint.command.permission.PermissionManager;
import _959.server_waypoint.config.Config;
import _959.server_waypoint.core.WaypointFileManager;
import _959.server_waypoint.core.WaypointServerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.util.Pair;
import net.kyori.adventure.text.Component;

import java.util.Iterator;
import java.util.List;

import static _959.server_waypoint.core.WaypointServerCore.CONFIG;
import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.text.TextButtonBuilder.addListButton;
import static _959.server_waypoint.text.TextButtonBuilder.addWaypointButton;
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
                if (waypointFileManager != null) {
                    List<WaypointList> waypointListsOnServer = waypointFileManager.getWaypointLists();
                    if (waypointListsOnServer.isEmpty()) {
                        promptNoWaypointList(player, dimensionName);
                    } else {
                        Component feedback = Component.translatable("waypoint.xaeros.sharing.found",
                                waypointTextNoTp(waypoint, dimensionName),
                                dimensionNameWithColor(dimensionName));
                        Component waypointLists = Component.text("");
                        for (Iterator<WaypointList> iterator = waypointListsOnServer.iterator(); iterator.hasNext();) {
                            WaypointList waypointList = iterator.next();
                            String listName = waypointList.name();
                            Component listItem = addWaypointButton(dimensionName, listName, waypoint)
                                    .append(Component.text(" ").style(DEFAULT_STYLE))
                                    .append(_959.server_waypoint.text.FormattedTextHelper.parse(waypointList.displayName()).style(DEFAULT_STYLE));
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
                    waypointServer.addWaypointFileManager(dimensionName);
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
