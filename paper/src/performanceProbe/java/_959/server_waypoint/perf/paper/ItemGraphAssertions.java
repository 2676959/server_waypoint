package _959.server_waypoint.perf.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.persistence.PersistentDataType;

import java.security.MessageDigest;
import java.util.HexFormat;

/** Independent read-only PDC/metadata graph; never calls a release detector/sanitizer. */
final class ItemGraphAssertions {
    static final String ORIGIN = "independent-bukkit-item-graph";
    private static final NamespacedKey TAG = new NamespacedKey("server_waypoint", "navigation_item");
    private static final NamespacedKey MARKER = new NamespacedKey("swperf", "fixture_marker");
    private int nodes;

    static JsonObject inspect(ItemStack stack) throws Exception {
        var reader = new ItemGraphAssertions();
        Node root = reader.visit(stack, "$", 0);
        JsonObject result = new JsonObject();
        result.addProperty("origin", ORIGIN);
        result.addProperty("complete", true);
        result.addProperty("nodes", reader.nodes);
        result.addProperty("protectedUnits", root.protectedUnits());
        result.addProperty("ordinaryLeafUnits", root.ordinaryUnits());
        result.addProperty("sha256", hash(stack));
        result.add("root", root.value());
        return result;
    }

    static boolean tagged(ItemStack stack) {
        if (empty(stack)) {
            return false;
        }
        var meta = stack.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().getOrDefault(TAG, PersistentDataType.BOOLEAN, false);
    }

    static String hash(ItemStack stack) throws Exception {
        byte[] bytes = empty(stack) ? new byte[0] : stack.serializeAsBytes();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private Node visit(ItemStack stack, String path, int depth) {
        if (depth > 64 || ++nodes > 4096) {
            // Diagnostic limits must block the observation, never label an
            // unvisited descendant as clean. These exceed the release's limit.
            throw new IllegalStateException("Independent item-graph observation limit");
        }
        JsonObject value = new JsonObject();
        value.addProperty("path", path);
        JsonArray children = new JsonArray();
        value.add("children", children);
        if (empty(stack)) {
            value.addProperty("material", "minecraft:air");
            value.addProperty("amount", 0);
            value.addProperty("tagged", false);
            return new Node(value, 0, 0);
        }
        int amount = stack.getAmount();
        boolean direct = tagged(stack);
        var meta = stack.getItemMeta();
        value.addProperty("material", stack.getType().getKey().toString());
        value.addProperty("amount", amount);
        value.addProperty("tagged", direct);
        if (meta != null) {
            value.addProperty("displayName", meta.displayName() == null ? "" : PlainTextComponentSerializer.plainText().serialize(meta.displayName()));
            String marker = meta.getPersistentDataContainer().get(MARKER, PersistentDataType.STRING);
            if (marker != null) {
                value.addProperty("fixtureMarker", marker);
            }
        }
        boolean carrier = false;
        long protectedChildren = 0;
        long ordinaryChildren = 0;
        if (meta instanceof BundleMeta bundle) {
            carrier = true;
            int slot = 0;
            for (ItemStack child : bundle.getItems()) {
                Node nested = visit(child, path + ".bundle[" + slot++ + "]", depth + 1);
                children.add(nested.value());
                protectedChildren = Math.addExact(protectedChildren, nested.protectedUnits());
                ordinaryChildren = Math.addExact(ordinaryChildren, nested.ordinaryUnits());
            }
        } else if (meta instanceof BlockStateMeta block && block.getBlockState() instanceof Container container) {
            carrier = true;
            ItemStack[] contents = container.getSnapshotInventory().getContents();
            for (int slot = 0; slot < contents.length; slot++) {
                if (empty(contents[slot])) {
                    continue;
                }
                Node nested = visit(contents[slot], path + ".container[" + slot + "]", depth + 1);
                children.add(nested.value());
                protectedChildren = Math.addExact(protectedChildren, nested.protectedUnits());
                ordinaryChildren = Math.addExact(ordinaryChildren, nested.ordinaryUnits());
            }
        }
        long protectedUnits = Math.addExact(direct ? amount : 0, Math.multiplyExact(amount, protectedChildren));
        long ordinaryUnits = carrier ? Math.multiplyExact(amount, ordinaryChildren) : direct ? 0 : amount;
        return new Node(value, protectedUnits, ordinaryUnits);
    }

    private static boolean empty(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    private record Node(JsonObject value, long protectedUnits, long ordinaryUnits) {
    }
}
