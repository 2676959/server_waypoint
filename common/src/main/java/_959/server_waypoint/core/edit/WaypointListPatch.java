package _959.server_waypoint.core.edit;

import java.util.Objects;

public record WaypointListPatch(
        PatchField<String> identifier,
        PatchField<String> displayName
) {
    public WaypointListPatch {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(displayName, "displayName");
    }

    public static WaypointListPatch empty() {
        return new WaypointListPatch(PatchField.unchanged(), PatchField.unchanged());
    }
}
