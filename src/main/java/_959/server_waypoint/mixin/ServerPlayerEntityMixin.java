package _959.server_waypoint.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.server.network.ServerPlayerEntity;
//? if <= 1.20.1 {
/*import _959.server_waypoint.access.PlayerLocaleAccessor;
import net.minecraft.network.packet.c2s.play.ClientSettingsC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin
//? if <= 1.20.1 {
        /*implements PlayerLocaleAccessor
*///?}
{
//? if <= 1.20.1 {
    /*@Unique
    private String sw$locale;

    @Inject(
            method = "setClientSettings",
            at = @At(value = "TAIL")
    )
    private void onClientSettings(ClientSettingsC2SPacket packet, CallbackInfo ci) {
        this.sw$locale = packet.language();
    }

    @Nullable
    @Override
    public String sw$getLocale() {
        return this.sw$locale;
    }
*///?}
}