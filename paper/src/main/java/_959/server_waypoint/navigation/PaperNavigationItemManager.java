package _959.server_waypoint.navigation;

import _959.server_waypoint.ModInfo;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class PaperNavigationItemManager {
    private static final int OFFHAND_SLOT = 40;

    private final NamespacedKey navigationItemKey = new NamespacedKey(ModInfo.MOD_ID, "navigation_item");
    private final String navigationItemTag = this.navigationItemKey.toString();

    public ItemStack tag(ItemStack item) {
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(this.navigationItemKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isNavigationItem(@Nullable ItemStack item) {
        return !isEmpty(item) && PaperNavigationItemReader.isNavigationItem(
                CraftItemStack.unwrap(item), this.navigationItemTag);
    }

    public boolean isPortableStorage(@Nullable ItemStack item) {
        return !isEmpty(item) && PaperNavigationItemReader.isPortableStorage(CraftItemStack.unwrap(item));
    }

    public Optional<NavigationMethod> method(@Nullable ItemStack item) {
        if (!this.isNavigationItem(item)) {
            return Optional.empty();
        }
        return switch (item.getType()) {
            case COMPASS -> Optional.of(NavigationMethod.COMPASS);
            case FILLED_MAP -> Optional.of(NavigationMethod.MAP);
            default -> Optional.empty();
        };
    }

    public boolean isMethod(@Nullable ItemStack item, NavigationMethod method) {
        return this.method(item).filter(method::equals).isPresent();
    }

    public boolean hasItem(Player player, NavigationMethod method) {
        return !this.findLocations(player, method).isEmpty();
    }

    public int emptyStorageSlots(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isEmpty(item)) {
                count++;
            }
        }
        return count;
    }

    public boolean install(Player player, NavigationMethod method, ItemStack desiredItem) {
        List<ItemLocation> locations = this.findLocations(player, method);
        if (!locations.isEmpty()) {
            ItemLocation retained = locations.get(0);
            retained.set(player, desiredItem);
            for (int index = 1; index < locations.size(); index++) {
                locations.get(index).clear(player);
            }
            return true;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (isEmpty(storage[slot])) {
                inventory.setItem(slot, desiredItem);
                return true;
            }
        }
        return false;
    }

    public boolean updateExisting(Player player, NavigationMethod method, ItemStack desiredItem) {
        List<ItemLocation> locations = this.findLocations(player, method);
        if (locations.isEmpty()) {
            return false;
        }
        locations.get(0).set(player, desiredItem);
        for (int index = 1; index < locations.size(); index++) {
            locations.get(index).clear(player);
        }
        return true;
    }

    public void removeMethodItems(Player player, NavigationMethod method) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.isMethod(item, method)) {
                inventory.clear(slot);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (this.isMethod(cursor, method)) {
            player.setItemOnCursor(null);
        }
        this.removeFromInvalidInventory(player.getOpenInventory().getTopInventory());
    }

    public ValidationResult validateDirectInventory(
            Player player,
            @Nullable NavigationSession session
    ) {
        Set<NavigationMethod> retained = EnumSet.noneOf(NavigationMethod.class);
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (!this.isNavigationItem(item)) {
                continue;
            }

            Optional<NavigationMethod> method = this.method(item);
            boolean allowedSlot = isAllowedDirectSlot(slot);
            boolean allowedSessionItem = session != null
                    && method.filter(NavigationMethod::ownsItem).isPresent()
                    && method.filter(session::isEnabled).isPresent()
                    && allowedSlot
                    && retained.add(method.orElseThrow());
            if (!allowedSessionItem) {
                inventory.clear(slot);
                changed = true;
                continue;
            }
            if (item.getAmount() != 1) {
                item.setAmount(1);
                inventory.setItem(slot, item);
                changed = true;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (this.isNavigationItem(cursor)) {
            Optional<NavigationMethod> method = this.method(cursor);
            boolean allowedCursorItem = session != null
                    && method.filter(NavigationMethod::ownsItem).isPresent()
                    && method.filter(session::isEnabled).isPresent()
                    && retained.add(method.orElseThrow());
            if (!allowedCursorItem) {
                player.setItemOnCursor(null);
                changed = true;
            } else if (cursor.getAmount() != 1) {
                cursor.setAmount(1);
                player.setItemOnCursor(cursor);
                changed = true;
            }
        }

        Set<NavigationMethod> missing = EnumSet.noneOf(NavigationMethod.class);
        if (session != null) {
            for (NavigationMethod method : session.enabledMethods()) {
                if (method.ownsItem() && !retained.contains(method)) {
                    missing.add(method);
                }
            }
        }
        return new ValidationResult(changed, Set.copyOf(missing));
    }

    public boolean removeFromInvalidInventory(Inventory inventory) {
        boolean changed = false;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.isNavigationItem(item)) {
                inventory.clear(slot);
                changed = true;
            }
        }
        return changed;
    }

    public void purgeAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.isNavigationItem(item)) {
                inventory.clear(slot);
            }
        }
        this.sanitizeCursor(player);
        this.removeFromInvalidInventory(player.getOpenInventory().getTopInventory());
    }

    public boolean sanitizeCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (this.isNavigationItem(cursor)) {
            player.setItemOnCursor(null);
            return true;
        }
        return false;
    }

    private List<ItemLocation> findLocations(Player player, NavigationMethod method) {
        List<ItemLocation> locations = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (this.isMethod(storage[slot], method)) {
                locations.add(ItemLocation.storage(slot));
            }
        }
        if (this.isMethod(inventory.getItemInOffHand(), method)) {
            locations.add(ItemLocation.offhandLocation());
        }
        if (this.isMethod(player.getItemOnCursor(), method)) {
            locations.add(ItemLocation.cursorLocation());
        }
        return locations;
    }

    private static boolean isAllowedDirectSlot(int slot) {
        return (slot >= 0 && slot < OFFHAND_SLOT - 4) || slot == OFFHAND_SLOT;
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    public record ValidationResult(boolean changed, Set<NavigationMethod> missingMethods) {
    }

    private record ItemLocation(int storageSlot, boolean offhand, boolean cursor) {
        private static ItemLocation storage(int slot) {
            return new ItemLocation(slot, false, false);
        }

        private static ItemLocation offhandLocation() {
            return new ItemLocation(-1, true, false);
        }

        private static ItemLocation cursorLocation() {
            return new ItemLocation(-1, false, true);
        }

        private void set(Player player, ItemStack item) {
            PlayerInventory inventory = player.getInventory();
            if (this.offhand) {
                inventory.setItemInOffHand(item);
            } else if (this.cursor) {
                player.setItemOnCursor(item);
            } else {
                inventory.setItem(this.storageSlot, item);
            }
        }

        private void clear(Player player) {
            PlayerInventory inventory = player.getInventory();
            if (this.offhand) {
                inventory.setItemInOffHand(null);
            } else if (this.cursor) {
                player.setItemOnCursor(null);
            } else {
                inventory.clear(this.storageSlot);
            }
        }
    }
}
