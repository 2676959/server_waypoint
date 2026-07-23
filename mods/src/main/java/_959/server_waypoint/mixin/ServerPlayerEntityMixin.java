package _959.server_waypoint.mixin;

import _959.server_waypoint.access.PlayerLocaleAccessor;
import _959.server_waypoint.access.PlayerNavigationSessionAccessor;
import _959.server_waypoint.common.server.navigation.ModNavigationHooks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if <= 1.20.1
/*import net.minecraft.network.protocol.game.ServerboundClientInformationPacket;*/
//? if >= 1.21.6 {
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
//?} else {
/*import net.minecraft.nbt.CompoundTag;
*///?}

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerEntityMixin implements PlayerNavigationSessionAccessor
//? if <= 1.20.1 {
        /*, PlayerLocaleAccessor
*///?}
{
    @Unique
    private static final String sw$NAVIGATION_SESSION_KEY = "server_waypoint:navigation_session";

    @Unique
    private @Nullable String sw$navigationSession;

    @Inject(method = "die", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)
    private void removeNavigationItemsBeforeDeath(DamageSource damageSource, CallbackInfo ci) {
        ModNavigationHooks.onPlayerDeath((ServerPlayer) (Object) this);
    }

    //? if >= 1.21.6 {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void readNavigationSession(ValueInput input, CallbackInfo ci) {
        this.sw$setNavigationSession(input.getStringOr(sw$NAVIGATION_SESSION_KEY, ""));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void writeNavigationSession(ValueOutput output, CallbackInfo ci) {
        if (this.sw$navigationSession != null) {
            output.putString(sw$NAVIGATION_SESSION_KEY, this.sw$navigationSession);
        }
    }
    //?} else {
    /*@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readNavigationSession(CompoundTag tag, CallbackInfo ci) {
        //? if >= 1.21.5 {
        this.sw$setNavigationSession(tag.getString(sw$NAVIGATION_SESSION_KEY).orElse(""));
        //?} else
        /^this.sw$setNavigationSession(tag.getString(sw$NAVIGATION_SESSION_KEY));^/
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void writeNavigationSession(CompoundTag tag, CallbackInfo ci) {
        if (this.sw$navigationSession != null) {
            tag.putString(sw$NAVIGATION_SESSION_KEY, this.sw$navigationSession);
        }
    }
    *///?}

    @Inject(method = "restoreFrom", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void copyNavigationSession(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        this.sw$setNavigationSession(
                ((PlayerNavigationSessionAccessor) oldPlayer).sw$getNavigationSession()
        );
    }

    @Override
    public @Nullable String sw$getNavigationSession() {
        return this.sw$navigationSession;
    }

    @Override
    public void sw$setNavigationSession(@Nullable String encodedSession) {
        this.sw$navigationSession = encodedSession == null || encodedSession.isBlank()
                ? null
                : encodedSession;
    }

//? if <= 1.20.1 {
    /*@Unique
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
*///?}
}
