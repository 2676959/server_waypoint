package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Allay;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Owned native spawn fixtures; expected clean carriers are constructed separately. */
final class ProtectionFixtures {
    private static final NamespacedKey MARKER = new NamespacedKey("swperf", "fixture_marker");
    private static final Set<String> KINDS = Set.of("direct", "shulker", "bundle-1", "mixed", "bundle-8", "bundle-9", "plain-shulker", "plain-bundle-9");
    private static final Set<String> CONTAINER_KINDS = Set.of("direct", "nested", "ordinary", "empty");
    private final LifecycleAssertions lifecycle;
    private static final Set<String> STATION_KINDS = Set.of("workbench", "cartography");
    private static final Set<String> SEED_MATERIALS = Set.of("paper", "compass", "filled_map");
    private final Map<UUID, Entry> owned = new HashMap<>();
    private final Map<String, ContainerEntry> containers = new HashMap<>();
    private final Map<String, StationEntry> stations = new HashMap<>();
    private final Map<String, EntityFixture> entityFixtures = new HashMap<>();

    ProtectionFixtures(LifecycleAssertions lifecycle) {
        this.lifecycle = lifecycle;
    }

    JsonObject spawn(JsonObject request) throws Exception {
        owner();
        if (owned.size() >= 256) {
            throw new IllegalStateException("Owned protection fixture limit");
        }
        String kind = request.get("kind").getAsString();
        if (!KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown nested fixture kind");
        }
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native fixture owner absent");
        }
        ItemStack navigation = navigationItem(player, request.get("method").getAsString());
        ItemStack actual = create(kind, navigation, !kind.startsWith("plain-"));
        // This construction never invokes a release sanitizer or consumes its
        // output. Every ordinary child/name/lore/PDC marker is fixed by recipe.
        ItemStack expected = kind.equals("direct") ? new ItemStack(Material.AIR) : create(kind, navigation, false);
        byte[] beforeBytes = actual.serializeAsBytes();
        ItemStack decoded = ItemStack.deserializeBytes(beforeBytes);
        boolean valid = actual.getAmount() == decoded.getAmount() && actual.isSimilar(decoded);
        if (!valid) {
            throw new IllegalStateException("Native nested fixture codec roundtrip differs");
        }
        JsonObject before = ItemGraphAssertions.inspect(actual);
        JsonObject expectedAfter = ItemGraphAssertions.inspect(expected);
        Object plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        Object manager = NavigationAssertions.field(plugin, "navigationItemManager");
        boolean productTagged = (Boolean) manager.getClass().getMethod("isNavigationItem", ItemStack.class).invoke(manager, actual);
        var location = player.getLocation().add(1.5, 0.5, 1.5);
        location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Item entity = location.getWorld().dropItem(location, actual.clone());
        entity.setPickupDelay(32767);
        UUID id = entity.getUniqueId();
        owned.put(id, new Entry(entity, before, expectedAfter, beforeBytes, valid, productTagged));
        JsonObject key = new JsonObject();
        key.addProperty("entityUuid", id.toString());
        return snapshot(key);
    }

    JsonObject snapshot(JsonObject request) throws Exception {
        owner();
        UUID id = UUID.fromString(request.get("entityUuid").getAsString());
        Entry entry = owned.get(id);
        if (entry == null) {
            throw new IllegalArgumentException("Unowned fixture entity");
        }
        JsonObject spawn = lifecycle.spawnReceipt(id);
        ItemStack after = entry.entity().getItemStack();
        JsonObject result = new JsonObject();
        result.addProperty("trigger", "native-world-drop-item");
        result.addProperty("entityUuid", id.toString());
        result.addProperty("worldPresent", entry.entity().getWorld().getEntity(id) != null);
        result.addProperty("spawnCancelled", spawn.get("cancelled").getAsBoolean());
        result.addProperty("roundTripValid", entry.roundTripValid());
        result.addProperty("installedTopLevelTagBefore", entry.productTagged());
        result.addProperty("beforeItemBytesBase64", Base64.getEncoder().encodeToString(entry.beforeBytes()));
        if (!after.getType().isAir() && after.getAmount() > 0) {
            result.addProperty("afterItemBytesBase64", Base64.getEncoder().encodeToString(after.serializeAsBytes()));
        }
        result.add("before", entry.before().deepCopy());
        result.add("expectedAfter", entry.expected().deepCopy());
        result.add("after", ItemGraphAssertions.inspect(after));
        result.add("spawnReceipt", spawn);
        return result;
    }

    JsonObject remove(JsonObject request) {
        owner();
        UUID id = UUID.fromString(request.get("entityUuid").getAsString());
        Entry entry = owned.remove(id);
        if (entry == null) {
            throw new IllegalArgumentException("Refusing to remove an unowned entity");
        }
        entry.entity().remove();
        JsonObject result = new JsonObject();
        result.addProperty("entityUuid", id.toString());
        result.addProperty("worldPresent", entry.entity().getWorld().getEntity(id) != null);
        result.addProperty("ownedRemaining", owned.size());
        return result;
    }

    JsonObject placeContainer(JsonObject request) throws Exception {
        owner();
        if (containers.size() >= 64) {
            throw new IllegalStateException("Owned other-container fixture limit");
        }
        String kind = request.get("kind").getAsString();
        if (!CONTAINER_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown other-container fixture kind");
        }
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native other-container owner absent");
        }
        if (containers.containsKey(player.getName())) {
            throw new IllegalStateException("Owner already has an other-container fixture");
        }
        int x = request.get("x").getAsInt();
        int y = request.get("y").getAsInt();
        int z = request.get("z").getAsInt();
        Block block = player.getWorld().getBlockAt(x, y, z);
        block.getChunk().load();
        block.setType(Material.CHEST, false);
        if (!(block.getState(false) instanceof Chest chest)) {
            throw new IllegalStateException("Native chest fixture missing");
        }
        Inventory inventory = chest.getBlockInventory();
        inventory.clear();
        switch (kind) {
            case "direct" -> {
                inventory.setItem(0, navigationItem(player, request.get("method").getAsString()));
                inventory.setItem(1, ordinary(Material.DIAMOND, 3, "chest-diamonds"));
                inventory.setItem(2, ordinary(Material.STONE, 16, "chest-stone"));
            }
            case "nested" -> inventory.setItem(0, shulker(navigationItem(player, request.get("method").getAsString())));
            case "ordinary" -> {
                inventory.setItem(1, ordinary(Material.DIAMOND, 3, "chest-diamonds"));
                inventory.setItem(2, ordinary(Material.STONE, 16, "chest-stone"));
            }
            default -> {
            }
        }
        List<ItemStack> expectedItems = expectedContents(kind);
        JsonObject expectedTop = lifecycle.classify(expectedItems);
        JsonArray expectedGraphs = new JsonArray();
        for (ItemStack item : expectedItems) {
            if (item != null && !item.getType().isAir() && item.getAmount() > 0) {
                expectedGraphs.add(ItemGraphAssertions.inspect(item));
            }
        }
        containers.put(player.getName(), new ContainerEntry(block, player.getUniqueId(), expectedTop, expectedGraphs));
        return snapshotContainer(request);
    }

    JsonObject snapshotContainer(JsonObject request) throws Exception {
        owner();
        ContainerEntry entry = container(request);
        if (!(entry.block().getState(false) instanceof Chest chest)) {
            throw new IllegalStateException("Owned other-container missing");
        }
        Player player = Bukkit.getPlayer(entry.owner());
        JsonObject result = new JsonObject();
        result.addProperty("inventoryType", "CHEST");
        result.addProperty("x", entry.block().getX());
        result.addProperty("y", entry.block().getY());
        result.addProperty("z", entry.block().getZ());
        result.addProperty("kind", request.has("kind") ? request.get("kind").getAsString() : "snapshot");
        result.add("top", lifecycle.classify(Arrays.asList(chest.getBlockInventory().getContents())));
        var graphs = new JsonArray();
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                graphs.add(ItemGraphAssertions.inspect(item));
            }
        }
        result.add("graphs", graphs);
        result.add("expectedTop", entry.expectedTop().deepCopy());
        result.add("expectedGraphs", entry.expectedGraphs().deepCopy());
        if (player != null) {
            result.add("cursor", lifecycle.classify(List.of(player.getItemOnCursor())));
        }
        return result;
    }

    JsonObject dirtyContainer(JsonObject request) throws Exception {
        owner();
        ContainerEntry entry = container(request);
        if (!(entry.block().getState(false) instanceof Chest chest)) {
            throw new IllegalStateException("Owned other-container missing");
        }
        Player player = Bukkit.getPlayer(entry.owner());
        if (player == null) {
            throw new IllegalStateException("Native other-container owner absent");
        }
        String inject = request.get("inject").getAsString();
        if (!Set.of("top", "cursor", "both").contains(inject)) {
            throw new IllegalArgumentException("Unknown other-container dirty injection");
        }
        ItemStack clone = navigationItem(player, request.get("method").getAsString());
        if ("top".equals(inject) || "both".equals(inject)) {
            chest.getBlockInventory().setItem(0, clone);
        }
        if ("cursor".equals(inject) || "both".equals(inject)) {
            player.setItemOnCursor(clone.clone());
        }
        return snapshotContainer(request);
    }

    JsonObject setDragCursor(JsonObject request) throws Exception {
        owner();
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native drag cursor owner absent");
        }
        int amount = request.get("amount").getAsInt();
        if (amount < 2 || amount > 64) {
            throw new IllegalArgumentException("Stackable drag cursor amount required");
        }
        ItemStack stack = new ItemStack(Material.PAPER, amount);
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(
                new NamespacedKey("server_waypoint", "navigation_item"),
                PersistentDataType.BOOLEAN,
                true);
        stack.setItemMeta(meta);
        if (!ItemGraphAssertions.tagged(stack) || stack.getAmount() != amount) {
            throw new IllegalStateException("Stackable tagged drag cursor was not constructed");
        }
        Object plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        Object manager = NavigationAssertions.field(plugin, "navigationItemManager");
        boolean productTagged = (Boolean) manager.getClass()
                .getMethod("isNavigationItem", ItemStack.class)
                .invoke(manager, stack);
        if (!productTagged) {
            throw new IllegalStateException("Installed release does not treat the drag cursor as a navigation item");
        }
        player.setItemOnCursor(stack);
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("amount", amount);
        result.addProperty("material", stack.getType().getKey().toString());
        result.add("cursor", lifecycle.classify(List.of(player.getItemOnCursor())));
        return result;
    }

    JsonObject removeContainer(JsonObject request) {
        owner();
        ContainerEntry entry = containers.remove(request.get("player").getAsString());
        if (entry == null) {
            throw new IllegalArgumentException("Unowned other-container fixture");
        }
        entry.block().setType(Material.AIR, false);
        JsonObject result = new JsonObject();
        result.addProperty("player", request.get("player").getAsString());
        result.addProperty("ownedRemaining", containers.size());
        result.addProperty("present", entry.block().getType() == Material.CHEST);
        return result;
    }

    JsonObject placeStation(JsonObject request) {
        owner();
        if (stations.size() >= 64) {
            throw new IllegalStateException("Owned station fixture limit");
        }
        String kind = request.get("kind").getAsString();
        if (!STATION_KINDS.contains(kind)) {
            throw new IllegalArgumentException("Unknown station fixture kind");
        }
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native station owner absent");
        }
        if (stations.containsKey(player.getName())) {
            throw new IllegalStateException("Owner already has a station fixture");
        }
        int x = request.get("x").getAsInt();
        int y = request.get("y").getAsInt();
        int z = request.get("z").getAsInt();
        Block block = player.getWorld().getBlockAt(x, y, z);
        block.getChunk().load();
        Material material = "workbench".equals(kind) ? Material.CRAFTING_TABLE : Material.CARTOGRAPHY_TABLE;
        block.setType(material, false);
        if (block.getType() != material) {
            throw new IllegalStateException("Native station fixture missing");
        }
        stations.put(player.getName(), new StationEntry(block, kind));
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("kind", kind);
        result.addProperty("inventoryType", "workbench".equals(kind) ? "WORKBENCH" : "CARTOGRAPHY");
        result.addProperty("x", x);
        result.addProperty("y", y);
        result.addProperty("z", z);
        result.addProperty("present", true);
        return result;
    }

    JsonObject seedStation(JsonObject request) throws Exception {
        owner();
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native station owner absent");
        }
        String materialName = request.get("material").getAsString();
        if (!SEED_MATERIALS.contains(materialName)) {
            throw new IllegalArgumentException("Unknown station seed material");
        }
        int amount = request.get("amount").getAsInt();
        if (amount < 1 || amount > 64 || (!"paper".equals(materialName) && amount != 1)) {
            throw new IllegalArgumentException("Station seed amount is outside the recipe");
        }
        ItemStack stack = switch (materialName) {
            case "paper" -> new ItemStack(Material.PAPER, amount);
            case "compass" -> new ItemStack(Material.COMPASS, 1);
            case "filled_map" -> plainFilledMap(player);
            default -> throw new IllegalArgumentException("Unknown station seed material");
        };
        if (ItemGraphAssertions.tagged(stack) || stack.getAmount() != amount) {
            throw new IllegalStateException("Station seed was tagged or the wrong size");
        }
        Object plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        Object manager = NavigationAssertions.field(plugin, "navigationItemManager");
        boolean productTagged = (Boolean) manager.getClass()
                .getMethod("isNavigationItem", ItemStack.class)
                .invoke(manager, stack);
        if (productTagged) {
            throw new IllegalStateException("Installed release treats the station seed as a navigation item");
        }
        int slot = -1;
        for (int index = 9; index <= 35; index++) {
            ItemStack current = player.getInventory().getItem(index);
            if (current == null || current.getType().isAir()) {
                slot = index;
                break;
            }
        }
        if (slot < 0) {
            throw new IllegalStateException("No empty storage slot for the station seed");
        }
        player.getInventory().setItem(slot, stack);
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("slot", slot);
        result.addProperty("amount", amount);
        result.addProperty("material", stack.getType().getKey().toString());
        return result;
    }

    JsonObject injectProtected(JsonObject request) throws Exception {
        owner();
        Player player = Bukkit.getPlayerExact(request.get("player").getAsString());
        if (player == null) {
            throw new IllegalStateException("Native station owner absent");
        }
        String kind = request.get("kind").getAsString();
        if ("workbench".equals(kind)) {
            if (player.getOpenInventory().getType() != InventoryType.WORKBENCH
                    || !(player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting)
                    || crafting.getMatrix().length != 9) {
                throw new IllegalStateException("Workbench matrix is not open");
            }
            ItemStack[] matrix = crafting.getMatrix();
            matrix[4] = takeNavigation(player, Material.COMPASS);
            crafting.setMatrix(matrix);
        } else if ("cartography".equals(kind)) {
            if (player.getOpenInventory().getType() != InventoryType.CARTOGRAPHY) {
                throw new IllegalStateException("Cartography table is not open");
            }
            player.getOpenInventory().getTopInventory().setItem(0, takeNavigation(player, Material.FILLED_MAP));
        } else {
            throw new IllegalArgumentException("Unknown station injection");
        }
        JsonObject result = new JsonObject();
        result.addProperty("player", player.getName());
        result.addProperty("kind", kind);
        result.addProperty("cursorProtected", ItemGraphAssertions.tagged(player.getItemOnCursor()));
        return result;
    }

    private static ItemStack takeNavigation(Player player, Material material) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() == material && ItemGraphAssertions.tagged(cursor) && cursor.getAmount() == 1) {
            ItemStack taken = cursor.clone();
            player.setItemOnCursor(null);
            return taken;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() == material && ItemGraphAssertions.tagged(item) && item.getAmount() == 1) {
                inventory.setItem(slot, null);
                return item.clone();
            }
        }
        throw new IllegalStateException("Navigation item was not awaiting station injection");
    }

    JsonObject removeStation(JsonObject request) {
        owner();
        StationEntry entry = stations.remove(request.get("player").getAsString());
        if (entry == null) {
            throw new IllegalArgumentException("Unowned station fixture");
        }
        entry.block().setType(Material.AIR, false);
        JsonObject result = new JsonObject();
        result.addProperty("player", request.get("player").getAsString());
        result.addProperty("present", entry.block().getType() != Material.AIR);
        result.addProperty("ownedRemaining", stations.size());
        return result;
    }

    JsonObject prepareEntity(JsonObject request) throws Exception {
        owner();
        if (entityFixtures.size() >= 64) {
            throw new IllegalStateException("Owned entity fixture limit");
        }
        String name = request.get("player").getAsString();
        String kind = request.get("kind").getAsString();
        String mode = request.get("mode").getAsString();
        if (!Set.of("pickup", "hopper", "frame", "armor-stand", "allay").contains(kind)) {
            throw new IllegalArgumentException("Unknown entity fixture kind");
        }
        if (!Set.of("protected", "ordinary", "sanitize").contains(mode)) {
            throw new IllegalArgumentException("Unknown entity fixture mode");
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            throw new IllegalStateException("Native entity fixture owner absent");
        }
        if (entityFixtures.containsKey(name)) {
            throw new IllegalStateException("Owner already has an entity fixture");
        }
        World world = player.getWorld();
        int x = player.getLocation().getBlockX() + 2;
        int y = player.getLocation().getBlockY();
        int z = player.getLocation().getBlockZ();
        world.getChunkAt(x >> 4, z >> 4);
        Block support = null;
        Block hopper = null;
        Item dropped = null;
        Entity interact = null;
        ItemStack moving = switch (mode) {
            case "protected", "sanitize" -> navigationItem(player, "compass");
            case "ordinary" -> kind.equals("frame") || kind.equals("armor-stand")
                    ? one(playerStone(player))
                    : ordinary(Material.EMERALD, 1, "entity-emerald-" + name);
            default -> throw new IllegalArgumentException("Unknown entity fixture mode");
        };
        switch (kind) {
            case "pickup" -> dropped = delayedDrop(world, player.getLocation(), moving);
            case "hopper" -> {
                hopper = world.getBlockAt(x, y, z);
                if (!hopper.getType().isAir()) {
                    throw new IllegalStateException("Entity fixture hopper occupied");
                }
                hopper.setType(Material.HOPPER);
                dropped = delayedDrop(world, new Location(world, x + 0.5, y, z + 4.5), moving);
            }
            case "frame" -> {
                support = world.getBlockAt(x, y, z);
                Block space = world.getBlockAt(x - 1, y, z);
                if (!support.getType().isAir() || !space.getType().isAir()) {
                    throw new IllegalStateException("Entity fixture frame space occupied");
                }
                support.setType(Material.STONE);
                ItemFrame frame = world.spawn(space.getLocation(), ItemFrame.class, entity -> entity.setFacingDirection(BlockFace.WEST, true));
                if (frame == null) {
                    throw new IllegalStateException("Native item frame did not spawn");
                }
                if (mode.equals("sanitize")) {
                    frame.setItem(moving.clone(), false);
                }
                interact = frame;
            }
            case "armor-stand" -> {
                ArmorStand stand = world.spawn(new Location(world, x + 0.5, y, z + 0.5), ArmorStand.class, entity -> {
                    entity.setGravity(false);
                    entity.setArms(true);
                });
                if (stand == null) {
                    throw new IllegalStateException("Native armor stand did not spawn");
                }
                if (mode.equals("sanitize")) {
                    stand.getEquipment().setItem(EquipmentSlot.HAND, moving.clone());
                }
                interact = stand;
            }
            case "allay" -> {
                Allay allay = world.spawn(new Location(world, x + 0.5, y, z + 0.5), Allay.class, entity -> entity.setAI(false));
                if (allay == null) {
                    throw new IllegalStateException("Native allay did not spawn");
                }
                if (mode.equals("sanitize")) {
                    allay.getEquipment().setItemInMainHand(moving.clone());
                }
                interact = allay;
            }
            default -> throw new IllegalArgumentException("Unknown entity fixture kind");
        }
        entityFixtures.put(name, new EntityFixture(kind, interact, support, hopper, dropped));
        JsonObject result = new JsonObject();
        result.addProperty("player", name);
        result.addProperty("kind", kind);
        result.addProperty("mode", mode);
        Entity reported = interact != null ? interact : dropped;
        result.addProperty("entityId", reported == null ? -1 : reported.getEntityId());
        result.addProperty("x", hopper == null ? x : hopper.getX());
        result.addProperty("y", hopper == null ? y : hopper.getY());
        result.addProperty("z", hopper == null ? z : hopper.getZ());
        result.add("moving", lifecycle.classify(List.of(moving)));
        result.add("carrier", carrierContents(entityFixtures.get(name)));
        return result;
    }

    JsonObject enableEntity(JsonObject request) {
        owner();
        EntityFixture fixture = entityFixture(request);
        if (fixture.dropped() == null || !fixture.dropped().isValid()) {
            throw new IllegalStateException("Entity fixture has no delayed item");
        }
        if (fixture.hopper() != null) {
            fixture.dropped().teleport(fixture.hopper().getLocation().add(0.5, 1.05, 0.5));
            fixture.dropped().setVelocity(new Vector(0, 0, 0));
        }
        fixture.dropped().setPickupDelay(0);
        JsonObject result = new JsonObject();
        result.addProperty("player", request.get("player").getAsString());
        result.addProperty("pickupDelay", fixture.dropped().getPickupDelay());
        return result;
    }

    JsonObject snapshotEntity(JsonObject request) throws Exception {
        owner();
        EntityFixture fixture = entityFixture(request);
        JsonObject result = new JsonObject();
        result.addProperty("player", request.get("player").getAsString());
        result.addProperty("kind", fixture.kind());
        result.add("carrier", carrierContents(fixture));
        return result;
    }

    JsonObject removeEntity(JsonObject request) {
        owner();
        EntityFixture fixture = entityFixtures.remove(request.get("player").getAsString());
        if (fixture == null) {
            throw new IllegalArgumentException("Unowned entity fixture");
        }
        if (fixture.dropped() != null && fixture.dropped().isValid()) {
            fixture.dropped().remove();
        }
        if (fixture.interact() != null && fixture.interact().isValid()) {
            fixture.interact().remove();
        }
        if (fixture.support() != null) {
            fixture.support().setType(Material.AIR, false);
        }
        if (fixture.hopper() != null) {
            fixture.hopper().setType(Material.AIR, false);
        }
        JsonObject result = new JsonObject();
        result.addProperty("player", request.get("player").getAsString());
        result.addProperty("ownedRemaining", entityFixtures.size());
        return result;
    }

    JsonObject persistedSessions() {
        owner();
        NamespacedKey key = NamespacedKey.fromString("server_waypoint:navigation_session");
        if (key == null) {
            throw new IllegalStateException("Navigation session key invalid");
        }
        JsonObject result = new JsonObject();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String stored = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            result.addProperty(player.getName(), stored != null && !stored.isBlank());
        }
        return result;
    }

    private Item delayedDrop(World world, Location location, ItemStack moving) {
        Item dropped = world.dropItem(location, new ItemStack(Material.STONE));
        dropped.setItemStack(moving.clone());
        dropped.setPickupDelay(32767);
        dropped.setUnlimitedLifetime(true);
        return dropped;
    }

    private static ItemStack playerStone(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.STONE && !ItemGraphAssertions.tagged(item)) {
                return item;
            }
        }
        throw new IllegalStateException("Native owner stone absent");
    }

    private static ItemStack one(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return copy;
    }

    private EntityFixture entityFixture(JsonObject request) {
        EntityFixture fixture = entityFixtures.get(request.get("player").getAsString());
        if (fixture == null) {
            throw new IllegalArgumentException("Unowned entity fixture");
        }
        return fixture;
    }

    private JsonObject carrierContents(EntityFixture fixture) throws Exception {
        List<ItemStack> stacks = switch (fixture.kind()) {
            case "hopper" -> fixture.hopper() == null
                    ? List.of()
                    : Arrays.asList(((org.bukkit.block.Hopper) fixture.hopper().getState()).getInventory().getContents());
            case "pickup" -> fixture.dropped() != null && fixture.dropped().isValid()
                    ? List.of(fixture.dropped().getItemStack())
                    : List.of();
            case "frame" -> List.of(((ItemFrame) fixture.interact()).getItem());
            case "armor-stand" -> List.of(((ArmorStand) fixture.interact()).getEquipment().getItem(EquipmentSlot.HAND));
            case "allay" -> List.of(((Allay) fixture.interact()).getEquipment().getItemInMainHand());
            default -> throw new IllegalArgumentException("Unknown entity fixture kind");
        };
        return lifecycle.classify(stacks);
    }

    private static ItemStack plainFilledMap(Player player) {
        MapView view = Bukkit.createMap(player.getWorld());
        ItemStack map = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view);
        map.setItemMeta(meta);
        if (!meta.hasMapView() || view.isLocked() || view.getScale().getValue() >= 4) {
            throw new IllegalStateException("Plain cartography map cannot be scaled");
        }
        return map;
    }

    private ContainerEntry container(JsonObject request) {
        ContainerEntry entry = containers.get(request.get("player").getAsString());
        if (entry == null) {
            throw new IllegalArgumentException("Unowned other-container fixture");
        }
        return entry;
    }

    private static List<ItemStack> expectedContents(String kind) {
        return switch (kind) {
            case "direct", "ordinary" -> List.of(
                    ordinary(Material.DIAMOND, 3, "chest-diamonds"),
                    ordinary(Material.STONE, 16, "chest-stone"));
            case "nested" -> List.of(shulker(null));
            default -> List.of();
        };
    }

    private static ItemStack navigationItem(Player player, String method) {
        Material material = switch (method) {
            case "compass" -> Material.COMPASS;
            case "map" -> Material.FILLED_MAP;
            default -> throw new IllegalArgumentException("Item method required");
        };
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material && ItemGraphAssertions.tagged(item) && item.getAmount() == 1) {
                return item.clone();
            }
        }
        throw new IllegalStateException("Native owner navigation item absent");
    }

    private static ItemStack create(String kind, ItemStack navigation, boolean includeNavigation) {
        String shape = kind.startsWith("plain-") ? kind.substring(6) : kind;
        return switch (shape) {
            case "direct" -> navigation.clone();
            case "shulker" -> shulker(includeNavigation ? navigation.clone() : null);
            case "mixed" -> shulker(bundle(2, navigation, includeNavigation));
            case "bundle-1" -> bundle(1, navigation, includeNavigation);
            case "bundle-8" -> bundle(8, navigation, includeNavigation);
            case "bundle-9" -> bundle(9, navigation, includeNavigation);
            default -> throw new IllegalArgumentException("Unknown carrier shape");
        };
    }

    private static ItemStack bundle(int depth, ItemStack navigation, boolean includeNavigation) {
        if (depth == 0) {
            return includeNavigation ? navigation.clone() : null;
        }
        ItemStack item = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) item.getItemMeta();
        meta.displayName(Component.text("Fixture bundle layer " + depth));
        meta.lore(List.of(Component.text("Preserve ordinary bundle metadata")));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, "bundle-layer-" + depth);
        List<ItemStack> contents = new ArrayList<>();
        ItemStack nested = bundle(depth - 1, navigation, includeNavigation);
        if (nested != null) {
            contents.add(nested);
        }
        contents.add(ordinary(Material.DIAMOND, 1, "bundle-diamond-" + depth));
        meta.setItems(contents);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack shulker(ItemStack first) {
        ItemStack item = new ItemStack(Material.BLUE_SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        meta.displayName(Component.text("Fixture blue carrier"));
        meta.lore(List.of(Component.text("Preserve carrier metadata and ordinary children")));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, "blue-carrier");
        ShulkerBox box = (ShulkerBox) meta.getBlockState();
        box.customName(Component.text("Fixture container inventory"));
        if (first != null) {
            box.getSnapshotInventory().setItem(0, first);
        }
        box.getSnapshotInventory().setItem(1, ordinary(Material.DIAMOND, 3, "shulker-diamonds"));
        box.getSnapshotInventory().setItem(2, ordinary(Material.STONE, 64, "shulker-stone"));
        meta.setBlockState(box);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack ordinary(Material material, int amount, String marker) {
        ItemStack item = new ItemStack(material, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Ordinary " + marker));
        meta.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, marker);
        item.setItemMeta(meta);
        return item;
    }

    private static void owner() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Paper protection fixture requires server owner thread");
        }
    }

    private record Entry(Item entity, JsonObject before, JsonObject expected, byte[] beforeBytes, boolean roundTripValid, boolean productTagged) {
    }

    private record ContainerEntry(Block block, UUID owner, JsonObject expectedTop, JsonArray expectedGraphs) {
    }

    private record StationEntry(Block block, String kind) {
    }

    private record EntityFixture(String kind, Entity interact, Block support, Block hopper, Item dropped) {
    }
}
