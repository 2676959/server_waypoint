package _959.server_waypoint.common.server.navigation;

import _959.server_waypoint.common.network.ModMessageSender;
import _959.server_waypoint.navigation.NavigationDisplayText;
import _959.server_waypoint.navigation.NavigationMethod;
import _959.server_waypoint.navigation.NavigationMethodHandler;
import _959.server_waypoint.navigation.NavigationResult;
import _959.server_waypoint.navigation.NavigationSession;
import _959.server_waypoint.navigation.NavigationSnapshot;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class BossbarNavigationHandler implements NavigationMethodHandler<ServerPlayer> {
    private final Map<UUID, ServerBossEvent> bossbars = new HashMap<>();

    @Override
    public NavigationMethod method() {
        return NavigationMethod.BOSSBAR;
    }

    @Override
    public NavigationResult enable(
            ServerPlayer player,
            NavigationSession session,
            NavigationSnapshot snapshot
    ) {
        ServerBossEvent bossbar = this.bossbars.computeIfAbsent(
                player.getUUID(),
                this::createBossbar
        );
        bindPlayer(bossbar, player);
        this.update(player, session, snapshot);
        return NavigationResult.success();
    }

    @Override
    public void update(ServerPlayer player, NavigationSession session, NavigationSnapshot snapshot) {
        ServerBossEvent bossbar = this.bossbars.get(player.getUUID());
        if (bossbar == null) {
            return;
        }
        bindPlayer(bossbar, player);
        bossbar.setName(ModMessageSender.getInstance().getTranslatedText(
                player,
                NavigationDisplayText.build(session, snapshot)
        ));
        bossbar.setProgress(snapshot.inTargetDimension() ? snapshot.facingProgress() : 0.0F);
        bossbar.setVisible(true);
    }

    @Override
    public void disable(ServerPlayer player, NavigationSession session) {
        ServerBossEvent bossbar = this.bossbars.remove(player.getUUID());
        if (bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar.setVisible(false);
        }
    }

    @Override
    public void cleanupPlayer(UUID playerUuid, NavigationSession session) {
        ServerBossEvent bossbar = this.bossbars.remove(playerUuid);
        if (bossbar != null) {
            bossbar.removeAllPlayers();
            bossbar.setVisible(false);
        }
    }

    private ServerBossEvent createBossbar(UUID playerUuid) {
        //? if >= 26 {
        UUID bossbarUuid = UUID.nameUUIDFromBytes(
                ("server_waypoint:navigation:" + playerUuid).getBytes(StandardCharsets.UTF_8)
        );
        return new ServerBossEvent(
                bossbarUuid,
                net.minecraft.network.chat.Component.empty(),
                BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        //?} else {
        /*return new ServerBossEvent(
                net.minecraft.network.chat.Component.empty(),
                BossEvent.BossBarColor.WHITE,
                BossEvent.BossBarOverlay.PROGRESS
        );
        *///?}
    }

    private static void bindPlayer(ServerBossEvent bossbar, ServerPlayer player) {
        if (!bossbar.getPlayers().contains(player)) {
            bossbar.removeAllPlayers();
            bossbar.addPlayer(player);
        }
    }
}
