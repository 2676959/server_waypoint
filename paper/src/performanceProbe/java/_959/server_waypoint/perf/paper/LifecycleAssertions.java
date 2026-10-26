package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.Arrays;
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
    public void onDrop(PlayerDropItemEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("drop", event.getPlayer());
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("itemEntityUuid", event.getItemDrop().getUniqueId().toString());
            value.addProperty("material", event.getItemDrop().getItemStack().getType().getKey().toString());
            value.addProperty("amount", event.getItemDrop().getItemStack().getAmount());
            value.add("items", classify(List.of(event.getItemDrop().getItemStack())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("swap", event.getPlayer());
            value.addProperty("cancelled", event.isCancelled());
            value.add("items", classify(Arrays.asList(event.getMainHandItem(), event.getOffHandItem())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) throws Exception {
        if (armed && event.getWhoClicked() instanceof Player player) {
            JsonObject value = playerEvent("inventory-click", player);
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("rawSlot", event.getRawSlot());
            value.addProperty("slot", event.getSlot());
            value.addProperty("action", event.getAction().name());
            value.addProperty("click", event.getClick().name());
            value.add("items", classify(Arrays.asList(event.getCurrentItem(), event.getCursor())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) throws Exception {
        if (armed && event.getPlayer() instanceof Player player) {
            JsonObject value = playerEvent("inventory-close", player);
            value.add("topItems", classify(Arrays.asList(event.getView().getTopInventory().getContents())));
            value.add("cursorItems", classify(List.of(player.getItemOnCursor())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSlotChange(PlayerInventorySlotChangeEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("inventory-slot-change", event.getPlayer());
            value.addProperty("slot", event.getSlot());
            value.add("previous", classify(List.of(event.getOldItemStack())));
            value.add("current", classify(List.of(event.getNewItemStack())));
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
        JsonArray worldEntities = new JsonArray();
        for (var world : Bukkit.getWorlds()) {
            for (Item item : world.getEntitiesByClass(Item.class)) {
                if (items.size() >= 10000) {
                    throw new IllegalStateException("Lifecycle world-item observation limit");
                }
                items.add(item.getItemStack());
                JsonObject entity = new JsonObject();
                entity.addProperty("uuid", item.getUniqueId().toString());
                entity.addProperty("material", item.getItemStack().getType().getKey().toString());
                entity.addProperty("amount", item.getItemStack().getAmount());
                worldEntities.add(entity);
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("origin", "native-lifecycle-monitor");
        result.add("events", events.deepCopy());
        result.addProperty("droppedObservations", dropped);
        result.add("worldItems", classify(items));
        result.add("worldEntities", worldEntities);
        // Read world and inventory counts within this same owner-thread
        // command so an intervening pickup cannot create an apparent loss.
        JsonObject inventories = new JsonObject();
        for (Player player : Bukkit.getOnlinePlayers()) {
            JsonObject inventory = classify(Arrays.asList(player.getInventory().getContents()));
            inventory.add("cursor", classify(List.of(player.getItemOnCursor())));
            inventories.add(player.getName(), inventory);
        }
        result.add("inventories", inventories);
        return result;
    }

    JsonObject spawnReceipt(java.util.UUID id) {
        owner();
        JsonObject found = null;
        for (var event : events) {
            JsonObject value = event.getAsJsonObject();
            if (value.get("event").getAsString().equals("item-spawn") && value.get("uuid").getAsString().equals(id.toString())) {
                if (found != null) {
                    throw new IllegalStateException("Duplicate native spawn receipt");
                }
                found = value;
            }
        }
        if (found == null || dropped != 0) {
            throw new IllegalStateException("Complete native spawn receipt absent");
        }
        return found.deepCopy();
    }

    private JsonObject classify(Collection<ItemStack> stacks) throws Exception {
        var plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            throw new IllegalStateException("Installed release absent");
        }
        int protectedItems = 0;
        int nestedCarriers = 0;
        Map<String, Integer> ordinary = new TreeMap<>();
        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            JsonObject graph = ItemGraphAssertions.inspect(stack);
            if (graph.getAsJsonObject("root").get("tagged").getAsBoolean()) {
                protectedItems += stack.getAmount();
            } else if (graph.get("protectedUnits").getAsLong() != 0) {
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
