package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.navigation.NavigationDisplayText;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import net.minecraft.server.level.ServerPlayer;

final class ActionbarNavigationHandler implements NavigationMethodHandler<ServerPlayer> {
    @Override
    public NavigationMethod method() {
        return NavigationMethod.ACTIONBAR;
    }

    @Override
    public NavigationResult enable(
            ServerPlayer player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public void update(ServerPlayer player, NavigationSession session, NavigationSnapshot snapshot) {
        player.sendSystemMessage(
                ModMessageSender.getInstance().getTranslatedText(
                        player,
                        NavigationDisplayText.build(session, snapshot)
                ),
                true
        );
    }

    @Override
    public void disable(ServerPlayer player, NavigationSession session) {
        // Intentionally do not send an empty actionbar; it may erase another mod's content.
    }
}
