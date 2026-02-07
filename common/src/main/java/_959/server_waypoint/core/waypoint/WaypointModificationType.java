package _959.server_waypoint.core.waypoint;

import net.kyori.adventure.text.TranslatableComponent;

import static net.kyori.adventure.text.Component.translatable;

public enum WaypointModificationType {
    /** add a new waypoint */
    ADD,
    /** add an empty waypoint list */
    ADD_LIST,
    /** remove a waypoint list */
    REMOVE,
    /** remove an empty waypoint list */
    REMOVE_LIST,
    /** update a waypoint */
    UPDATE;

    public TranslatableComponent toTranslatable() {
        return switch (this) {
            case ADD, ADD_LIST -> translatable("waypoint.modification.type.add");
            case REMOVE, REMOVE_LIST -> translatable("waypoint.modification.type.remove");
            case UPDATE -> translatable("waypoint.modification.type.update");
        };
    }
}
