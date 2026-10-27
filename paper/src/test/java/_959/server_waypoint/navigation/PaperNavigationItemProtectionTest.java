package _959.server_waypoint.navigation;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PaperNavigationItemProtectionTest {
    private static final String KEY = "server_waypoint:navigation_item";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        //? if >=26.2 {
        // Item defaults are bound during resource loading, after Bootstrap.bootStrap().
        net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_INITIALIZERS
                .build(net.minecraft.data.registries.VanillaRegistries.createLookup())
                .forEach(net.minecraft.core.component.DataComponentInitializers.PendingComponents::apply);
        //?}
        org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
    }

    @Test
    void markerRequiresTheBukkitByteTypeAndTreatsEveryNonzeroByteAsTrue() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        assertFalse(PaperNavigationItemReader.isNavigationItem(stack, KEY));
        for (byte value : new byte[]{1, -1, 2}) {
            mark(stack, ByteTag.valueOf(value));
            assertTrue(PaperNavigationItemReader.isNavigationItem(stack, KEY));
        }
        mark(stack, ByteTag.ZERO);
        assertFalse(PaperNavigationItemReader.isNavigationItem(stack, KEY));
        mark(stack, IntTag.valueOf(1));
        assertFalse(PaperNavigationItemReader.isNavigationItem(stack, KEY));
        mark(stack, StringTag.valueOf("true"));
        assertFalse(PaperNavigationItemReader.isNavigationItem(stack, KEY));
    }

    @Test
    void portableStorageGuardsRecognizeEmptyBundlesAndShulkers() {
        PaperNavigationItemManager manager = new PaperNavigationItemManager();
        for (net.minecraft.world.item.Item item : List.of(Items.BUNDLE, Items.SHULKER_BOX)) {
            assertTrue(manager.isPortableStorage(
                    org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(new ItemStack(item))));
        }
        assertFalse(manager.isPortableStorage(null));
        assertFalse(manager.isPortableStorage(
                org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(new ItemStack(Items.STONE))));
    }

    @Test
    void ignoresNestedMarkersWithoutChangingCarrierContents() {
        ItemStack protectedItem = new ItemStack(Items.FILLED_MAP);
        mark(protectedItem, ByteTag.ONE);
        for (ItemStack carrier : List.of(bundle(protectedItem), shulker(protectedItem), shulker(bundle(shulker(protectedItem))))) {
            ItemStack original = carrier.copy();
            assertFalse(PaperNavigationItemReader.isNavigationItem(carrier, KEY));
            assertTrue(ItemStack.isSameItemSameComponents(original, carrier));
        }
    }

    @Test
    void carrierTopLevelMarkerCountsRegardlessOfItsContents() {
        ItemStack box = shulker(bundle(new ItemStack(Items.DIAMOND)));
        mark(box, ByteTag.ONE);
        assertTrue(PaperNavigationItemReader.isNavigationItem(box, KEY));
        mark(box, ByteTag.ZERO);
        assertFalse(PaperNavigationItemReader.isNavigationItem(box, KEY));
    }

    @Test
    void invalidInventoryCleanupLeavesAnUntaggedCarrierUntouched() {
        ItemStack protectedItem = new ItemStack(Items.COMPASS);
        mark(protectedItem, ByteTag.ONE);
        ItemStack carrier = bundle(shulker(protectedItem));
        ItemStack original = carrier.copy();
        net.minecraft.world.SimpleContainer contents = new net.minecraft.world.SimpleContainer(carrier);
        org.bukkit.craftbukkit.inventory.CraftInventory inventory =
                new org.bukkit.craftbukkit.inventory.CraftInventory(contents);

        assertFalse(new PaperNavigationItemManager().removeFromInvalidInventory(inventory));
        assertTrue(ItemStack.isSameItemSameComponents(original, contents.getItem(0)));
    }

    @Test
    void invalidInventoryCleanupPreservesNestedMarkersAndRemovesOnlyDirectMarkers() {
        ItemStack protectedItem = new ItemStack(Items.COMPASS);
        mark(protectedItem, ByteTag.ONE);
        ItemStack carrier = shulker(bundle(protectedItem));
        ItemStack original = carrier.copy();
        net.minecraft.world.SimpleContainer contents = new net.minecraft.world.SimpleContainer(carrier, protectedItem);
        org.bukkit.craftbukkit.inventory.CraftInventory inventory =
                new org.bukkit.craftbukkit.inventory.CraftInventory(contents);
        PaperNavigationItemManager manager = new PaperNavigationItemManager();

        assertTrue(manager.removeFromInvalidInventory(inventory));
        assertTrue(contents.getItem(1).isEmpty());
        assertTrue(ItemStack.isSameItemSameComponents(original, contents.getItem(0)));
        assertFalse(manager.removeFromInvalidInventory(inventory));
    }

    private static void mark(ItemStack stack, net.minecraft.nbt.Tag value) {
        CompoundTag pdc = new CompoundTag();
        pdc.put(KEY, value);
        CompoundTag custom = new CompoundTag();
        custom.put("PublicBukkitValues", pdc);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    private static ItemStack shulker(ItemStack child) {
        ItemStack stack = new ItemStack(Items.SHULKER_BOX);
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(child)));
        return stack;
    }

    private static ItemStack bundle(ItemStack child) {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        //? if >=26.2 {
        stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(child))));
        //?} else {
        /*stack.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(List.of(child)));
        *///?}
        return stack;
    }
}
