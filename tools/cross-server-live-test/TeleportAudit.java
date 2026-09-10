import org.bukkit.event.*;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.nio.file.Files;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Disposable test-only plugin. Never included in a release artifact. */
public final class TeleportAudit extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> counts = new java.util.concurrent.ConcurrentHashMap<>();
    private UUID selectedTestPlayer;
    public void onEnable() {
        String configuredPlayer = getConfig().getString("test-player-uuid", "").strip();
        selectedTestPlayer = configuredPlayer.isEmpty() ? null : UUID.fromString(configuredPlayer);
        getServer().getPluginManager().registerEvents(this, this);
    }
    @EventHandler public void login(AsyncPlayerPreLoginEvent event) {
        if (Files.exists(getDataFolder().toPath().resolve("deny-login"))) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "STEP16 destination rejected login");
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var id = player.getUniqueId();
        getLogger().info("STEP19_JOIN player=" + id + " owned=" + Bukkit.isOwnedByCurrentRegion(player));
        if ((player.getName().equals("CodexStep19") || id.equals(selectedTestPlayer))
                && Files.exists(getDataFolder().toPath().resolve("disconnect-next-tick"))) {
            player.getScheduler().execute(this, () -> player.kickPlayer("STEP19 controlled arrival disconnect"),
                    () -> getLogger().info("STEP19_KICK_RETIRED player=" + id), 1L);
        }
        if (id.equals(selectedTestPlayer)) {
            var actionFile = getDataFolder().toPath().resolve("arrival-action.txt");
            if (Files.isRegularFile(actionFile)) {
                try {
                    String action = Files.readString(actionFile).strip();
                    player.getScheduler().execute(this, () -> {
                        getLogger().info("STEP19_ARRIVAL_ACTION player=" + id + " action=" + action
                                + " owned=" + Bukkit.isOwnedByCurrentRegion(player));
                        switch (action) {
                            case "deny-permission" -> player.addAttachment(this, "server_waypoint.command.tp", false);
                            case "move-target" -> player.performCommand("wp edit waypoint minecraft:overworld Test Target set position 8197 81 30");
                            case "disconnect" -> player.kickPlayer("STEP19 controlled authenticated arrival disconnect");
                            default -> throw new IllegalArgumentException("Unknown test arrival action");
                        }
                    }, () -> getLogger().info("STEP19_ACTION_RETIRED player=" + id), 1L);
                } catch (java.io.IOException failure) {
                    throw new IllegalStateException(failure);
                }
            }
        }
        player.getScheduler().execute(this, () -> {
            var pos = player.getLocation();
            getLogger().info("STEP19_OWNER player=" + id + " owned=" + Bukkit.isOwnedByCurrentRegion(player)
                    + " current=" + (Bukkit.getPlayer(id) == player) + " online=" + player.isOnline()
                    + " x=" + pos.getX() + " y=" + pos.getY() + " z=" + pos.getZ());
        }, () -> getLogger().info("STEP19_RETIRED player=" + id), 20L);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        getLogger().info("STEP19_QUIT player=" + event.getPlayer().getUniqueId()
                + " owned=" + Bukkit.isOwnedByCurrentRegion(event.getPlayer()));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        int count = counts.merge(event.getPlayer().getUniqueId(), 1, Integer::sum);
        var target = event.getTo();
        getLogger().info("STEP16_TELEPORT player=" + event.getPlayer().getUniqueId() + " count=" + count
                + " owned=" + Bukkit.isOwnedByCurrentRegion(event.getPlayer())
                + " x=" + target.getX() + " y=" + target.getY() + " z=" + target.getZ());
    }
}
