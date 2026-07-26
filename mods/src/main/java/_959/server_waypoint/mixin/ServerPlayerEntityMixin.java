package _959.server_waypoint.mixin;

import _959.server_waypoint.access.PlayerLocaleAccessor;
import _959.server_waypoint.access.PlayerNavigationMapIdAccessor;
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
public abstract class ServerPlayerEntityMixin implements
        PlayerNavigationMapIdAccessor,
        PlayerNavigationSessionAccessor
//? if <= 1.20.1 {
        /*, PlayerLocaleAccessor
*///?}
{
    @Unique
    private static final String sw$NAVIGATION_MAP_ID_KEY = "server_waypoint:navigation_map_id";

    @Unique
    private static final String sw$NAVIGATION_SESSION_KEY = "server_waypoint:navigation_session";

    @Unique
    private int sw$navigationMapId = -1;

    @Unique
    private @Nullable String sw$navigationSession;

    @Inject(method = "die", at = @At("HEAD")/*? if >= 26 {*/, remap = false/*?}*/)
    private void removeNavigationItemsBeforeDeath(DamageSource damageSource, CallbackInfo ci) {
        ModNavigationHooks.onPlayerDeath((ServerPlayer) (Object) this);
    }

    //? if >= 1.21.6 {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void readNavigationData(ValueInput input, CallbackInfo ci) {
        this.sw$setNavigationMapId(input.getIntOr(sw$NAVIGATION_MAP_ID_KEY, -1));
        this.sw$setNavigationSession(input.getStringOr(sw$NAVIGATION_SESSION_KEY, ""));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void writeNavigationData(ValueOutput output, CallbackInfo ci) {
        if (this.sw$navigationMapId >= 0) {
            output.putInt(sw$NAVIGATION_MAP_ID_KEY, this.sw$navigationMapId);
        }
        if (this.sw$navigationSession != null) {
            output.putString(sw$NAVIGATION_SESSION_KEY, this.sw$navigationSession);
        }
    }
    //?} else {
    /*@Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readNavigationData(CompoundTag tag, CallbackInfo ci) {
        //? if >= 1.21.5 {
        this.sw$setNavigationMapId(tag.getInt(sw$NAVIGATION_MAP_ID_KEY).orElse(-1));
        //?} else {
        /^this.sw$setNavigationMapId(
                tag.contains(sw$NAVIGATION_MAP_ID_KEY, 99)
                        ? tag.getInt(sw$NAVIGATION_MAP_ID_KEY)
                        : -1
        );^/
        //?}
        //? if >= 1.21.5 {
        this.sw$setNavigationSession(tag.getString(sw$NAVIGATION_SESSION_KEY).orElse(""));
        //?} else
        /^this.sw$setNavigationSession(tag.getString(sw$NAVIGATION_SESSION_KEY));^/
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void writeNavigationData(CompoundTag tag, CallbackInfo ci) {
        if (this.sw$navigationMapId >= 0) {
            tag.putInt(sw$NAVIGATION_MAP_ID_KEY, this.sw$navigationMapId);
        }
        if (this.sw$navigationSession != null) {
            tag.putString(sw$NAVIGATION_SESSION_KEY, this.sw$navigationSession);
        }
    }
    *///?}

    @Inject(method = "restoreFrom", at = @At("TAIL")/*? if >= 26 {*/, remap = false/*?}*/)
    private void copyNavigationData(ServerPlayer oldPlayer, boolean restoreAll, CallbackInfo ci) {
        this.sw$setNavigationMapId(
                ((PlayerNavigationMapIdAccessor) oldPlayer).sw$getNavigationMapId()
        );
        this.sw$setNavigationSession(
                ((PlayerNavigationSessionAccessor) oldPlayer).sw$getNavigationSession()
        );
    }

    @Override
    public int sw$getNavigationMapId() {
        return this.sw$navigationMapId;
    }

    @Override
    public void sw$setNavigationMapId(int mapId) {
        this.sw$navigationMapId = Math.max(mapId, -1);
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
