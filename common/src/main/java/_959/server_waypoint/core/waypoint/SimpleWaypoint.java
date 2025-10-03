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

    public SimpleWaypoint(String name, String initials, WaypointPos pos, int rgb, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = pos;
        this.rgb = rgb;
        this.yaw = convertYaw(yaw);
        this.global = global;
    }

    public SimpleWaypoint(String name, String initials, int x, int y, int z, int rgb, int yaw, boolean global) {
        this.name = name;
        this.initials = initials;
        this.pos = new WaypointPos(x, y, z);
        this.rgb = rgb;
        this.yaw = convertYaw(yaw);
        this.global = global;
    }

    private int convertYaw(int yaw) {
        boolean isNegative = yaw < 0;
        int r = Math.abs(yaw) % 360;
        r = (r <= 180) ? r : r - 360;
        r = isNegative ? -r : r;
        return r;
    }

    public void copyFrom(SimpleWaypoint other) {
        this.initials = other.initials;
        this.pos = other.pos;
        this.colorIdx = other.colorIdx;
        this.yaw = other.yaw;
        this.global = other.global;
    }

    public String name() {
        return this.name;
    }

    public String initials() {
        return this.initials;
    }

    public WaypointPos pos() {
        return this.pos;
    }

    public int rgb() {
        return this.rgb;
    }

    public int yaw() {
        return this.yaw;
    }

    public boolean global() {
        return this.global;
    }

    public SimpleWaypoint setName(String name) {
        this.name = name;
        return this;
    }

    public void setInitials(String initials) {
        this.initials = initials;
    }

    public void setPos(WaypointPos pos) {
        this.pos = pos;
    }

    public void setRgb(int rgb) {
        this.rgb = rgb;
    }

    public void setYaw(int yaw) {
        this.yaw = convertYaw(yaw);
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    public String toString() {
        return "SimpleWaypoint{name='" + this.name + "', initials='" + this.initials + "', pos=" + this.pos + ", rgb=" + this.rgb + ", yaw=" + this.yaw + ", global=" + this.global + "}";
    }

    public static SimpleWaypoint fromString(String waypointString) throws NumberFormatException {
        String[] args = waypointString.split(SEPARATOR);
        int colorIdx = Integer.parseInt(args[5]);
        int rgb = colorIndexToRgb(colorIdx);
        return new SimpleWaypoint(args[0], args[1], new WaypointPos(Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4])), rgb, Integer.parseInt(args[6]), Boolean.parseBoolean(args[7]));
    }

    public boolean compareProperties(String initials, WaypointPos pos, int colorIdx, int yaw, boolean global) {
        return this.initials.equals(initials) && this.pos.equals(pos) && this.rgb == colorIdx && this.yaw == convertYaw(yaw) && this.global == global;
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
