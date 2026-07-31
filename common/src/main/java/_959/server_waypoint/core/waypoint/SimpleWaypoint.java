package _959.server_waypoint.core.waypoint;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;

import static _959.server_waypoint.core.WaypointServerCore.LOGGER;
import static _959.server_waypoint.util.ColorUtils.*;

public class SimpleWaypoint {
    @Expose private String name;
    @Expose private String initials;
    @Expose private WaypointPos pos;
    @Expose @SerializedName("color") @JsonAdapter(ColorToHexCodeSerializer.class) private int rgb;
    @Expose private int yaw;
    @Expose private boolean global;
    private static final String SEPARATOR = ":";
    // not on paper
    //? if !paper {
    public volatile int renderId = -1; // -1 means not in waypoint render

    public boolean isRendered() {
        return this.renderId != -1;
    }

    // this is used for prevent incorrect deserialization by gson
    @SuppressWarnings("unused")
    private SimpleWaypoint() {
        this.renderId = -1;
    }
    //?}

    public SimpleWaypoint(String name, String initials, WaypointPos pos, int rgb, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = pos;
        this.rgb = rgb;
        this.yaw = convertYaw(yaw);
        this.global = global;
        //? if !paper
        this.renderId = -1;
    }

    public SimpleWaypoint(String name, String initials, int x, int y, int z, int rgb, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = new WaypointPos(x, y, z);
        this.rgb = rgb;
        this.yaw = convertYaw(yaw);
        this.global = global;
        //? if !paper
        this.renderId = -1;
    }

    // do not need to copy renderId as renderId should be unique for each instance
    public SimpleWaypoint(SimpleWaypoint other) {
        State state = other.snapshotState();
        this.name = state.name();
        this.initials = state.initials();
        this.pos = state.pos();
        this.rgb = state.rgb();
        this.yaw = state.yaw();
        this.global = state.global();
        //? if !paper
        this.renderId = -1;
    }

    private int convertYaw(int yaw) {
        yaw %= 360;
        return (yaw > 180) ? (yaw - 360) : (yaw < -180 ? yaw + 360 : yaw);
    }

    void copyFrom(SimpleWaypoint other) {
        State state = other.snapshotState();
        synchronized (this) {
            this.applyState(state);
        }
    }

    public synchronized String name() {
        return this.name;
    }

    public synchronized String initials() {
        return this.initials;
    }

    public synchronized WaypointPos pos() {
        return this.pos;
    }

    public synchronized int x() {
        return this.pos.x();
    }

    public synchronized int y() {
        return this.pos.y();
    }

    public synchronized int z() {
        return this.pos.z();
    }

    public synchronized float X() {
        return this.pos.X();
    }

    public synchronized float Y() {
        return this.pos.Y();
    }

    public synchronized float Z() {
        return this.pos.Z();
    }

    public synchronized int rgb() {
        return this.rgb;
    }

    public synchronized int yaw() {
        return this.yaw;
    }

    public synchronized boolean global() {
        return this.global;
    }

    public synchronized String toString() {
        return "SimpleWaypoint{name='" + this.name + "', initials='" + this.initials + "', pos=" + this.pos + ", rgb=" + this.rgb + ", yaw=" + this.yaw + ", global=" + this.global + "}";
    }

    public static SimpleWaypoint fromString(String waypointString) throws NumberFormatException {
        String[] args = waypointString.split(SEPARATOR);
        int colorIdx = Integer.parseInt(args[5]);
        int rgb = colorIndexToRgb(colorIdx);
        return new SimpleWaypoint(args[0], args[1], new WaypointPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])), rgb, Integer.parseInt(args[6]), Boolean.parseBoolean(args[7]));
    }

    synchronized boolean compareProperties(String name, String initials, WaypointPos pos, int colorIdx, int yaw, boolean global) {
        return this.name.equals(name) && this.initials.equals(initials) && this.pos.equals(pos) && this.rgb == colorIdx && this.yaw == convertYaw(yaw) && this.global == global;
    }

    synchronized void updateProperties(String name, String initials, WaypointPos pos, int rgb, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = pos;
        this.rgb = rgb;
        this.yaw = convertYaw(yaw);
        this.global = global;
    }

    private synchronized State snapshotState() {
        return new State(this.name, this.initials, this.pos, this.rgb, this.yaw, this.global);
    }

    private void applyState(State state) {
        this.name = state.name();
        this.initials = state.initials();
        this.pos = state.pos();
        this.rgb = state.rgb();
        this.yaw = state.yaw();
        this.global = state.global();
    }

    private record State(String name, String initials, WaypointPos pos, int rgb, int yaw, boolean global) {
    }

    public static class ColorToHexCodeSerializer implements JsonSerializer<Integer>, JsonDeserializer<Integer> {
        @Override
        public JsonElement serialize(Integer integer, Type type, JsonSerializationContext jsonSerializationContext) {
            if (integer == null) {
                return null;
            }
            return new JsonPrimitive(rgbToHexCode(integer, true));
        }

        @Override
        public Integer deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            if (jsonElement == null || jsonElement.isJsonNull()) {
                return null;
            }
            String hexCode = jsonElement.getAsString();
            int color = hexCodeToRgb(hexCode, true);
            if (color < 0) {
                LOGGER.warn("found invalid hex code: {}, replaced with #39C5BB", hexCode);
                return 0x39C5BB;
            } else {
                return color;
            }
        }
    }
}
