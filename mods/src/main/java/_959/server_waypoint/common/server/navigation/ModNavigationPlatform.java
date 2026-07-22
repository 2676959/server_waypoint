package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.server.WaypointServerMod;
import _959.server_waypoint.navigation.NavigationMath;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationPlatform;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import _959.server_waypoint.navigation.NavigationTarget;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

final class ModNavigationPlatform implements NavigationPlatform<ServerPlayer> {
    private final ModNavigationItemManager itemManager;

    ModNavigationPlatform(ModNavigationItemManager itemManager) {
        this.itemManager = itemManager;
    }

    @Override
    public UUID playerUuid(ServerPlayer player) {
        return player.getUUID();
    }

    @Override
    public Optional<ServerPlayer> findPlayer(UUID playerUuid) {
        MinecraftServer server = WaypointServerMod.MINECRAFT_SERVER;
        return server == null
                ? Optional.empty()
                : Optional.ofNullable(server.getPlayerList().getPlayer(playerUuid));
    }

    @Override
    public NavigationSnapshot snapshot(ServerPlayer player, NavigationTarget target) {
        //? if >= 1.21.11 {
        String dimensionName = player.level().dimension().identifier().toString();
        //?} else {
        /*String dimensionName = player.level().dimension().location().toString();
        *///?}
        return NavigationMath.snapshot(
                dimensionName,
                player.getX(),
                player.getY(),
                player.getZ(),
                player.getYRot(),
                target
        );
    }

    @Override
    public NavigationResult preflight(
            ServerPlayer player,
            @Nullable NavigationSession currentSession,
            NavigationSession proposedSession
    ) {
        return this.itemManager.preflight(player, currentSession, proposedSession);
    }

    @Override
    public void assertServerThread() {
        MinecraftServer server = WaypointServerMod.MINECRAFT_SERVER;
        if (server != null && !server.isSameThread()) {
            throw new IllegalStateException("Navigation state must be accessed on the server thread");
        }
    }

    @Override
    public void onHandlerException(UUID playerUuid, NavigationMethod method, RuntimeException exception) {
        WaypointServerMod.LOGGER.error(
                "Navigation method {} failed for player {}",
                method.id(),
                playerUuid,
                exception
        );
    }
}
