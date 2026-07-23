package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.server.WaypointServerMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.Nullable;

/** Small static bridge used by vanilla server Mixins. */
public final class ModNavigationHooks {
    private ModNavigationHooks() {
    }

    public static boolean shouldBlockMenuClick(
            AbstractContainerMenu menu,
            int slotIndex,
            int button,
            String clickType,
            Player player
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        Slot slot = slotIndex >= 0 && slotIndex < menu.slots.size()
                ? menu.slots.get(slotIndex)
                : null;
        ItemStack clicked = slot == null ? ItemStack.EMPTY : slot.getItem();
        ItemStack carried = menu.getCarried();
        ItemStack swapped = "SWAP".equals(clickType)
                && button >= 0
                && button < serverPlayer.getInventory().getContainerSize()
                ? serverPlayer.getInventory().getItem(button)
                : ItemStack.EMPTY;
        boolean clickedNavigationItem = ModNavigationItemData.isNavigationItem(clicked);
        boolean carriedNavigationItem = ModNavigationItemData.isNavigationItem(carried);
        boolean swappedNavigationItem = ModNavigationItemData.isNavigationItem(swapped);
        if (!clickedNavigationItem && !carriedNavigationItem && !swappedNavigationItem) {
            return false;
        }

        boolean blocked = false;
        if (carriedNavigationItem) {
            blocked = slot == null
                    || slot.container != serverPlayer.getInventory()
                    || isNestedStorage(clicked)
                    || "THROW".equals(clickType)
                    || "CLONE".equals(clickType)
                    || "PICKUP_ALL".equals(clickType);
        }
        if (clickedNavigationItem) {
            blocked |= slot == null
                    || slot.container != serverPlayer.getInventory()
                    || isNestedStorage(carried)
                    || "THROW".equals(clickType)
                    || "CLONE".equals(clickType)
                    || "PICKUP_ALL".equals(clickType)
                    || "QUICK_MOVE".equals(clickType) && menu != serverPlayer.inventoryMenu;
        }
        if (swappedNavigationItem) {
            blocked |= slot == null
                    || slot.container != serverPlayer.getInventory();
        }
        if (blocked) {
            denyMenuClick(serverPlayer, menu);
        }
        return blocked;
    }

    public static void afterMenuClick(Player player, AbstractContainerMenu menu) {
        ModNavigationRuntime runtime = runtime();
        if (runtime != null && player instanceof ServerPlayer serverPlayer) {
            runtime.validateMenu(serverPlayer, menu, true);
        }
    }

    public static void onMenuClosed(Player player, AbstractContainerMenu menu) {
        ModNavigationRuntime runtime = runtime();
        if (runtime != null && player instanceof ServerPlayer serverPlayer) {
            runtime.onInventoryClose(serverPlayer, menu);
        }
    }

    public static boolean shouldBlockSelectedDrop(Player player, ItemStack selectedItem) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !ModNavigationItemData.isNavigationItem(selectedItem)) {
            return false;
        }

        // The client predicts Q-drops before the server handles the action. Since
        // removeFromSelected is cancelled, vanilla has no changed slot to send back.
        serverPlayer.containerMenu.sendAllDataToRemote();
        return true;
    }

    public static void onPlayerDeath(ServerPlayer player) {
        ModNavigationRuntime runtime = runtime();
        if (runtime != null) {
            runtime.onPlayerDeath(player);
        }
    }

    public static boolean containsNavigationItem(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (ModNavigationItemData.isNavigationItem(container.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    public static void deferMenuValidation(Player player, AbstractContainerMenu menu) {
        ModNavigationRuntime runtime = runtime();
        MinecraftServer server = WaypointServerMod.MINECRAFT_SERVER;
        if (runtime != null && server != null && player instanceof ServerPlayer serverPlayer) {
            server.execute(() -> runtime.validateMenu(serverPlayer, menu, true));
        }
    }

    public static boolean isNavigationItem(ItemStack stack) {
        return ModNavigationItemData.isNavigationItem(stack);
    }

    public static void sanitizeAllay(Allay allay) {
        ItemStack heldItem = allay.getItemInHand(InteractionHand.MAIN_HAND);
        if (ModNavigationItemData.isNavigationItem(heldItem)) {
            allay.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        }
        removeNavigationItems(allay.getInventory());
    }

    public static void sanitizeArmorStand(ArmorStand armorStand) {
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.MAINHAND);
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.OFFHAND);
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.HEAD);
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.CHEST);
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.LEGS);
        sanitizeArmorStandSlot(armorStand, EquipmentSlot.FEET);
    }

    private static void denyMenuClick(ServerPlayer player, AbstractContainerMenu menu) {
        ModNavigationRuntime runtime = runtime();
        if (runtime != null) {
            runtime.sendMovementDenied(player);
            runtime.validateMenu(player, menu, false);
        }
        menu.sendAllDataToRemote();
    }

    private static boolean isNestedStorage(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.getItem() instanceof BundleItem) {
            return true;
        }
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static void removeNavigationItems(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (ModNavigationItemData.isNavigationItem(container.getItem(slot))) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static void sanitizeArmorStandSlot(ArmorStand armorStand, EquipmentSlot slot) {
        if (ModNavigationItemData.isNavigationItem(armorStand.getItemBySlot(slot))) {
            armorStand.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static @Nullable ModNavigationRuntime runtime() {
        WaypointServerMod waypointServer = WaypointServerMod.getInstance();
        return waypointServer == null ? null : waypointServer.navigation();
    }
}
