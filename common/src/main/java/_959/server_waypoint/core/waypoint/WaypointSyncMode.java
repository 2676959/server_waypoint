package _959.server_waypoint.core.waypoint;

import com.google.gson.annotations.SerializedName;
import net.kyori.adventure.text.TranslatableComponent;

import static net.kyori.adventure.text.Component.translatable;

public enum WaypointSyncMode {
    @SerializedName("replace_list")
    REPLACE_LIST("replace_list"),
    @SerializedName("tracked_waypoints")
    TRACKED_WAYPOINTS("tracked_waypoints");

    private final String name;

    WaypointSyncMode(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return this.name;
    }

    public TranslatableComponent toTranslatable() {
        return translatable("waypoint.sync_mode." + this.name);
    }

    public static WaypointSyncMode fromSerializedName(String name) {
        for (WaypointSyncMode mode : values()) {
            if (mode.name.equalsIgnoreCase(name)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown waypoint sync mode: " + name);
    }
}
