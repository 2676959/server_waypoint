package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.navigation.NavigationMethod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

//? if >= 1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
//?}

/**
 * Reads and writes the ownership marker shared by navigation item handlers and
 * inventory-protection hooks.
 */
public final class ModNavigationItemData {
    private static final String NAVIGATION_ITEM_KEY = "server_waypoint:navigation_item";
    private static final String OWNER_KEY = "owner";
    private static final String METHOD_KEY = "method";
    private static final String MARKER_VALUE = "true";

    private ModNavigationItemData() {
    }

    public static void tag(ItemStack stack, UUID owner, NavigationMethod method) {
        stack.setCount(1);
        //? if >= 1.20.5 {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> writeMarker(tag, owner, method));
        //?} else {
        /*writeMarker(stack.getOrCreateTag(), owner, method);
        *///?}
    }

    public static boolean isNavigationItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = copyTag(stack);
        return tag != null && MARKER_VALUE.equals(readString(tag, NAVIGATION_ITEM_KEY));
    }

    public static boolean isOwnedBy(ItemStack stack, UUID owner) {
        return owner(stack).filter(owner::equals).isPresent();
    }

    public static Optional<UUID> owner(ItemStack stack) {
        CompoundTag tag = copyTag(stack);
        if (tag == null || !MARKER_VALUE.equals(readString(tag, NAVIGATION_ITEM_KEY))) {
            return Optional.empty();
        }
        String owner = readString(tag, OWNER_KEY);
        if (owner == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(owner));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static Optional<NavigationMethod> method(ItemStack stack) {
        CompoundTag tag = copyTag(stack);
        if (tag == null || !MARKER_VALUE.equals(readString(tag, NAVIGATION_ITEM_KEY))) {
            return Optional.empty();
        }
        return NavigationMethod.fromId(readString(tag, METHOD_KEY));
    }

    private static void writeMarker(CompoundTag tag, UUID owner, NavigationMethod method) {
        tag.putString(NAVIGATION_ITEM_KEY, MARKER_VALUE);
        tag.putString(OWNER_KEY, owner.toString());
        tag.putString(METHOD_KEY, method.id());
    }

    private static @Nullable CompoundTag copyTag(ItemStack stack) {
        //? if >= 1.20.5 {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        return tag == null ? null : tag.copy();
        *///?}
    }

    private static @Nullable String readString(CompoundTag tag, String key) {
        //? if >= 1.21.5 {
        return tag.getString(key).orElse(null);
        //?} else {
        /*String value = tag.getString(key);
        return value.isEmpty() ? null : value;
        *///?}
    }
}
