package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.util.DimensionFileHelper;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

//? if >= 1.20.5 {
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
//?} else {
/*import com.mojang.serialization.DynamicOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
*///?}

final class CompassNavigationHandler implements NavigationMethodHandler<ServerPlayer> {
    private final ModNavigationItemManager itemManager;

    CompassNavigationHandler(ModNavigationItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public NavigationMethod method() {
        return NavigationMethod.COMPASS;
    }

    @Override
    public NavigationResult enable(
            ServerPlayer player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        ItemStack compass = this.createCompass(session.target());
        if (compass == null) {
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        return this.itemManager.updateOrInsert(player, this.method(), compass);
    }

    @Override
    public void update(ServerPlayer player, NavigationSession session, NavigationSnapshot snapshot) {
        ItemStack compass = this.createCompass(session.target());
        if (compass == null) {
            throw new IllegalStateException("Navigation compass target dimension is unavailable");
        }
        NavigationResult result = this.itemManager.updateOrInsert(player, this.method(), compass);
        if (!result.successful()) {
            throw new IllegalStateException("Navigation compass preflight capacity was not preserved");
        }
    }

    @Override
    public void disable(ServerPlayer player, NavigationSession session) {
        this.itemManager.removeMethodItems(player, this.method());
    }

    NavigationResult restore(ServerPlayer player, NavigationSession session) {
        ItemStack compass = this.createCompass(session.target());
        return compass == null
                ? NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE)
                : this.itemManager.updateOrInsert(player, this.method(), compass);
    }

    private @Nullable ItemStack createCompass(NavigationTarget target) {
        ResourceKey<Level> dimension = DimensionFileHelper.getDimensionKey(target.dimensionName());
        if (dimension == null) {
            return null;
        }
        BlockPos position = new BlockPos(
                target.position().x(),
                target.position().y(),
                target.position().z()
        );
        ItemStack compass = new ItemStack(Items.COMPASS);
        //? if >= 1.20.5 {
        compass.set(
                DataComponents.LODESTONE_TRACKER,
                new LodestoneTracker(Optional.of(GlobalPos.of(dimension, position)), false)
        );
        //?} else {
        /*DataResult<Tag> encodedDimension = Level.RESOURCE_KEY_CODEC.encodeStart(NbtOps.INSTANCE, dimension);
        encodedDimension.result().ifPresent(tag -> compass.getOrCreateTag().put("LodestoneDimension", tag));
        compass.getOrCreateTag().put("LodestonePos", net.minecraft.nbt.NbtUtils.writeBlockPos(position));
        compass.getOrCreateTag().putBoolean("LodestoneTracked", false);
        *///?}
        return compass;
    }
}
