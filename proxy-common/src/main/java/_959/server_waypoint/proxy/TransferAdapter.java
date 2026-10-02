package _959.server_waypoint.proxy;

import _959.server_waypoint.crossserver.RemoteServerId;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** S is an adapter-owned opaque destination handle; no platform class belongs in shared policy. */
public interface TransferAdapter<S> {
    /**
     * Requests a switch after coordinator preparation/authorization (implemented in later steps).
     * Recheck the proxy-authenticated UUID and expected source immediately before requesting the
     * switch; never trust an earlier snapshot. Never block the calling thread. Expected failures
     * return a stable result. Successful completion does not authorize or perform teleportation.
     */
    CompletionStage<TransferResult> transfer(UUID playerId, RemoteServerId expectedSource, S destination);
}
