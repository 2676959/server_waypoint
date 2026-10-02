package _959.server_waypoint.crossserver.protocol;

import java.util.Objects;
import java.util.UUID;

/** Session-local sender sequence plus request correlation. Enforcement of replay belongs to the session. */
public record ApplicationEnvelope(long sequence, UUID requestId, ApplicationMessage message) {
    public ApplicationEnvelope {
        if (sequence < 0) {
            throw new IllegalArgumentException("Sequence must be non-negative");
        }
        ApplicationMessage.requireId(requestId);
        Objects.requireNonNull(message, "message");
    }
}
