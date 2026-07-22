package _959.server_waypoint.mixin;

import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
//? if >= 1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ArmorStand.class)
public abstract class ArmorStandNavigationMixin {
    @Inject(
            method = "swapItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/InteractionHand;)Z",
            at = @At("HEAD"),
            cancellable = true/*? if >= 26 {*/,
            remap = false/*?}*/
    )
    private void blockNavigationItemInsertion(
            Player player,
            EquipmentSlot slot,
            ItemStack playerItem,
            InteractionHand hand,
            CallbackInfoReturnable<Boolean> cir
    ) {
        ModNavigationHooks.sanitizeArmorStand((ArmorStand) (Object) this);
        if (ModNavigationHooks.isNavigationItem(playerItem)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void sanitizeLoadedNavigationItems(
            //? if >= 1.21.6 {
            ValueInput input,
            //?} else {
            /*CompoundTag tag,
            *///?}
            CallbackInfo ci
    ) {
        ModNavigationHooks.sanitizeArmorStand((ArmorStand) (Object) this);
    }
}
