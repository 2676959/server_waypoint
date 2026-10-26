package _959.server_waypoint.perf.paper;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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
    private final LifecycleAssertions lifecycle;
    private final Map<UUID, Entry> owned = new HashMap<>();

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
        boolean productContains = (Boolean) manager.getClass().getMethod("containsNavigationItem", ItemStack.class).invoke(manager, actual);
        var location = player.getLocation().add(1.5, 0.5, 1.5);
        location.getWorld().getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Item entity = location.getWorld().dropItem(location, actual.clone());
        entity.setPickupDelay(32767);
        UUID id = entity.getUniqueId();
        owned.put(id, new Entry(entity, before, expectedAfter, beforeBytes, valid, productContains));
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
        result.addProperty("installedReleaseContainsBefore", entry.productContains());
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

    private record Entry(Item entity, JsonObject before, JsonObject expected, byte[] beforeBytes, boolean roundTripValid, boolean productContains) {
    }
}
