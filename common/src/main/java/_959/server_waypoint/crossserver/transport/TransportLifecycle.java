package _959.server_waypoint.crossserver.transport;

import java.util.concurrent.CompletionStage;

/**
 * Nonblocking transport lifecycle. Calls and completion callbacks must be serialized by the owner.
 * Start/stop are idempotent; stop completes only after sockets, tasks, and session keys are released.
 * Expected failures complete with a result value, not a platform-specific exception.
 * This contract does not start networking until a later implementation is explicitly enabled.
 */
public interface TransportLifecycle {
    CompletionStage<TransportResult> start();

    CompletionStage<TransportResult> stop();
}
