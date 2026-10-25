package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Read-only property snapshots from the installed release, with no current core dependency. */
final class StorageAssertions {
    private StorageAssertions() {
    }

    static JsonObject snapshot() throws ReflectiveOperationException {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper storage snapshot requires server owner thread");
        }
        var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            throw new IllegalStateException("Published plugin absent");
        }
        Class<?> coreType = Class.forName("_959.server_waypoint.core.WaypointServerCore", true, plugin.getClass().getClassLoader());
        Object core = coreType.getField("INSTANCE").get(null);
        Map<?, ?> dimensions = (Map<?, ?>) coreType.getMethod("getFileManagerMap").invoke(core);
        JsonObject data = new JsonObject();
        for (var dimension : dimensions.entrySet()) {
            Object file = dimension.getValue();
            Map<?, ?> lists = (Map<?, ?>) file.getClass().getMethod("getWaypointListMap").invoke(file);
            JsonArray values = new JsonArray();
            for (Object list : lists.values()) {
                JsonObject value = new JsonObject();
                value.addProperty("list_name", call(list, "name").toString());
                Object displayName = call(list, "displayNameOverride");
                if (displayName != null) {
                    value.addProperty("display_name", displayName.toString());
                }
                value.addProperty("n", (Number) call(list, "getSyncNum"));
                JsonArray waypoints = new JsonArray();
                for (Object live : (Collection<?>) call(list, "simpleWaypoints")) {
                    // The release's public copy constructor takes one synchronized
                    // property snapshot, so getters cannot observe mixed updates.
                    Object waypoint = live.getClass().getConstructor(live.getClass()).newInstance(live);
                    JsonObject point = new JsonObject();
                    point.addProperty("name", call(waypoint, "name").toString());
                    Object label = call(waypoint, "displayNameOverride");
                    if (label != null) {
                        point.addProperty("display_name", label.toString());
                    }
                    point.addProperty("initials", call(waypoint, "initials").toString());
                    JsonArray position = new JsonArray();
                    for (String coordinate : new String[]{"x", "y", "z"}) {
                        position.add((Number) call(waypoint, coordinate));
                    }
                    point.add("pos", position);
                    point.addProperty("color", String.format(Locale.ROOT, "#%06X", ((Number) call(waypoint, "rgb")).intValue() & 0xFFFFFF));
                    point.addProperty("yaw", (Number) call(waypoint, "yaw"));
                    point.addProperty("global", (Boolean) call(waypoint, "global"));
                    JsonArray keywords = new JsonArray();
                    for (Object keyword : (Collection<?>) call(waypoint, "keywords")) {
                        keywords.add((String) keyword);
                    }
                    point.add("keywords", keywords);
                    point.addProperty("description", call(waypoint, "description").toString());
                    Object icon = call(waypoint, "icon");
                    if (icon != null) {
                        point.addProperty("icon", icon.toString());
                    }
                    waypoints.add(point);
                }
                value.add("waypoints", waypoints);
                values.add(value);
            }
            data.add(dimension.getKey().toString(), values);
        }
        JsonObject result = new JsonObject();
        result.add("dimensions", data);
        result.addProperty("origin", "installed-release-property-snapshot");
        return result;
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }
}
