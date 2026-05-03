package _959.server_waypoint.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ServerPlayer.class)
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