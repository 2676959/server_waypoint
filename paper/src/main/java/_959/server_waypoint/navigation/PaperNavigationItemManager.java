package _959.server_waypoint.navigation;

import _959.server_waypoint.ModInfo;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PaperNavigationItemManager {
    private static final int OFFHAND_SLOT = 40;
    private static final int MAX_NESTING_DEPTH = 8;

    private final NamespacedKey navigationItemKey = new NamespacedKey(ModInfo.MOD_ID, "navigation_item");
    private final NamespacedKey ownerKey = new NamespacedKey(ModInfo.MOD_ID, "owner");
    private final NamespacedKey methodKey = new NamespacedKey(ModInfo.MOD_ID, "method");

    public ItemStack tag(ItemStack item, UUID owner, NavigationMethod method) {
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(this.navigationItemKey, PersistentDataType.BOOLEAN, true);
        data.set(this.ownerKey, PersistentDataType.STRING, owner.toString());
        data.set(this.methodKey, PersistentDataType.STRING, method.id());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isNavigationItem(@Nullable ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .getOrDefault(this.navigationItemKey, PersistentDataType.BOOLEAN, false);
    }

    public boolean containsNavigationItem(@Nullable ItemStack item) {
        return this.containsNavigationItem(item, 0);
    }

    public boolean isPortableStorage(@Nullable ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta) {
            return true;
        }
        if (meta instanceof BlockStateMeta blockStateMeta) {
            return blockStateMeta.getBlockState() instanceof ShulkerBox;
        }
        return false;
    }

    public Optional<UUID> owner(@Nullable ItemStack item) {
        if (!this.isNavigationItem(item)) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(this.ownerKey, PersistentDataType.STRING);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<NavigationMethod> method(@Nullable ItemStack item) {
        if (!this.isNavigationItem(item)) {
            return Optional.empty();
        }
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(this.methodKey, PersistentDataType.STRING);
        return NavigationMethod.fromId(value);
    }

    public boolean isMethod(@Nullable ItemStack item, NavigationMethod method) {
        return this.method(item).filter(method::equals).isPresent();
    }

    public boolean hasOwnedItem(Player player, NavigationMethod method) {
        return this.findOwnedLocations(player, method).size() > 0;
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
        List<ItemLocation> locations = this.findOwnedLocations(player, method);
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
        List<ItemLocation> locations = this.findOwnedLocations(player, method);
        if (locations.isEmpty()) {
            return false;
        }
        locations.get(0).set(player, desiredItem);
        for (int index = 1; index < locations.size(); index++) {
            locations.get(index).clear(player);
        }
        return true;
    }

    public void removeOwnedMethod(Player player, NavigationMethod method) {
        UUID playerUuid = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.isOwnedMethod(item, playerUuid, method)) {
                inventory.clear(slot);
            } else if (this.removeNestedNavigationItems(item)) {
                inventory.setItem(slot, item);
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (this.isOwnedMethod(cursor, playerUuid, method)) {
            player.setItemOnCursor(null);
        } else if (this.removeNestedNavigationItems(cursor)) {
            player.setItemOnCursor(cursor);
        }
        this.removeFromInvalidInventory(player.getOpenInventory().getTopInventory());
    }

    public ValidationResult validateDirectInventory(
            Player player,
            @Nullable NavigationSession session
    ) {
        UUID playerUuid = player.getUniqueId();
        Set<NavigationMethod> retained = EnumSet.noneOf(NavigationMethod.class);
        boolean changed = false;
        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (this.removeNestedNavigationItems(item)) {
                inventory.setItem(slot, item);
                changed = true;
            }
            if (!this.isNavigationItem(item)) {
                continue;
            }

            Optional<UUID> owner = this.owner(item);
            Optional<NavigationMethod> method = this.method(item);
            boolean allowedSlot = isAllowedDirectSlot(slot);
            boolean allowedSessionItem = session != null
                    && owner.filter(playerUuid::equals).isPresent()
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
        if (this.removeNestedNavigationItems(cursor)) {
            player.setItemOnCursor(cursor);
            changed = true;
        }
        if (this.isNavigationItem(cursor)) {
            Optional<UUID> owner = this.owner(cursor);
            Optional<NavigationMethod> method = this.method(cursor);
            boolean allowedCursorItem = session != null
                    && owner.filter(playerUuid::equals).isPresent()
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
            } else if (this.removeNestedNavigationItems(item)) {
                inventory.setItem(slot, item);
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
            } else if (this.removeNestedNavigationItems(item)) {
                inventory.setItem(slot, item);
            }
        }
        this.sanitizeCursor(player);
        this.removeFromInvalidInventory(player.getOpenInventory().getTopInventory());
    }

    public boolean removeNestedNavigationItems(@Nullable ItemStack item) {
        return this.removeNestedNavigationItems(item, 0);
    }

    public Set<UUID> navigationOwners(@Nullable ItemStack item) {
        Set<UUID> owners = new HashSet<>();
        this.collectNavigationOwners(item, 0, owners);
        return Set.copyOf(owners);
    }

    public boolean sanitizeCursor(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (this.isNavigationItem(cursor)) {
            player.setItemOnCursor(null);
            return true;
        }
        if (this.removeNestedNavigationItems(cursor)) {
            player.setItemOnCursor(cursor);
            return true;
        }
        return false;
    }

    private List<ItemLocation> findOwnedLocations(Player player, NavigationMethod method) {
        List<ItemLocation> locations = new ArrayList<>();
        UUID playerUuid = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (this.isOwnedMethod(storage[slot], playerUuid, method)) {
                locations.add(ItemLocation.storage(slot));
            }
        }
        if (this.isOwnedMethod(inventory.getItemInOffHand(), playerUuid, method)) {
            locations.add(ItemLocation.offhandLocation());
        }
        if (this.isOwnedMethod(player.getItemOnCursor(), playerUuid, method)) {
            locations.add(ItemLocation.cursorLocation());
        }
        return locations;
    }

    private boolean isOwnedMethod(
            @Nullable ItemStack item,
            UUID expectedOwner,
            NavigationMethod expectedMethod
    ) {
        return this.owner(item).filter(expectedOwner::equals).isPresent()
                && this.method(item).filter(expectedMethod::equals).isPresent();
    }

    private boolean containsNavigationItem(@Nullable ItemStack item, int depth) {
        if (isEmpty(item) || depth > MAX_NESTING_DEPTH) {
            return false;
        }
        if (this.isNavigationItem(item)) {
            return true;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            for (ItemStack bundledItem : bundleMeta.getItems()) {
                if (this.containsNavigationItem(bundledItem, depth + 1)) {
                    return true;
                }
            }
        }
        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof ShulkerBox shulkerBox) {
                for (ItemStack storedItem : shulkerBox.getSnapshotInventory().getContents()) {
                    if (this.containsNavigationItem(storedItem, depth + 1)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void collectNavigationOwners(
            @Nullable ItemStack item,
            int depth,
            Set<UUID> owners
    ) {
        if (isEmpty(item) || depth > MAX_NESTING_DEPTH) {
            return;
        }
        this.owner(item).ifPresent(owners::add);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            for (ItemStack bundledItem : bundleMeta.getItems()) {
                this.collectNavigationOwners(bundledItem, depth + 1, owners);
            }
        }
        if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof ShulkerBox shulkerBox) {
                for (ItemStack storedItem : shulkerBox.getSnapshotInventory().getContents()) {
                    this.collectNavigationOwners(storedItem, depth + 1, owners);
                }
            }
        }
    }

    private boolean removeNestedNavigationItems(@Nullable ItemStack item, int depth) {
        if (isEmpty(item) || depth > MAX_NESTING_DEPTH || !item.hasItemMeta()) {
            return false;
        }
        boolean changed = false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BundleMeta bundleMeta) {
            List<ItemStack> retainedItems = new ArrayList<>();
            for (ItemStack bundledItem : bundleMeta.getItems()) {
                if (this.isNavigationItem(bundledItem)) {
                    changed = true;
                    continue;
                }
                if (this.removeNestedNavigationItems(bundledItem, depth + 1)) {
                    changed = true;
                }
                retainedItems.add(bundledItem);
            }
            if (changed) {
                bundleMeta.setItems(retainedItems);
                item.setItemMeta(bundleMeta);
            }
        } else if (meta instanceof BlockStateMeta blockStateMeta) {
            BlockState state = blockStateMeta.getBlockState();
            if (state instanceof ShulkerBox shulkerBox) {
                Inventory nestedInventory = shulkerBox.getSnapshotInventory();
                for (int slot = 0; slot < nestedInventory.getSize(); slot++) {
                    ItemStack storedItem = nestedInventory.getItem(slot);
                    if (this.isNavigationItem(storedItem)) {
                        nestedInventory.clear(slot);
                        changed = true;
                    } else if (this.removeNestedNavigationItems(storedItem, depth + 1)) {
                        nestedInventory.setItem(slot, storedItem);
                        changed = true;
                    }
                }
                if (changed) {
                    blockStateMeta.setBlockState(shulkerBox);
                    item.setItemMeta(blockStateMeta);
                }
            }
        }
        return changed;
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
