package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;

/** Backend-owned outbound connection lifecycle; identity is configured before starting. */
public interface BackendTransport extends TransportLifecycle {
    RemoteServerId serverId();
}
