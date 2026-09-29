package _959.server_waypoint.common.client.gui.screens;

import _959.server_waypoint.common.client.gui.screens.WaypointManagerScreen.ManagerViewState;
import org.jetbrains.annotations.Nullable;

/** Pure rules for the Map mods section of {@link ClientConfigScreen}. */
final class ClientConfigSync {
    private ClientConfigSync() {
    }

    /** Which rows a map mod gets. */
    enum MapModRowState {
        /** This loader has no integration for the map mod: no rows. */
        HIDDEN,
        /** Supported but not installed: one muted "Not installed" row. */
        NOT_INSTALLED,
        /** Supported and installed: the auto-sync and sync-now rows. */
        INSTALLED
    }

    /** Why a map mod can't sync now; its message explains the disabled Sync button. */
    enum SyncBlocker {
        NO_WORLD("server_waypoint.config.sync.no_world"),
        WAYPOINTS_LOADING("server_waypoint.manager.loading"),
        NO_SERVERSIDE_SUPPORT("server_waypoint.no_serverside_support"),
        INCOMPATIBLE_SERVER("server_waypoint.incompatible_protocol_version"),
        MAP_MOD_LOADING("server_waypoint.config.sync.map_mod_loading");

        private final String messageKey;

        SyncBlocker(String messageKey) {
            this.messageKey = messageKey;
        }

        String messageKey() {
            return this.messageKey;
        }

        /** Whether the message takes the map mod's name as its {@code %s} argument. */
        boolean namesMapMod() {
            return this == MAP_MOD_LOADING;
        }
    }

    static MapModRowState resolveMapModRowState(boolean supported, boolean installed) {
        if (!supported) {
            return MapModRowState.HIDDEN;
        }
        return installed ? MapModRowState.INSTALLED : MapModRowState.NOT_INSTALLED;
    }

    /**
     * The first reason a map mod can't sync, or null when it can. The world check comes first
     * because {@code isXaerosMinimapReady} stays true after the player leaves a world.
     */
    static @Nullable SyncBlocker resolveSyncBlocker(boolean inWorld, ManagerViewState waypointState, boolean mapModReady) {
        if (!inWorld) {
            return SyncBlocker.NO_WORLD;
        }
        SyncBlocker waypointBlocker = switch (waypointState) {
            case LOADING -> SyncBlocker.WAYPOINTS_LOADING;
            case UNSUPPORTED -> SyncBlocker.NO_SERVERSIDE_SUPPORT;
            case INCOMPATIBLE -> SyncBlocker.INCOMPATIBLE_SERVER;
            case READY -> null;
        };
        if (waypointBlocker != null) {
            return waypointBlocker;
        }
        return mapModReady ? null : SyncBlocker.MAP_MOD_LOADING;
    }
}
