package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.player.PlayerInventorySlotChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import io.papermc.paper.event.player.PlayerItemFrameChangeEvent;
import org.bukkit.Keyed;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.security.MessageDigest;
import java.util.Collection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Passive receipts from actual native events, armed only by the lifecycle fixture. */
final class LifecycleAssertions implements Listener {
    private boolean armed;
    private final JsonArray events = new JsonArray();
    private int dropped;
    private final Map<UUID, JsonObject> allayHands = new HashMap<>();

    JsonObject arm() {
        owner();
        armed = true;
        events.asList().clear();
        allayHands.clear();
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
            value.addProperty("type", event.getView().getType().name());
            value.addProperty("rawSlot", event.getRawSlot());
            value.addProperty("slot", event.getSlot());
            value.addProperty("action", event.getAction().name());
            value.addProperty("click", event.getClick().name());
            value.add("items", classify(Arrays.asList(event.getCurrentItem(), event.getCursor())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) throws Exception {
        if (armed && event.getWhoClicked() instanceof Player player) {
            JsonObject value = playerEvent("inventory-drag", player);
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("type", event.getView().getType().name());
            value.addProperty("dragType", event.getType().name());
            JsonArray rawSlots = new JsonArray();
            event.getRawSlots().stream().sorted().forEach(rawSlots::add);
            value.add("rawSlots", rawSlots);
            value.add("items", classify(List.of(event.getOldCursor())));
            value.add("topItems", classify(Arrays.asList(event.getView().getTopInventory().getContents())));
            value.add("cursorItems", classify(List.of(player.getItemOnCursor())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareCraft(PrepareItemCraftEvent event) throws Exception {
        if (armed && event.getView().getPlayer() instanceof Player player) {
            JsonObject value = playerEvent("prepare-craft", player);
            value.addProperty("type", event.getView().getType().name());
            value.add("recipe", recipeKey(event.getRecipe()));
            value.add("matrix", classify(Arrays.asList(event.getInventory().getMatrix())));
            value.add("result", resultView(event.getInventory().getResult()));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPrepareResult(com.destroystokyo.paper.event.inventory.PrepareResultEvent event) throws Exception {
        if (armed && event.getView().getPlayer() instanceof Player player) {
            JsonObject value = playerEvent("prepare-result", player);
            value.addProperty("type", event.getView().getType().name());
            value.add("top", classify(Arrays.asList(event.getView().getTopInventory().getContents())));
            value.add("result", resultView(event.getResult()));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCraftItem(CraftItemEvent event) throws Exception {
        if (armed && event.getWhoClicked() instanceof Player player) {
            JsonObject value = playerEvent("craft-item", player);
            value.addProperty("type", event.getView().getType().name());
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("rawSlot", event.getRawSlot());
            value.add("recipe", recipeKey(event.getRecipe()));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCartographyItem(io.papermc.paper.event.player.CartographyItemEvent event) throws Exception {
        if (armed && event.getWhoClicked() instanceof Player player) {
            JsonObject value = playerEvent("cartography-item", player);
            value.addProperty("type", event.getView().getType().name());
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("rawSlot", event.getRawSlot());
            value.add("result", resultView(event.getCurrentItem()));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent event) throws Exception {
        if (armed && event.getPlayer() instanceof Player player) {
            JsonObject value = playerEvent("inventory-open", player);
            value.addProperty("type", event.getView().getType().name());
            value.add("topItems", classify(Arrays.asList(event.getView().getTopInventory().getContents())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) throws Exception {
        if (armed && event.getPlayer() instanceof Player player) {
            JsonObject value = playerEvent("inventory-close", player);
            value.addProperty("type", event.getView().getType().name());
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
    public void onEntityPickup(EntityPickupItemEvent event) throws Exception {
        if (armed && event.getEntity() instanceof Player player) {
            JsonObject value = playerEvent("entity-pickup", player);
            value.addProperty("cancelled", event.isCancelled());
            value.add("items", classify(List.of(event.getItem().getItemStack())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryPickup(InventoryPickupItemEvent event) throws Exception {
        if (!armed) {
            return;
        }
        Location location = event.getInventory().getLocation();
        if (location == null) {
            return;
        }
        Block hopper = location.getBlock();
        if (hopper.getType() != Material.HOPPER) {
            return;
        }
        JsonObject value = new JsonObject();
        value.addProperty("event", "inventory-pickup");
        value.addProperty("tick", Bukkit.getCurrentTick());
        value.addProperty("cancelled", event.isCancelled());
        value.addProperty("x", hopper.getX());
        value.addProperty("y", hopper.getY());
        value.addProperty("z", hopper.getZ());
        value.add("items", classify(List.of(event.getItem().getItemStack())));
        retain(value);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onCancelledFrameInteract(PlayerInteractEntityEvent event) throws Exception {
        if (!armed || !event.isCancelled() || !(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItem(event.getHand());
        JsonObject value = playerEvent("item-frame", event.getPlayer());
        value.addProperty("cancelled", true);
        value.addProperty("action", frame.getItem().getType().isAir() ? "PLACE" : "REMOVE");
        value.add("items", classify(List.of(held)));
        value.add("injected", classify(List.of(frame.getItem())));
        retain(value);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemFrame(PlayerItemFrameChangeEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("item-frame", event.getPlayer());
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("action", event.getAction().name());
            JsonObject items = classify(List.of(event.getItemStack()));
            value.add("items", items);
            value.add("injected", items.deepCopy());
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) throws Exception {
        if (armed) {
            JsonObject value = playerEvent("armor-stand", event.getPlayer());
            value.addProperty("cancelled", event.isCancelled());
            value.addProperty("slot", event.getSlot().name());
            value.add("items", classify(List.of(event.getPlayerItem())));
            value.add("injected", classify(List.of(event.getArmorStandItem())));
            retain(value);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void beforeAllay(PlayerInteractEntityEvent event) throws Exception {
        if (armed && event.getRightClicked() instanceof Allay allay) {
            allayHands.put(allay.getUniqueId(), classify(List.of(allay.getEquipment().getItemInMainHand())));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onAllay(PlayerInteractEntityEvent event) throws Exception {
        if (armed && event.getRightClicked() instanceof Allay allay) {
            JsonObject value = playerEvent("allay-interact", event.getPlayer());
            value.addProperty("cancelled", event.isCancelled());
            value.add("items", classify(List.of(event.getPlayer().getInventory().getItem(event.getHand()))));
            JsonObject injected = allayHands.remove(allay.getUniqueId());
            value.add("injected", injected == null ? classify(List.of()) : injected);
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
            JsonArray slots = new JsonArray();
            ItemStack[] contents = player.getInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", slot);
                entry.addProperty("material", stack.getType().getKey().toString());
                entry.addProperty("amount", stack.getAmount());
                slots.add(entry);
            }
            inventory.add("slots", slots);
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

    JsonObject classify(Collection<ItemStack> stacks) throws Exception {
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

    private JsonObject resultView(ItemStack stack) {
        JsonObject value = new JsonObject();
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            value.addProperty("material", "minecraft:air");
            value.addProperty("amount", 0);
            value.addProperty("protected", false);
            return value;
        }
        value.addProperty("material", stack.getType().getKey().toString());
        value.addProperty("amount", stack.getAmount());
        value.addProperty("protected", ItemGraphAssertions.tagged(stack));
        return value;
    }

    private static com.google.gson.JsonElement recipeKey(Recipe recipe) {
        if (recipe instanceof Keyed keyed) {
            return new com.google.gson.JsonPrimitive(keyed.getKey().toString());
        }
        return com.google.gson.JsonNull.INSTANCE;
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
