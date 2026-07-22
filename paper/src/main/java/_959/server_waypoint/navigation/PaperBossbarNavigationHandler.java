package _959.server_waypoint.navigation;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PaperBossbarNavigationHandler implements NavigationMethodHandler<Player> {
    private final Map<UUID, BossBar> bossbars = new HashMap<>();

    @Override
    public NavigationMethod method() {
        return NavigationMethod.BOSSBAR;
    }

    @Override
    public NavigationResult enable(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        BossBar previous = this.bossbars.remove(player.getUniqueId());
        if (previous != null) {
            player.hideBossBar(previous);
        }
        BossBar bossbar = BossBar.bossBar(
                NavigationDisplayText.build(session, snapshot),
                progress(snapshot),
                BossBar.Color.WHITE,
                BossBar.Overlay.PROGRESS
        );
        this.bossbars.put(player.getUniqueId(), bossbar);
        player.showBossBar(bossbar);
        return NavigationResult.success();
    }

    @Override
    public void update(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        BossBar bossbar = this.bossbars.get(player.getUniqueId());
        if (bossbar == null) {
            this.enable(player, session, snapshot);
            return;
        }
        bossbar.name(NavigationDisplayText.build(session, snapshot));
        bossbar.progress(progress(snapshot));
    }

    @Override
    public void disable(Player player, NavigationSession session) {
        BossBar bossbar = this.bossbars.remove(player.getUniqueId());
        if (bossbar != null) {
            player.hideBossBar(bossbar);
        }
    }

    @Override
    public void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        this.bossbars.remove(playerUuid);
    }

    private static float progress(NavigationSnapshot snapshot) {
        return snapshot.inTargetDimension()
                ? Math.max(0.0F, Math.min(1.0F, snapshot.facingProgress()))
                : 0.0F;
    }
}
