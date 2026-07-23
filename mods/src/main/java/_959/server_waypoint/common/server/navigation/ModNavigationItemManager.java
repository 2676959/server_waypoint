package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

//? if >= 1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
*///?}

/** Server-thread-only direct-inventory operations for session-owned items. */
public final class ModNavigationItemManager {
    private static final int FIRST_MAIN_SLOT = 0;
    private static final int LAST_MAIN_SLOT = 35;

    public NavigationResult preflight(
            ServerPlayer player,
            @Nullable NavigationSession currentSession,
            NavigationSession proposedSession
    ) {
        Set<NavigationMethod> found = EnumSet.noneOf(NavigationMethod.class);
        Inventory inventory = player.getInventory();
        int availableSlots = 0;

        for (int slot = FIRST_MAIN_SLOT; slot <= LAST_MAIN_SLOT; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                availableSlots++;
            } else if (ModNavigationItemData.isNavigationItem(stack)) {
                ModNavigationItemData.method(stack).ifPresent(found::add);
            }
        }
        ItemStack offhand = inventory.getItem(Inventory.SLOT_OFFHAND);
        if (ModNavigationItemData.isNavigationItem(offhand)) {
            ModNavigationItemData.method(offhand).ifPresent(found::add);
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (ModNavigationItemData.isNavigationItem(carried)) {
            ModNavigationItemData.method(carried).ifPresent(found::add);
        }

        int requiredSlots = 0;
        for (NavigationMethod method : proposedSession.enabledMethods()) {
            if (method.ownsItem() && !found.contains(method)) {
                requiredSlots++;
            }
        }
        return requiredSlots <= availableSlots
                ? NavigationResult.success()
                : NavigationResult.insufficientInventory(requiredSlots, availableSlots);
    }

    public NavigationResult updateOrInsert(
            ServerPlayer player,
            NavigationMethod method,
            ItemStack configuredStack
    ) {
        Inventory inventory = player.getInventory();
        int existingSlot = findDirectItemSlot(player, method);
        ItemStack carried = player.containerMenu.getCarried();
        ModNavigationItemData.tag(configuredStack);
        if (existingSlot >= 0) {
            ItemStack existing = inventory.getItem(existingSlot);
            if (!ItemStack.matches(existing, configuredStack)) {
                inventory.setItem(existingSlot, configuredStack);
            } else if (existing.getCount() != 1) {
                existing.setCount(1);
            }
            removeDuplicates(player, method, existingSlot);
            inventory.setChanged();
            return NavigationResult.success();
        }
        if (isMethod(carried, method)) {
            player.containerMenu.setCarried(configuredStack);
            removeDuplicates(player, method, -1);
            inventory.setChanged();
            return NavigationResult.success();
        }

        for (int slot = FIRST_MAIN_SLOT; slot <= LAST_MAIN_SLOT; slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, configuredStack);
                removeDuplicates(player, method, slot);
                inventory.setChanged();
                return NavigationResult.success();
            }
        }
        return NavigationResult.insufficientInventory(1, 0);
    }

    public boolean hasItem(ServerPlayer player, NavigationMethod method) {
        return findDirectItemSlot(player, method) >= 0
                || isMethod(player.containerMenu.getCarried(), method);
    }

    public void removeMethodItems(ServerPlayer player, NavigationMethod method) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isMethod(stack, method)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (isMethod(carried, method)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        inventory.setChanged();
    }

    public void removeAllNavigationItems(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ModNavigationItemData.isNavigationItem(stack)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            } else {
                cleanNestedNavigationItems(stack);
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (ModNavigationItemData.isNavigationItem(carried)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        } else {
            cleanNestedNavigationItems(carried);
        }
        inventory.setChanged();
    }

    InventoryState captureState(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> contents = new ArrayList<>(inventory.getContainerSize());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            contents.add(inventory.getItem(slot).copy());
        }
        return new InventoryState(contents, player.containerMenu.getCarried().copy());
    }

    void restoreState(ServerPlayer player, InventoryState state) {
        Inventory inventory = player.getInventory();
        if (inventory.getContainerSize() != state.contents().size()) {
            throw new IllegalStateException("Player inventory size changed during navigation item restoration");
        }
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, state.contents().get(slot).copy());
        }
        player.containerMenu.setCarried(state.carried().copy());
        inventory.setChanged();
    }

    /**
     * Removes foreign, malformed, duplicated, or equipment-slot navigation
     * items and normalizes every retained stack to a single item.
     */
    public boolean validateDirectInventory(
            ServerPlayer player,
            Set<NavigationMethod> enabledMethods
    ) {
        Inventory inventory = player.getInventory();
        Set<NavigationMethod> found = EnumSet.noneOf(NavigationMethod.class);
        boolean changed = false;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            changed |= cleanNestedNavigationItems(stack);
            if (!ModNavigationItemData.isNavigationItem(stack)) {
                continue;
            }
            NavigationMethod method = ModNavigationItemData.method(stack).orElse(null);
            boolean allowedSlot = slot <= LAST_MAIN_SLOT || slot == Inventory.SLOT_OFFHAND;
            if (!allowedSlot
                    || method == null
                    || !method.ownsItem()
                    || !enabledMethods.contains(method)
                    || !found.add(method)) {
                inventory.setItem(slot, ItemStack.EMPTY);
                changed = true;
                continue;
            }
            if (stack.getCount() != 1) {
                stack.setCount(1);
                changed = true;
            }
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (ModNavigationItemData.isNavigationItem(carried)) {
            NavigationMethod carriedMethod = ModNavigationItemData.method(carried).orElse(null);
            if (carriedMethod == null
                    || !enabledMethods.contains(carriedMethod)
                    || found.contains(carriedMethod)) {
                player.containerMenu.setCarried(ItemStack.EMPTY);
                changed = true;
            } else if (carried.getCount() != 1) {
                carried.setCount(1);
                changed = true;
            }
        }

        if (changed) {
            inventory.setChanged();
        }
        return changed;
    }

    private int findDirectItemSlot(ServerPlayer player, NavigationMethod method) {
        Inventory inventory = player.getInventory();
        for (int slot = FIRST_MAIN_SLOT; slot <= LAST_MAIN_SLOT; slot++) {
            if (isMethod(inventory.getItem(slot), method)) {
                return slot;
            }
        }
        return isMethod(inventory.getItem(Inventory.SLOT_OFFHAND), method)
                ? Inventory.SLOT_OFFHAND
                : -1;
    }

    private void removeDuplicates(ServerPlayer player, NavigationMethod method, int retainedSlot) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot != retainedSlot && isMethod(inventory.getItem(slot), method)) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (retainedSlot >= 0
                && isMethod(player.containerMenu.getCarried(), method)) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

    record InventoryState(List<ItemStack> contents, ItemStack carried) {
    }

    private static boolean isMethod(ItemStack stack, NavigationMethod method) {
        return ModNavigationItemData.method(stack).filter(method::equals).isPresent();
    }

    static boolean cleanNestedNavigationItems(ItemStack containerStack) {
        if (containerStack.isEmpty()) {
            return false;
        }
        //? if >= 1.20.5 {
        boolean changed = cleanBundleContents(containerStack);
        changed |= cleanContainerContents(containerStack);
        return changed;
        //?} else {
        /*CompoundTag tag = containerStack.getTag();
        if (tag == null) {
            return false;
        }
        boolean changed = false;
        if (containerStack.getItem() instanceof BundleItem) {
            changed |= cleanSerializedItemList(tag, "Items");
        }
        if (containerStack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock
                && tag.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
            changed |= cleanSerializedItemList(tag.getCompound("BlockEntityTag"), "Items");
        }
        return changed;
        *///?}
    }

    //? if >= 1.20.5 {
    private static boolean cleanBundleContents(ItemStack bundle) {
        if (!(bundle.getItem() instanceof BundleItem)) {
            return false;
        }
        BundleContents contents = bundle.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        java.util.List<ItemStack> items = contents.itemCopyStream().toList();
        BundleContents.Mutable cleaned = new BundleContents.Mutable(BundleContents.EMPTY);
        boolean changed = false;
        for (ItemStack item : items) {
            if (ModNavigationItemData.isNavigationItem(item)) {
                changed = true;
                continue;
            }
            changed |= cleanNestedNavigationItems(item);
            cleaned.tryInsert(item);
        }
        if (changed) {
            bundle.set(DataComponents.BUNDLE_CONTENTS, cleaned.toImmutable());
        }
        return changed;
    }

    private static boolean cleanContainerContents(ItemStack container) {
        ItemContainerContents contents = container.getOrDefault(
                DataComponents.CONTAINER,
                ItemContainerContents.EMPTY
        );
        //? if >= 26 {
        java.util.List<ItemStack> items = new java.util.ArrayList<>(contents.allItemsCopyStream().toList());
        //?} else {
        /*java.util.List<ItemStack> items = new java.util.ArrayList<>(contents.stream().toList());
        *///?}
        boolean changed = false;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack item = items.get(slot);
            if (ModNavigationItemData.isNavigationItem(item)) {
                items.set(slot, ItemStack.EMPTY);
                changed = true;
            } else {
                changed |= cleanNestedNavigationItems(item);
            }
        }
        if (changed) {
            container.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
        return changed;
    }
    //?} else {
    /*private static boolean cleanSerializedItemList(CompoundTag parent, String key) {
        if (!parent.contains(key, Tag.TAG_LIST)) {
            return false;
        }
        ListTag items = parent.getList(key, Tag.TAG_COMPOUND);
        ListTag cleaned = new ListTag();
        boolean changed = false;
        for (Tag serialized : items) {
            CompoundTag itemTag = (CompoundTag) serialized;
            ItemStack item = ItemStack.of(itemTag);
            if (ModNavigationItemData.isNavigationItem(item)) {
                changed = true;
                continue;
            }
            if (cleanNestedNavigationItems(item)) {
                CompoundTag cleanedItemTag = itemTag.copy();
                CompoundTag cleanedStackTag = item.getTag();
                if (cleanedStackTag == null) {
                    cleanedItemTag.remove("tag");
                } else {
                    cleanedItemTag.put("tag", cleanedStackTag.copy());
                }
                cleaned.add(cleanedItemTag);
                changed = true;
            } else {
                cleaned.add(itemTag.copy());
            }
        }
        if (changed) {
            parent.put(key, cleaned);
        }
        return changed;
    }
    *///?}
}
