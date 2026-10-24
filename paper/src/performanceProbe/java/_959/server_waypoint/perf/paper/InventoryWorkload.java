package _959.server_waypoint.perf.paper;

import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.block.ShulkerBox;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;

/** Bounded production event dispatch; counters never claim physical transfers. */
final class InventoryWorkload {
    private InventoryMoveItemEvent event;
    private int offered;
    private int completed;
    private int cancelled;
    private long duration;
    private long allocated;
    private final com.sun.management.ThreadMXBean allocations = ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean && bean.isThreadAllocatedMemorySupported() ? bean : null;

    void configure(JsonObject data) {
        String variant = data.has("variant") ? data.get("variant").getAsString() : "none";
        offered = data.has("eventsPerTick") ? data.get("eventsPerTick").getAsInt() : 0;
        if (offered < 0 || offered > 1000) {
            throw new IllegalArgumentException("Synthetic events/tick outside [0,1000]");
        }
        if (allocations != null && !allocations.isThreadAllocatedMemoryEnabled()) {
            allocations.setThreadAllocatedMemoryEnabled(true);
        }
        ItemStack item;
        if (variant.startsWith("plain")) {
            item = new ItemStack(Material.STONE, 64);
        } else if (variant.startsWith("meta")) {
            item = new ItemStack(Material.DIAMOND, 16);
            ItemMeta meta = item.getItemMeta();
            meta.customName(net.kyori.adventure.text.Component.text("Perf diamond"));
            item.setItemMeta(meta);
        } else if (variant.startsWith("bundle")) {
            item = new ItemStack(Material.BUNDLE);
            if (variant.contains("full")) {
                BundleMeta meta = (BundleMeta) item.getItemMeta();
                meta.setItems(List.of(new ItemStack(Material.DIAMOND, 64)));
                item.setItemMeta(meta);
            }
        } else if (variant.startsWith("direct-tag")) {
            item = tag(new ItemStack(Material.COMPASS));
        } else if (variant.startsWith("nested-tag")) {
            item = shulker(tag(new ItemStack(Material.COMPASS)), 1);
        } else if (variant.startsWith("nested") || variant.startsWith("nesting-boundary")) {
            item = new ItemStack(Material.STONE);
            int depth = variant.startsWith("nesting-boundary") ? 9 : 3;
            for (int i = 0; i < depth; i++) {
                item = shulker(item, 1);
            }
        } else if (variant.contains("empty")) {
            item = new ItemStack(Material.SHULKER_BOX);
        } else if (variant.contains("full")) {
            item = shulker(new ItemStack(Material.STONE, 64), 27);
        } else {
            ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
            EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
            meta.addStoredEnchant(Enchantment.MENDING, 1, true);
            book.setItemMeta(meta);
            item = shulker(book, 27);
        }
        event = new InventoryMoveItemEvent(Bukkit.createInventory(null, 27), CraftItemStack.asCraftCopy(item), Bukkit.createInventory(null, 27), true);
    }

    private ItemStack tag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("server_waypoint", "navigation_item"), PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack shulker(ItemStack content, int slots) {
        ItemStack item = new ItemStack(Material.SHULKER_BOX);
        BlockStateMeta meta = (BlockStateMeta) item.getItemMeta();
        ShulkerBox state = (ShulkerBox) meta.getBlockState();
        for (int i = 0; i < slots; i++) {
            state.getInventory().setItem(i, content.clone());
        }
        meta.setBlockState(state);
        item.setItemMeta(meta);
        return item;
    }

    void tick() {
        completed = 0;
        cancelled = 0;
        long thread = Thread.currentThread().getId();
        long before = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread);
        long started = System.nanoTime();
        for (int i = 0; i < offered; i++) {
            event.setCancelled(false);
            Bukkit.getPluginManager().callEvent(event);
            completed++;
            if (event.isCancelled()) {
                cancelled++;
            }
        }
        duration = System.nanoTime() - started;
        allocated = allocations == null ? -1 : allocations.getThreadAllocatedBytes(thread) - before;
    }

    void addSample(JsonObject sample) {
        sample.addProperty("workloadDurationNs", duration);
        sample.addProperty("allocatedBytes", allocated);
        sample.addProperty("allocationScope", allocations == null ? "unsupported" : "server-thread");
        sample.addProperty("allocationThread", Thread.currentThread().getName());
        sample.addProperty("syntheticEvents", completed);
        sample.addProperty("cancelledSyntheticEvents", cancelled);
        sample.addProperty("offeredSyntheticEvents", offered);
    }

    static int navigationSessions() throws ReflectiveOperationException {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ServerWaypoint");
        if (plugin == null) {
            return 0;
        }
        var serviceField = plugin.getClass().getDeclaredField("navigationService");
        serviceField.setAccessible(true);
        Object service = serviceField.get(plugin);
        var sessionsField = service.getClass().getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        return ((Map<?, ?>) sessionsField.get(service)).size();
    }
}
