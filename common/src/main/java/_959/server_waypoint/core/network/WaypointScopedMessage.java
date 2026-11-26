package _959.server_waypoint.core.network;

import _959.server_waypoint.core.collection.WaypointScope;

/** A message concerning exactly one backend collection. Owner identity never crosses the wire. */
public interface WaypointScopedMessage extends ChunkedMessage {
    WaypointScope scope();
}
