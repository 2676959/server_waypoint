package _959.server_waypoint.perf.paper;

import com.google.gson.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.Directional;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.structure.Structure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

final class PaperFixtureAdapter {
    private final World world = Bukkit.getWorlds().get(0);
    private final List<Block> hoppers = new ArrayList<>();
    private JsonObject manifest;
    private Path directory;
    private boolean placedThisProcess;
    private int[] previous;
    private long physicalChanges;

    static String hash(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    void load(Path path) throws Exception {
        directory = path.toRealPath();
        manifest = JsonParser.parseString(Files.readString(directory.resolve("manifest.json"))).getAsJsonObject();
        if (manifest.get("schemaVersion").getAsInt() != 1 || !manifest.get("targetRuntime").getAsString().equals("paper-1.21.11")) {
            throw new IllegalArgumentException("Wrong fixture runtime/schema");
        }
        if (Bukkit.getUnsafe().getDataVersion() != manifest.get("dataVersion").getAsInt()) {
            throw new IllegalArgumentException("Wrong fixture DataVersion");
        }
        for (var entry : manifest.getAsJsonObject("templates").entrySet()) {
            Path file = directory.resolve(entry.getKey()).normalize();
            if (!file.getParent().equals(directory) || !hash(file).equals(entry.getValue().getAsString())) {
                throw new IllegalArgumentException("Template identity mismatch");
            }
        }
        hoppers.clear();
        for (JsonElement value : manifest.getAsJsonArray("placements")) {
            JsonObject placement = value.getAsJsonObject();
            for (JsonElement state : manifest.getAsJsonArray("expectedState")) {
                Block block = block(placement, state.getAsJsonObject());
                world.setChunkForceLoaded(block.getX() >> 4, block.getZ() >> 4, true);
                if (state.getAsJsonObject().get("material").getAsString().equals("HOPPER")) {
                    hoppers.add(block);
                }
            }
        }
        previous = null;
        physicalChanges = 0;
    }

    void place(Path path) throws Exception {
        load(path);
        if (Files.exists(directory.resolve("placed.json"))) {
            throw new IllegalStateException("Refusing to overwrite a placed fixture");
        }
        Structure structure = Bukkit.getStructureManager().loadStructure(directory.resolve("cell.nbt").toFile());
        for (JsonElement value : manifest.getAsJsonArray("placements")) {
            JsonObject p = value.getAsJsonObject();
            structure.place(new Location(world, p.get("x").getAsInt(), p.get("y").getAsInt(), p.get("z").getAsInt()), false, org.bukkit.block.structure.StructureRotation.NONE, org.bukkit.block.structure.Mirror.NONE, 0, 1, new Random(95920261024L));
        }
        placedThisProcess = true;
        Files.writeString(directory.resolve("placed.json"), "{\"recipeSha256\":\"" + manifest.get("recipeSha256").getAsString() + "\"}");
        quiesce();
    }

    private Block block(JsonObject placement, JsonObject state) {
        var offset = state.getAsJsonArray("offset");
        return world.getBlockAt(placement.get("x").getAsInt() + offset.get(0).getAsInt(), placement.get("y").getAsInt() + offset.get(1).getAsInt(), placement.get("z").getAsInt() + offset.get(2).getAsInt());
    }

    JsonObject verify() throws Exception {
        if (manifest == null) {
            throw new IllegalStateException("No fixture loaded");
        }
        for (JsonElement value : manifest.getAsJsonArray("placements")) {
            for (JsonElement expected : manifest.getAsJsonArray("expectedState")) {
                JsonObject state = expected.getAsJsonObject();
                Block block = block(value.getAsJsonObject(), state);
                if (!block.getType().name().equals(state.get("material").getAsString())) {
                    throw new IllegalStateException("Material mismatch at " + block.getLocation());
                }
                if (state.has("facing") && !((Directional) block.getBlockData()).getFacing().name().equals(state.get("facing").getAsString())) {
                    throw new IllegalStateException("Facing mismatch");
                }
                if (block.getState() instanceof Container container) {
                    List<ItemStack> actual = Arrays.stream(container.getInventory().getContents()).filter(Objects::nonNull).filter(item -> !item.getType().isAir()).toList();
                    JsonArray items = state.getAsJsonArray("items");
                    if (actual.size() != items.size()) {
                        throw new IllegalStateException("Inventory slots mismatch");
                    }
                    for (int i = 0; i < actual.size(); i++) {
                        verifyItem(actual.get(i), items.get(i).getAsJsonObject());
                    }
                }
            }
        }
        JsonObject result = snapshot();
        result.addProperty("verified", true);
        result.addProperty("reopened", !placedThisProcess && Files.isRegularFile(directory.resolve("placed.json")));
        result.addProperty("conserved", true);
        result.addProperty("recipeSha256", manifest.get("recipeSha256").getAsString());
        return result;
    }

    private static void verifyItem(ItemStack actual, JsonObject expected) {
        if (!actual.getType().getKey().toString().equals(expected.get("id").getAsString()) || actual.getAmount() != expected.get("count").getAsInt()) {
            throw new IllegalStateException("Item identity/count mismatch: " + actual);
        }
        if (!expected.has("components")) {
            return;
        }
        var components = expected.getAsJsonObject("components");
        if (components.has("minecraft:stored_enchantments")) {
            if (!(actual.getItemMeta() instanceof EnchantmentStorageMeta meta) || meta.getStoredEnchantLevel(Enchantment.MENDING) != 1) {
                throw new IllegalStateException("Nested enchantment lost");
            }
        }
        if (components.has("minecraft:custom_name") && !actual.getItemMeta().hasCustomName()) {
            throw new IllegalStateException("Custom name lost");
        }
        if (components.has("minecraft:container")) {
            if (!(actual.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof ShulkerBox shulker)) {
                throw new IllegalStateException("Shulker metadata lost");
            }
            JsonArray contents = components.getAsJsonArray("minecraft:container");
            for (JsonElement value : contents) {
                JsonObject entry = value.getAsJsonObject();
                verifyItem(shulker.getInventory().getItem(entry.get("slot").getAsInt()), entry.getAsJsonObject("item"));
            }
        }
        if (components.has("minecraft:bundle_contents")) {
            if (!(actual.getItemMeta() instanceof BundleMeta meta)) {
                throw new IllegalStateException("Bundle metadata lost");
            }
            JsonArray contents = components.getAsJsonArray("minecraft:bundle_contents");
            if (meta.getItems().size() != contents.size()) {
                throw new IllegalStateException("Bundle count mismatch");
            }
            for (int i = 0; i < contents.size(); i++) {
                verifyItem(meta.getItems().get(i), contents.get(i).getAsJsonObject());
            }
        }
    }

    void activate() {
        for (Block block : hoppers) {
            block.getRelative(0, -1, 0).setType(Material.AIR, true);
        }
        previous = null;
        physicalChanges = 0;
    }

    void quiesce() {
        for (Block block : hoppers) {
            block.getRelative(0, -1, 0).setType(Material.REDSTONE_BLOCK, true);
        }
    }

    void observePhysicalChanges() {
        int[] current = new int[hoppers.size()];
        for (int i = 0; i < hoppers.size(); i++) {
            Container container = (Container) hoppers.get(i).getState(false);
            for (ItemStack item : container.getInventory().getContents()) {
                if (item != null) {
                    current[i] += item.getAmount();
                }
            }
            if (previous != null && current[i] > previous[i]) {
                physicalChanges += current[i] - previous[i];
            }
        }
        previous = current;
    }

    JsonObject snapshot() {
        JsonObject result = new JsonObject();
        result.addProperty("onlinePlayers", Bukkit.getOnlinePlayers().size());
        result.addProperty("physicalTransfersObserved", physicalChanges);
        result.addProperty("physicalCounterUnit", "observed-inventory-increases");
        result.addProperty("forceLoadedHoppers", hoppers.stream().filter(b -> world.isChunkForceLoaded(b.getX() >> 4, b.getZ() >> 4)).count());
        result.addProperty("expectedHoppers", hoppers.size());
        long items = 0;
        for (Block hopper : hoppers) {
            for (ItemStack item : ((Container) hopper.getState(false)).getInventory().getContents()) {
                if (item != null) {
                    items += item.getAmount();
                }
            }
        }
        result.addProperty("topLevelItemCount", items);
        return result;
    }
}
