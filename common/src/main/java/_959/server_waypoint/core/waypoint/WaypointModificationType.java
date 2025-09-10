package _959.server_waypoint.core.waypoint;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;

public enum WaypointModificationType {
    ADD,
    REMOVE,
    UPDATE;

    public String toVerb() {
        return switch (this) {
            case ADD -> "added";
            case REMOVE -> "removed";
            case UPDATE -> "updated";
        };
    }

    public TranslatableComponent toTranslatable() {
        return switch (this) {
            case ADD -> Component.translatable("waypoint.modification.type.add");
            case REMOVE -> Component.translatable("waypoint.modification.type.remove");
            case UPDATE -> Component.translatable("waypoint.modification.type.update");
        };
    }
}
