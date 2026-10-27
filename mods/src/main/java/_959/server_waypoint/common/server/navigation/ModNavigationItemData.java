package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.navigation.NavigationMethod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

//? if >= 1.20.5 {
import _959.server_waypoint.mixin.CustomDataAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}

/**
 * Reads and writes the protection marker shared by navigation item handlers and
 * inventory-protection hooks.
 */
public final class ModNavigationItemData {
    private static final String NAVIGATION_ITEM_KEY = "server_waypoint:navigation_item";
    private static final String MARKER_VALUE = "true";
    private static final StringTag MARKER_TAG = StringTag.valueOf(MARKER_VALUE);

    private ModNavigationItemData() {
    }

    public static void tag(ItemStack stack) {
        stack.setCount(1);
        //? if >= 1.20.5 {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, ModNavigationItemData::writeMarker);
        //?} else {
        /*writeMarker(stack.getOrCreateTag());
        *///?}
    }

    public static boolean isNavigationItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        //? if >= 1.20.5 {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && hasMarker(((CustomDataAccessor) (Object) data).serverWaypoint$getTag());
        //?} else {
        /*return hasMarker(stack.getTag());
        *///?}
    }

    public static Optional<NavigationMethod> method(ItemStack stack) {
        if (!isNavigationItem(stack)) {
            return Optional.empty();
        }
        if (stack.getItem() == Items.COMPASS) {
            return Optional.of(NavigationMethod.COMPASS);
        }
        if (stack.getItem() == Items.FILLED_MAP) {
            return Optional.of(NavigationMethod.MAP);
        }
        return Optional.empty();
    }

    private static void writeMarker(CompoundTag tag) {
        tag.putString(NAVIGATION_ITEM_KEY, MARKER_VALUE);
    }

    static boolean hasMarker(@Nullable CompoundTag tag) {
        return tag != null && MARKER_TAG.equals(tag.get(NAVIGATION_ITEM_KEY));
    }
}
