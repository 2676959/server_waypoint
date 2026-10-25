package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cheap native observations; client-issued action replies are not completion proof. */
final class BotAssertions implements Listener {
    private final Map<UUID, long[]> counters = new HashMap<>();

    private long[] counters(Player player) {
        return counters.computeIfAbsent(player.getUniqueId(), ignored -> new long[3]);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void movement(PlayerMoveEvent event) {
        if (event.hasChangedPosition()) {
            counters(event.getPlayer())[0]++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void inventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            counters(player)[1]++;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void command(PlayerCommandPreprocessEvent event) {
        counters(event.getPlayer())[2]++;
    }

    void sendMalformed() {
        var audited = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (!Bukkit.isPrimaryThread() || audited == null || Bukkit.getOnlinePlayers().size() != 1) {
            throw new IllegalStateException("One native player and audited plugin required");
        }
        byte[] body = new byte[5];
        var crc = new java.util.zip.CRC32();
        crc.update(body);
        var frame = java.nio.ByteBuffer.allocate(44);
        frame.putLong(0).putLong(0).putInt(0).putInt(0).putInt(1).put((byte) 0);
        frame.putInt(body.length).putInt((int) crc.getValue() ^ 1).putShort((short) body.length).put(body);
        Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(audited, "server_waypoint:message_chunk", frame.array());
    }

    JsonObject snapshot(boolean reset) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Player snapshot requires server owner thread");
        }
        if (reset) {
            counters.clear();
        }
        JsonArray players = new JsonArray();
        for (Player player : Bukkit.getOnlinePlayers()) {
            long[] counts = counters(player);
            JsonObject state = new JsonObject();
            state.addProperty("name", player.getName());
            state.addProperty("uuid", player.getUniqueId().toString());
            state.addProperty("movementEvents", counts[0]);
            state.addProperty("inventoryEvents", counts[1]);
            state.addProperty("commandEvents", counts[2]);
            var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
            if (plugin != null) {
                try {
                    var senderField = plugin.getClass().getDeclaredField("messageSender");
                    senderField.setAccessible(true);
                    Object sender = senderField.get(plugin);
                    state.addProperty("protocolAccepted", (Boolean) sender.getClass().getMethod("canSendChunkedMessage", Player.class).invoke(sender, player));
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException("Audited sender capability assertion unavailable", exception);
                }
            }
            state.addProperty("world", player.getWorld().getKey().toString());
            state.addProperty("x", player.getX());
            state.addProperty("y", player.getY());
            state.addProperty("z", player.getZ());
            JsonArray inventory = new JsonArray();
            for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                var item = player.getInventory().getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    JsonObject stack = new JsonObject();
                    stack.addProperty("slot", slot);
                    stack.addProperty("type", item.getType().getKey().toString());
                    stack.addProperty("count", item.getAmount());
                    inventory.add(stack);
                }
            }
            state.add("inventory", inventory);
            players.add(state);
        }
        JsonObject result = new JsonObject();
        result.add("players", players);
        result.addProperty("onlinePlayers", players.size());
        var audited = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (audited != null) {
            try {
                var field = audited.getClass().getDeclaredField("messageSender");
                field.setAccessible(true);
                Object sender = field.get(audited);
                for (String name : java.util.List.of("chunkedMessageSessions", "chunkedMessageCapablePlayers")) {
                    var resource = sender.getClass().getDeclaredField(name);
                    resource.setAccessible(true);
                    Object value = resource.get(sender);
                    result.addProperty(name, value instanceof Map<?, ?> map ? map.size() : ((java.util.Set<?>) value).size());
                }
                Class<?> contract = Class.forName("_959.server_waypoint.core.network.PlatformMessageSender", true, audited.getClass().getClassLoader());
                var managerAccessor = contract.getDeclaredMethod("chunkedMessageManager");
                managerAccessor.setAccessible(true);
                Object manager = managerAccessor.invoke(sender);
                var retained = manager.getClass().getDeclaredMethod("globallyRetainedBytes");
                retained.setAccessible(true);
                result.addProperty("chunkedRetainedBytes", ((Number) retained.invoke(manager)).longValue());
                Class<?> coreClass = Class.forName("_959.server_waypoint.core.WaypointServerCore", true, audited.getClass().getClassLoader());
                Object core = coreClass.getField("INSTANCE").get(null);
                Map<?, ?> dimensions = (Map<?, ?>) coreClass.getMethod("getFileManagerMap").invoke(core);
                JsonArray lists = new JsonArray();
                for (var dimension : dimensions.entrySet()) {
                    Object file = dimension.getValue();
                    Map<?, ?> fileLists = (Map<?, ?>) file.getClass().getMethod("getWaypointListMap").invoke(file);
                    for (var entry : fileLists.entrySet()) {
                        Object list = entry.getValue();
                        JsonObject state = new JsonObject();
                        state.addProperty("dimension", dimension.getKey().toString());
                        state.addProperty("name", entry.getKey().toString());
                        state.addProperty("revision", ((Number) list.getClass().getMethod("getSyncNum").invoke(list)).intValue());
                        JsonArray names = new JsonArray();
                        for (Object waypoint : (java.util.Collection<?>) list.getClass().getMethod("simpleWaypoints").invoke(list)) {
                            names.add(waypoint.getClass().getMethod("name").invoke(waypoint).toString());
                        }
                        state.add("waypoints", names);
                        lists.add(state);
                    }
                }
                result.add("waypointLists", lists);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Audited protocol resource assertion unavailable", exception);
            }
        }
        return result;
    }
}
