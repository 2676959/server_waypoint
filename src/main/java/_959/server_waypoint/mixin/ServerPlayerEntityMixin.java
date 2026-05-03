package _959.server_waypoint.mixin;

import _959.server_waypoint.access.PlayerLocaleAccessor;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <= 1.20.1
import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin
//? if <= 1.20.1 {
        implements PlayerLocaleAccessor
//?}
{
//? if <= 1.20.1 {
    @Unique
    private String sw$locale;

    @Inject(
            method = "updateOptions",
            at = @At(value = "TAIL")
    )
    private void onClientSettings(ServerboundClientInformationPacket packet, CallbackInfo ci) {
        this.sw$locale = packet.language();
    }

    @Nullable
    @Override
    public String sw$getLocale() {
        return this.sw$locale;
    }
//?}
}
