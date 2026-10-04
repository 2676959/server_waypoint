package _959.server_waypoint.crossserver.transport;

import java.util.concurrent.CompletionStage;

/**
 * Nonblocking transport lifecycle. Calls and completion callbacks must be serialized by the owner.
 * Start/stop are idempotent; stop completes only after sockets, tasks, and session keys are released.
 * Expected failures complete with a result value, not a platform-specific exception.
 * BackendAgent and the proxy CoordinatorAgent start networking only when explicitly enabled.
 */
public interface TransportLifecycle {
    CompletionStage<TransportResult> start();

    CompletionStage<TransportResult> stop();
}
