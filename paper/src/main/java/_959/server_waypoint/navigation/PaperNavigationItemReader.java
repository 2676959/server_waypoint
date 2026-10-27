package _959.server_waypoint.navigation;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.item.component.CustomData;

/** Reads only the top-level protection marker without materializing Bukkit metadata. */
final class PaperNavigationItemReader {
    private PaperNavigationItemReader() {
    }

    static boolean isPortableStorage(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BundleItem
                || stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    static boolean isNavigationItem(ItemStack item, String key) {
        if (item.isEmpty()) {
            return false;
        }
        CustomData customData = item.get(DataComponents.CUSTOM_DATA);
        if (customData == null
                || !(customData.getUnsafe().get("PublicBukkitValues") instanceof CompoundTag pdc)) {
            return false;
        }
        // PersistentDataType.BOOLEAN accepts only the byte primitive, with any
        // nonzero byte meaning true. Do not coerce strings or other number tags.
        return pdc.get(key) instanceof ByteTag value && !ByteTag.ZERO.equals(value);
    }
}
