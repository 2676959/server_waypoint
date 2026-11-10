package _959.server_waypoint.perf.mods;

import com.google.gson.JsonObject;

import java.lang.reflect.Field;

final class ProductionChannel {
    private ProductionChannel() {
    }

    static JsonObject inspect(String loader) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("handshakeOwner", "production");
        result.addProperty("handshakeAccepted", false);
        String className = switch (loader) {
            case "fabric" -> "_959.server_waypoint.fabric.ServerWaypointFabricServer";
            case "forge" -> "_959.server_waypoint.forge.ServerWaypointForge";
            case "neoforge" -> "_959.server_waypoint.neoforge.ServerWaypointNeoForge";
            default -> throw new IllegalArgumentException(loader);
        };
        Class<?> type;
        try {
            type = Class.forName(className, false, ProductionChannel.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            result.addProperty("present", false);
            result.addProperty("channelRegistered", false);
            return result;
        }
        result.addProperty("present", true);
        result.addProperty("loadedClassSource", type.getProtectionDomain().getCodeSource().getLocation().toString());
        if ("fabric".equals(loader)) {
            Class<?> payload = Class.forName("_959.server_waypoint.common.network.payload.c2s.ClientHandshakeC2SPayload");
            Object id = payload.getField("ID").get(null);
            result.addProperty("channelRegistered", id != null);
            result.addProperty("channel", String.valueOf(id));
            return result;
        }
        Field field = type.getField("PACKET_CHANNEL");
        Object channel = field.get(null);
        result.addProperty("channelRegistered", channel != null);
        result.addProperty("channel", channel == null ? "" : channel.getClass().getName());
        return result;
    }
}
