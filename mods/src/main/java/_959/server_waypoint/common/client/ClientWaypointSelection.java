package _959.server_waypoint.common.client;

import org.jetbrains.annotations.Nullable;
import java.util.Objects;
import java.util.UUID;

/** A captured destination; view changes cannot retarget a delayed operation. */
public record ClientWaypointSelection(ClientWaypointSource source, long generation, @Nullable UUID profileId,
                                      String dimensionName, String listIdentifier, @Nullable String waypointIdentifier) {
    public ClientWaypointSelection at(String dimension, String list, @Nullable String waypoint) {
        return new ClientWaypointSelection(source, generation, profileId, dimension, list, waypoint);
    }

    public ClientWaypointSelection {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(dimensionName, "dimensionName");
        Objects.requireNonNull(listIdentifier, "listIdentifier");
        if ((source == ClientWaypointSource.LOCAL) != (profileId != null)) {
            throw new IllegalArgumentException("Only Local selections require a profile UUID");
        }
    }
}
