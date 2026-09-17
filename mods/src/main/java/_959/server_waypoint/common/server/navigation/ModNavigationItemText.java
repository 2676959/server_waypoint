package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.navigation.NavigationDisplayText;
import _959.server_waypoint.navigation.NavigationTarget;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

//? if >= 1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
*///?}

/** Applies shared navigation item text through the version-specific item API. */
final class ModNavigationItemText {
    private ModNavigationItemText() {
    }

    static void apply(ItemStack item, NavigationTarget target) {
        Component name = TextHelper.toMinecraft(NavigationDisplayText.buildItemName(target));
        List<Component> lore = NavigationDisplayText.buildItemLore(target).stream()
                .map(TextHelper::toMinecraft)
                .toList();
        //? if >= 1.20.5 {
        item.set(DataComponents.CUSTOM_NAME, name);
        item.set(DataComponents.LORE, new ItemLore(lore));
        //?} else {
        /*item.setHoverName(name);
        CompoundTag display = item.getOrCreateTagElement("display");
        ListTag serializedLore = new ListTag();
        lore.stream()
                .map(Component.Serializer::toJson)
                .map(StringTag::valueOf)
                .forEach(serializedLore::add);
        display.put("Lore", serializedLore);
        *///?}
    }
}
