package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.RemoteClientCatalogs;
import _959.server_waypoint.crossserver.*;
import _959.server_waypoint.crossserver.catalog.CatalogReceiver;
import _959.server_waypoint.core.waypoint.WaypointQueryEngine;
import _959.server_waypoint.util.StringCommandBuilder;

import java.util.*;

/** GUI-only immutable identities and confirmation guard; never creates local waypoints. */
final class RemoteBrowserModel {
    record Path(RemoteServerId server, String dimension, String list, String waypoint) {
        RemoteWaypointKey key() {
            return waypoint == null ? null : new RemoteWaypointKey(server, dimension, list, waypoint);
        }
    }
    record Node(Path path, String label, RemoteCatalogState state, List<Node> children) {
        Node { children = List.copyOf(children); }
    }
    record Confirmation(long session, RemoteWaypointKey key, RemoteRevision revision,
                        RemoteWaypointSnapshot waypoint, String command) { }

    static List<Node> roots(Map<RemoteServerId, CatalogReceiver.View> servers, String filter, boolean reversed) {
        List<Node> roots = new ArrayList<>();
        for (var server : servers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(RemoteServerId::value))).toList()) {
            var id = server.getKey();
            var view = server.getValue();
            if (view.state() == RemoteCatalogState.UNAUTHORIZED) continue;
            List<Node> dimensions = new ArrayList<>();
            if (view.snapshot() != null && view.state() != RemoteCatalogState.UNAVAILABLE) {
                for (var dimension : new TreeMap<>(view.snapshot().dimensions()).entrySet()) {
                    List<Node> lists = new ArrayList<>();
                    for (var list : new TreeMap<>(dimension.getValue()).entrySet()) {
                        List<Node> waypoints = new ArrayList<>();
                        for (var waypoint : new TreeMap<>(list.getValue().waypoints()).entrySet()) {
                            var value = waypoint.getValue();
                            if (filter.isBlank() || WaypointQueryEngine.matchesFilter(waypoint.getKey(), filter)
                                    || WaypointQueryEngine.matchesFilter(list.getKey(), filter)
                                    || value.keywords().stream().anyMatch(word -> WaypointQueryEngine.matchesFilter(word, filter))) {
                                waypoints.add(new Node(new Path(id, dimension.getKey(), list.getKey(), waypoint.getKey()),
                                        label(value.displayName(), waypoint.getKey()), view.state(), List.of()));
                            }
                        }
                        if (reversed) Collections.reverse(waypoints);
                        if (!waypoints.isEmpty() || filter.isBlank()) {
                            lists.add(new Node(new Path(id, dimension.getKey(), list.getKey(), null),
                                    label(list.getValue().displayName(), list.getKey()), view.state(), waypoints));
                        }
                    }
                    if (reversed) Collections.reverse(lists);
                    if (!lists.isEmpty() || filter.isBlank()) {
                        dimensions.add(new Node(new Path(id, dimension.getKey(), null, null), dimension.getKey(), view.state(), lists));
                    }
                }
            }
            roots.add(new Node(new Path(id, null, null, null), label(view.displayName(), id.value()), view.state(), dimensions));
        }
        return List.copyOf(roots);
    }

    private static String label(String display, String identity) {
        return display.equals(identity) ? identity : display + " [" + identity + "]";
    }

    static Confirmation prepare(RemoteClientCatalogs catalogs, RemoteWaypointKey key) {
        if (key == null || catalogs.state() != RemoteCatalogState.AVAILABLE) return null;
        var view = catalogs.snapshot().get(key.serverId());
        if (view == null || view.state() != RemoteCatalogState.AVAILABLE || view.snapshot() == null) return null;
        var waypoint = view.snapshot().find(key).orElse(null);
        if (waypoint == null) return null;
        String command = "wp remote tp " + StringCommandBuilder.escapeArgument(key.serverId().value()) + " "
                + StringCommandBuilder.escapeArgument(key.dimensionName()) + " "
                + StringCommandBuilder.escapeArgument(key.listName()) + " "
                + StringCommandBuilder.escapeArgument(key.waypointName());
        // The oldest supported Minecraft command packet has a 256-character bound. Never truncate identities.
        if (command.length() > 256 || command.chars().anyMatch(c -> c < 32 || c == 127 || c == 167)) return null;
        return new Confirmation(catalogs.session(), key, view.snapshot().catalogRevision(), waypoint, command);
    }

    static boolean isCurrent(RemoteClientCatalogs catalogs, Confirmation confirmation) {
        return confirmation != null && confirmation.equals(prepare(catalogs, confirmation.key()));
    }
}
