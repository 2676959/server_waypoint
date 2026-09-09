package _959.server_waypoint.handoff;

import _959.server_waypoint.crossserver.handoff.DestinationPlatform;
import _959.server_waypoint.crossserver.handoff.DestinationResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/** Paper/Folia player ownership and asynchronous teleport adapter; never waits for chunk loading. */
public final class PaperDestinationPlatform implements DestinationPlatform<Player> {
    private final JavaPlugin plugin;
    private final Predicate<Player> permission;

    public PaperDestinationPlatform(JavaPlugin plugin, Predicate<Player> permission) {
        this.plugin = Objects.requireNonNull(plugin);
        this.permission = Objects.requireNonNull(permission);
    }
    @Override public boolean execute(Player player, Runnable task, Runnable retired) {
        if (!plugin.isEnabled()) return false;
        // Join events can precede installation in the live player lookup. Check on the next owner tick.
        return player.getScheduler().execute(plugin, () -> {
            if (isCurrentPlayer(player)) task.run(); else retired.run();
        }, retired, 1L);
    }
    @Override public boolean ownsThread(Player player) { return Bukkit.isOwnedByCurrentRegion(player); }
    @Override public UUID playerId(Player player) { return player.getUniqueId(); }
    @Override public boolean isCurrentPlayer(Player player) {
        return plugin.isEnabled() && player.isOnline() && plugin.getServer().getPlayer(player.getUniqueId()) == player;
    }
    @Override public boolean canTeleport(Player player) { return permission.test(player); }
    @Override public CompletionStage<Boolean> teleport(Player player, DestinationResolver.Target target) {
        if (!ownsThread(player) || !isCurrentPlayer(player)) return CompletableFuture.completedFuture(false);
        NamespacedKey key = NamespacedKey.fromString(target.key().dimensionName());
        var world = key == null ? null : plugin.getServer().getWorld(key);
        if (world == null) return CompletableFuture.completedFuture(false);
        var pos = target.position();
        return player.teleportAsync(new Location(world, pos.x() + 0.5D, pos.y(), pos.z() + 0.5D, target.yaw(), 0),
                PlayerTeleportEvent.TeleportCause.PLUGIN);
    }
}
