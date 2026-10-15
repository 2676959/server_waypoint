package _959.server_waypoint.crossserver.transport;

import _959.server_waypoint.crossserver.RemoteServerId;

import java.util.concurrent.CompletionStage;

/** Shared coordinator transport boundary. Registry policy remains proxy-owned. */
public interface CoordinatorTransport extends TransportLifecycle {
    /**
     * Closes sessions for the exact ID and releases their state. An absent session is success.
     * This is not revocation: registry policy must also prevent later admission of that identity.
     */
    CompletionStage<TransportResult> disconnect(RemoteServerId serverId);
}
