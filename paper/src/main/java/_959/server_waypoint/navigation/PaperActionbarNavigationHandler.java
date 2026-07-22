package _959.server_waypoint.navigation;

import org.bukkit.entity.Player;

public final class PaperActionbarNavigationHandler implements NavigationMethodHandler<Player> {
    @Override
    public NavigationMethod method() {
        return NavigationMethod.ACTIONBAR;
    }

    @Override
    public NavigationResult enable(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public void update(
            Player player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        player.sendActionBar(NavigationDisplayText.build(session, snapshot));
    }

    @Override
    public void disable(Player player, NavigationSession session) {
        // Deliberately do not send an empty actionbar; it could erase content
        // from another plugin or server feature.
    }
}
