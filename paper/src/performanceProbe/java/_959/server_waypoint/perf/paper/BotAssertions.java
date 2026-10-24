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
        return result;
    }
}
