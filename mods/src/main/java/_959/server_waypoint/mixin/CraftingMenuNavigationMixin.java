package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
//? if >= 1.21.11 {
import net.minecraft.server.level.ServerLevel;
//?} else {
/*import net.minecraft.world.level.Level;
*///?}
//? if >= 1.21 {
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CraftingMenu.class)
public abstract class CraftingMenuNavigationMixin {
    @Inject(method = "slotChangedCraftingGrid", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private static void blockNavigationItemCrafting(
            AbstractContainerMenu menu,
            //? if >= 1.21.11 {
            ServerLevel level,
            //?} else {
            /*Level level,
            *///?}
            Player player,
            CraftingContainer craftingContainer,
            ResultContainer resultContainer,
            //? if >= 1.21
            @Nullable RecipeHolder<CraftingRecipe> recipeHint,
            CallbackInfo ci
    ) {
        if (!ModNavigationHooks.containsNavigationItem(craftingContainer)) {
            return;
        }
        resultContainer.setItem(0, ItemStack.EMPTY);
        menu.setRemoteSlot(0, ItemStack.EMPTY);
        menu.broadcastChanges();
        ModNavigationHooks.deferMenuValidation(player, menu);
        ci.cancel();
    }
}
