package _959.server_waypoint.crossserver.protocol;

import _959.server_waypoint.crossserver.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** V1 application values only; receipt does not authenticate, authorize, or execute an operation. */
public sealed interface ApplicationMessage {
    record RegisterServer(RemoteServerId serverId, int protocolVersion, Set<Integer> capabilities) implements ApplicationMessage {
        public RegisterServer {
            Objects.requireNonNull(serverId, "serverId");
            if (protocolVersion != CrossServerProtocol.PROTOCOL_VERSION) {
                throw new IllegalArgumentException("Unsupported application version");
            }
            capabilities = Set.copyOf(capabilities);
            if (capabilities.stream().anyMatch(id -> id <= 0)) {
                throw new IllegalArgumentException("Capability IDs must be positive");
            }
        }
    }

    record RegisterResult(RemoteServerId serverId, Result result) implements ApplicationMessage {
        public RegisterResult {
            Objects.requireNonNull(serverId, "serverId");
            Objects.requireNonNull(result, "result");
        }
    }

    record Heartbeat() implements ApplicationMessage { }

    record CatalogMetadata(RemoteServerId serverId, String displayName, RemoteRevision revision,
                           CatalogExportPolicy exportPolicy) implements ApplicationMessage {
        public CatalogMetadata {
            Objects.requireNonNull(serverId, "serverId");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(exportPolicy, "exportPolicy");
        }
    }

    /** Slice of canonical catalog bytes; step 6 must bound reassembly and validate the complete catalog. */
    record CatalogSnapshot(RemoteServerId serverId, RemoteRevision revision, UUID snapshotId,
                           int offset, int totalBytes, Bytes data) implements ApplicationMessage {
        public CatalogSnapshot {
            Objects.requireNonNull(serverId, "serverId");
            Objects.requireNonNull(revision, "revision");
            requireId(snapshotId);
            Objects.requireNonNull(data, "data");
            if (offset < 0 || totalBytes <= 0 || data.size() == 0 || offset > totalBytes - data.size()) {
                throw new IllegalArgumentException("Invalid catalog chunk range");
            }
        }
    }

    /** Complete list replacements, exact list removals and dimension removals; no mutation is performed here. */
    record CatalogDelta(RemoteServerId serverId, RemoteRevision baseRevision, RemoteRevision revision,
                        Map<String, Map<String, RemoteListSnapshot>> replacements,
                        Map<String, Set<String>> removedLists, Set<String> removedDimensions) implements ApplicationMessage {
        public CatalogDelta {
            Objects.requireNonNull(serverId, "serverId");
            Objects.requireNonNull(baseRevision, "baseRevision");
            Objects.requireNonNull(revision, "revision");
            if (revision.compareTo(baseRevision) <= 0) {
                throw new IllegalArgumentException("Delta must advance the catalog revision");
            }
            Map<String, Map<String, RemoteListSnapshot>> copied = new HashMap<>();
            replacements.forEach((dimension, lists) -> copied.put(dimension, Map.copyOf(lists)));
            replacements = Map.copyOf(copied);
            Map<String, Set<String>> removals = new HashMap<>();
            removedLists.forEach((dimension, lists) -> removals.put(dimension, Set.copyOf(lists)));
            removedLists = Map.copyOf(removals);
            removedDimensions = Set.copyOf(removedDimensions);
            for (String dimension : removedDimensions) {
                if (replacements.containsKey(dimension) || removedLists.containsKey(dimension)) {
                    throw new IllegalArgumentException("Conflicting dimension operations");
                }
            }
            for (var entry : removedLists.entrySet()) {
                Map<String, RemoteListSnapshot> changed = replacements.get(entry.getKey());
                if (changed != null && entry.getValue().stream().anyMatch(changed::containsKey)) {
                    throw new IllegalArgumentException("Conflicting list operations");
                }
            }
        }
    }

    record CatalogInvalidate(RemoteServerId serverId, RemoteRevision revision, RemoteCatalogState state) implements ApplicationMessage {
        public CatalogInvalidate {
            Objects.requireNonNull(serverId, "serverId");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(state, "state");
            if (state == RemoteCatalogState.AVAILABLE) {
                throw new IllegalArgumentException("Invalidation cannot publish an available catalog");
            }
        }
    }

    record PrepareHandoff(UUID playerId, RemoteServerId source, RemoteWaypointKey target,
                          Action action, RemoteRevision observedCatalogRevision,
                          RemoteRevision observedListRevision) implements ApplicationMessage {
        public PrepareHandoff {
            requireRoute(playerId, source, target);
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(observedCatalogRevision, "observedCatalogRevision");
            Objects.requireNonNull(observedListRevision, "observedListRevision");
        }
    }

    /** The request UUID in the envelope remains the original prepare request throughout a handoff. */
    record HandoffBinding(UUID handoffId, UUID playerId, RemoteServerId source, RemoteWaypointKey target,
                          Action action, long expiresAtEpochMillis) {
        public HandoffBinding {
            requireId(handoffId);
            requireRoute(playerId, source, target);
            Objects.requireNonNull(action, "action");
            if (expiresAtEpochMillis <= 0) {
                throw new IllegalArgumentException("Expiry must be positive");
            }
        }
    }

    record HandoffPrepared(HandoffBinding binding) implements ApplicationMessage {
        public HandoffPrepared { Objects.requireNonNull(binding, "binding"); }
    }

    record HandoffRejected(Result reason) implements ApplicationMessage {
        public HandoffRejected { requireFailure(reason); }
    }

    record ClaimHandoff(UUID handoffId, UUID playerId, RemoteServerId destination) implements ApplicationMessage {
        public ClaimHandoff {
            requireId(handoffId);
            requireId(playerId);
            Objects.requireNonNull(destination, "destination");
        }
    }

    record HandoffClaimed(HandoffBinding binding) implements ApplicationMessage {
        public HandoffClaimed { Objects.requireNonNull(binding, "binding"); }
    }

    record CompleteHandoff(UUID handoffId, UUID playerId, RemoteServerId destination, Result result) implements ApplicationMessage {
        public CompleteHandoff {
            requireId(handoffId);
            requireId(playerId);
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(result, "result");
        }
    }

    record CancelHandoff(UUID handoffId, Result reason) implements ApplicationMessage {
        public CancelHandoff { requireId(handoffId); requireFailure(reason); }
    }

    record Error(Result reason) implements ApplicationMessage {
        public Error { requireFailure(reason); }
    }

    enum Action { TELEPORT }

    /** Stable codes are assigned explicitly by the codec; no exception text or secrets cross the wire. */
    enum Result {
        SUCCESS, UNAVAILABLE, UNAUTHORIZED, NOT_FOUND, STALE_CATALOG, BUSY, EXPIRED, REPLAY,
        WRONG_SOURCE, WRONG_DESTINATION, TRANSFER_FAILED, CANCELLED, UNSUPPORTED, INVALID_REQUEST, INTERNAL_ERROR
    }

    /** Defensive binary value with content equality and a redacted diagnostic. */
    final class Bytes {
        private final byte[] value;

        public Bytes(byte[] value) {
            this.value = Objects.requireNonNull(value, "value").clone();
        }

        public byte[] copy() { return value.clone(); }
        public int size() { return value.length; }
        @Override public boolean equals(Object other) { return other instanceof Bytes bytes && Arrays.equals(value, bytes.value); }
        @Override public int hashCode() { return Arrays.hashCode(value); }
        @Override public String toString() { return "Bytes[length=" + value.length + "]"; }
    }

    private static void requireRoute(UUID playerId, RemoteServerId source, RemoteWaypointKey target) {
        requireId(playerId);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source.equals(target.serverId())) {
            throw new IllegalArgumentException("Handoff requires a different destination");
        }
    }

    static void requireId(UUID id) {
        Objects.requireNonNull(id, "id");
        if (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0) {
            throw new IllegalArgumentException("UUID must not be nil");
        }
    }

    private static void requireFailure(Result reason) {
        Objects.requireNonNull(reason, "reason");
        if (reason == Result.SUCCESS) {
            throw new IllegalArgumentException("Failure reason cannot be success");
        }
    }
}
