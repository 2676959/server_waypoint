package _959.server_waypoint.core.edit;

import _959.server_waypoint.core.waypoint.WaypointPos;

import java.util.List;
import java.util.Objects;

public record WaypointPatch(
        PatchField<String> identifier,
        PatchField<String> displayName,
        PatchField<String> initials,
        PatchField<WaypointPos> position,
        PatchField<Integer> color,
        PatchField<Integer> yaw,
        PatchField<Boolean> visibility,
        PatchField<List<String>> keywords,
        PatchField<String> description
) {
    public WaypointPatch {
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(initials, "initials");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(yaw, "yaw");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(keywords, "keywords");
        Objects.requireNonNull(description, "description");
        if (keywords.isSet()) {
            keywords = PatchField.set(List.copyOf(keywords.requiredValue()));
        }
    }

    public static WaypointPatch empty() {
        return new WaypointPatch(
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged(),
                PatchField.unchanged()
        );
    }
}
