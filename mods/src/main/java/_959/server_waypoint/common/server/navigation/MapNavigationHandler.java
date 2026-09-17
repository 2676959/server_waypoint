package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.common.util.DimensionFileHelper;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

final class MapNavigationHandler implements NavigationMethodHandler<ServerPlayer> {
    private final ModNavigationItemManager itemManager;
    private final ModNavigationMapCache mapCache;

    MapNavigationHandler(ModNavigationItemManager itemManager, ModNavigationMapCache mapCache) {
        this.itemManager = itemManager;
        this.mapCache = mapCache;
    }

    @Override
    public NavigationMethod method() {
        return NavigationMethod.MAP;
    }

    @Override
    public NavigationResult enable(
            ServerPlayer player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        return this.install(player, session.target());
    }

    @Override
    public void update(ServerPlayer player, NavigationSession session, NavigationSnapshot snapshot) {
        NavigationResult result = this.install(player, session.target());
        if (!result.successful()) {
            throw new IllegalStateException("Navigation map preflight capacity was not preserved");
        }
    }

    @Override
    public void disable(ServerPlayer player, NavigationSession session) {
        this.itemManager.removeMethodItems(player, this.method());
    }

    NavigationResult restore(ServerPlayer player, NavigationSession session) {
        return this.install(player, session.target());
    }

    private NavigationResult install(ServerPlayer player, NavigationTarget target) {
        ModNavigationMapCache.PreparedMap preparedMap = this.prepareMap(player, target);
        if (preparedMap == null) {
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }
        NavigationResult result = this.itemManager.updateOrInsert(
                player,
                this.method(),
                preparedMap.item()
        );
        return result;
    }

    private @Nullable ModNavigationMapCache.PreparedMap prepareMap(
            ServerPlayer player,
            NavigationTarget target
    ) {
        MinecraftServer server = WaypointServerMod.MINECRAFT_SERVER;
        ResourceKey<Level> dimension = DimensionFileHelper.getDimensionKey(target.dimensionName());
        if (server == null || dimension == null) {
            return null;
        }
        ServerLevel targetLevel = server.getLevel(dimension);
        return targetLevel == null
                ? null
                : this.mapCache.prepare(player, targetLevel, target);
    }
}
