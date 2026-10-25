package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Passive receipts from actual native events, armed only by the lifecycle fixture. */
final class LifecycleAssertions implements Listener {
    private boolean armed;
    private final JsonArray events = new JsonArray();
    private int dropped;

    JsonObject arm() {
        owner();
        armed = true;
        events.asList().clear();
        dropped = 0;
        JsonObject result = new JsonObject();
        result.addProperty("origin", "native-lifecycle-monitor");
        return result;
    }

    JsonObject prepareNether() {
        owner();
        var world = Bukkit.getWorlds().stream()
                .filter(value -> value.getKey().toString().equals("minecraft:the_nether"))
                .findFirst().orElseThrow(() -> new IllegalStateException("Native Nether absent"));
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                world.getChunkAt(x, z);
                world.setChunkForceLoaded(x, z, true);
                if (!world.isChunkLoaded(x, z) || !world.isChunkForceLoaded(x, z)) {
                    throw new IllegalStateException("Native Nether fixture chunk not loaded/ticking");
                }
            }
        }
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                world.getBlockAt(x, 78, z).setType(Material.BEDROCK, false);
                boolean boundary = Math.abs(x) == 12 || Math.abs(z) == 12;
                for (int y = 79; y <= 83; y++) {
                    world.getBlockAt(x, y, z).setType(boundary && y <= 81 ? Material.BEDROCK : Material.AIR, false);
                }
            }
        }
        int pad = 0;
        int walls = 0;
        int clear = 0;
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (world.getBlockAt(x, 78, z).getType() == Material.BEDROCK) {
                    pad++;
                }
                boolean boundary = Math.abs(x) == 12 || Math.abs(z) == 12;
                for (int y = 79; y <= 83; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (boundary && y <= 81 && type == Material.BEDROCK) {
                        walls++;
                    } else if (!boundary && type == Material.AIR) {
                        clear++;
                    }
                }
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("dimension", world.getKey().toString());
        result.addProperty("padBlocks", pad);
        result.addProperty("wallBlocks", walls);
        result.addProperty("clearInteriorBlocks", clear);
        result.addProperty("verified", pad == 625 && walls == 288 && clear == 2645);
        if (!result.get("verified").getAsBoolean()) {
            throw new IllegalStateException("Native Nether landing blocks did not verify");
        }
        return result;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("death", event.getEntity());
            value.addProperty("keepInventory", event.getKeepInventory());
            value.add("drops", classify(event.getDrops()));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (armed) {
            JsonObject value = playerEvent("respawn", event.getPlayer());
            value.addProperty("destination", event.getRespawnLocation().getWorld().getKey().toString());
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (armed) {
            JsonObject value = playerEvent("world-change", event.getPlayer());
            value.addProperty("from", event.getFrom().getKey().toString());
            value.addProperty("to", event.getPlayer().getWorld().getKey().toString());
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemSpawn(ItemSpawnEvent event) throws Exception {
        if (armed) {
            JsonObject value = new JsonObject();
            value.addProperty("event", "item-spawn");
            value.addProperty("tick", Bukkit.getCurrentTick());
            value.addProperty("uuid", event.getEntity().getUniqueId().toString());
            value.addProperty("cancelled", event.isCancelled());
            value.add("items", classify(List.of(event.getEntity().getItemStack())));
            retain(value);
        }
    }

    JsonObject snapshot() throws Exception {
        owner();
        if (!armed) {
            throw new IllegalStateException("Lifecycle observations must be armed explicitly");
        }
        var items = new java.util.ArrayList<ItemStack>();
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (items.size() >= 10000) {
                    throw new IllegalStateException("Lifecycle world-item observation limit");
                }
                items.add(item.getItemStack());
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("origin", "native-lifecycle-monitor");
        result.add("events", events.deepCopy());
        result.addProperty("droppedObservations", dropped);
        result.add("worldItems", classify(items));
        return result;
    }

    private JsonObject classify(Collection<ItemStack> stacks) throws Exception {
        var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            throw new IllegalStateException("Installed release absent");
        }
        Object manager = NavigationAssertions.field(plugin, "navigationItemManager");
        var direct = manager.getClass().getMethod("isNavigationItem", ItemStack.class);
        var contains = manager.getClass().getMethod("containsNavigationItem", ItemStack.class);
        int protectedItems = 0;
        int nestedCarriers = 0;
        Map<String, Integer> ordinary = new TreeMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            if ((Boolean) direct.invoke(manager, stack)) {
                protectedItems += stack.getAmount();
            } else if ((Boolean) contains.invoke(manager, stack)) {
                nestedCarriers += stack.getAmount();
            } else {
                ItemStack identity = stack.clone();
                identity.setAmount(1);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(identity.serializeAsBytes()));
                ordinary.merge(stack.getType().getKey() + ":" + hash, stack.getAmount(), Integer::sum);
            }
        }
        JsonObject result = new JsonObject();
        JsonObject values = new JsonObject();
        ordinary.forEach(values::addProperty);
        result.add("ordinary", values);
        result.addProperty("protectedItems", protectedItems);
        result.addProperty("nestedProtectedCarriers", nestedCarriers);
        return result;
    }

    private JsonObject playerEvent(String event, Player player) {
        JsonObject value = new JsonObject();
        value.addProperty("event", event);
        value.addProperty("tick", Bukkit.getCurrentTick());
        value.addProperty("uuid", player.getUniqueId().toString());
        value.addProperty("name", player.getName());
        return value;
    }

    private void retain(JsonObject value) {
        owner();
        if (events.size() >= 1000) {
            dropped++;
        } else {
            events.add(value);
        }
    }

    private void owner() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper lifecycle observation requires server owner thread");
        }
    }
}
