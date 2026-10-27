package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
//? if >= 26 {
import net.minecraft.world.inventory.ContainerInput;
//?} else {
/*import net.minecraft.world.inventory.ClickType;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuNavigationMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true/*? if >= 26 {*/, remap = false/*?}*/)
    private void beforeNavigationItemClick(
            int slotIndex,
            int button,
            //? if >= 26 {
            ContainerInput input,
            //?} else {
            /*ClickType input,
            *///?}
            Player player,
            CallbackInfo ci,
            @Share("serverWaypointValidateMenu") LocalBooleanRef validateMenu
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (ModNavigationHooks.shouldBlockMenuClick(menu, slotIndex, button, input.name(), player)) {
            ci.cancel();
            return;
        }
        validateMenu.set(ModNavigationHooks.menuClickNeedsValidation(menu, slotIndex, button, input.name(), player));
    }

    @Inject(method = "clicked", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void afterNavigationItemClick(
            int slotIndex,
            int button,
            //? if >= 26 {
            ContainerInput input,
            //?} else {
            /*ClickType input,
            *///?}
            Player player,
            CallbackInfo ci,
            @Share("serverWaypointValidateMenu") LocalBooleanRef validateMenu
    ) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        if (validateMenu.get()
                || ModNavigationHooks.menuClickNeedsValidation(menu, slotIndex, button, input.name(), player)) {
            ModNavigationHooks.afterMenuClick(player, menu);
        }
    }

    @Inject(method = "removed", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)
    private void beforeNavigationMenuRemoved(Player player, CallbackInfo ci) {
        ModNavigationHooks.onMenuClosed(player, (AbstractContainerMenu) (Object) this);
    }
}
