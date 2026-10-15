package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;

import java.util.Optional;

/** Configured stable-ID mapping to adapter-owned handles; no inferred hostname/name aliases. */
public interface ProxyServerDirectory<S> {
    Optional<S> findServer(RemoteServerId serverId);
}
