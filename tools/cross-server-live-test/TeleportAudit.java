import org.bukkit.event.*;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import java.nio.file.Files;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Disposable test-only plugin. Never included in a release artifact. */
public final class TeleportAudit extends JavaPlugin implements Listener {
    private final Map<UUID, Integer> counts = new HashMap<>();
    public void onEnable() { getServer().getPluginManager().registerEvents(this, this); }
    @EventHandler public void login(AsyncPlayerPreLoginEvent event) {
        if (Files.exists(getDataFolder().toPath().resolve("deny-login"))) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "STEP16 destination rejected login");
        }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) return;
        int count = counts.merge(event.getPlayer().getUniqueId(), 1, Integer::sum);
        var target = event.getTo();
        getLogger().info("STEP16_TELEPORT player=" + event.getPlayer().getUniqueId() + " count=" + count
                + " x=" + target.getX() + " y=" + target.getY() + " z=" + target.getZ());
    }
}
