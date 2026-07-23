package _959.server_waypoint.navigation;

import _959.server_waypoint.ModInfo;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PaperNavigationPlatform implements NavigationPlatform<Player> {
    private final NamespacedKey navigationSessionKey = new NamespacedKey(
            ModInfo.MOD_ID,
            "navigation_session"
    );
    private final Server server;
    private final Logger logger;
    private final PaperNavigationItemManager itemManager;

    public PaperNavigationPlatform(
            Server server,
            Logger logger,
            PaperNavigationItemManager itemManager
    ) {
        this.server = server;
        this.logger = logger;
        this.itemManager = itemManager;
    }

    @Override
    public UUID playerUuid(Player player) {
        return player.getUniqueId();
    }

    @Override
    public Optional<Player> findPlayer(UUID playerUuid) {
        return Optional.ofNullable(this.server.getPlayer(playerUuid));
    }

    @Override
    public NavigationSnapshot snapshot(Player player, NavigationTarget target) {
        Location location = player.getLocation();
        return NavigationMath.snapshot(
                player.getWorld().getKey().asString(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                target
        );
    }

    @Override
    public NavigationResult preflight(
            Player player,
            @Nullable NavigationSession currentSession,
            NavigationSession proposedSession
    ) {
        if (this.resolveWorld(proposedSession.target().dimensionName()) == null) {
            return NavigationResult.failure(NavigationResult.Code.TARGET_UNAVAILABLE);
        }

        int requiredSlots = 0;
        for (NavigationMethod method : proposedSession.enabledMethods()) {
            if (method.ownsItem() && !this.itemManager.hasOwnedItem(player, method)) {
                requiredSlots++;
            }
        }
        int availableSlots = this.itemManager.emptyStorageSlots(player);
        if (requiredSlots > availableSlots) {
            return NavigationResult.insufficientInventory(requiredSlots, availableSlots);
        }
        return NavigationResult.success();
    }

    @Override
    public void assertServerThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Navigation must be accessed from the Paper server thread");
        }
    }

    @Override
    public void onHandlerException(
            UUID playerUuid,
            NavigationMethod method,
            RuntimeException exception
    ) {
        this.logger.log(
                Level.SEVERE,
                "Navigation handler " + method.id() + " failed for player " + playerUuid,
                exception
        );
    }

    @Override
    public Optional<String> loadPersistedSession(Player player) {
        return Optional.ofNullable(player.getPersistentDataContainer().get(
                this.navigationSessionKey,
                PersistentDataType.STRING
        ));
    }

    @Override
    public void savePersistedSession(Player player, String encodedSession) {
        player.getPersistentDataContainer().set(
                this.navigationSessionKey,
                PersistentDataType.STRING,
                encodedSession
        );
    }

    @Override
    public void clearPersistedSession(Player player) {
        PersistentDataContainer data = player.getPersistentDataContainer();
        data.remove(this.navigationSessionKey);
    }

    @Override
    public void onPersistenceException(
            UUID playerUuid,
            String operation,
            RuntimeException exception
    ) {
        this.logger.log(
                Level.SEVERE,
                "Could not " + operation + " persistent navigation session for player " + playerUuid,
                exception
        );
    }

    public @Nullable World resolveWorld(String dimensionName) {
        NamespacedKey key = NamespacedKey.fromString(dimensionName);
        return key == null ? null : this.server.getWorld(key);
    }
}
