package _959.server_waypoint.navigation;

import org.bukkit.entity.Player;

/**
 * Paper item handlers expose a lifecycle-only restore operation in addition to
 * the shared navigation handler contract.
 */
public interface PaperItemNavigationHandler {
    NavigationMethod method();

    boolean restore(Player player, NavigationSession session);
}
