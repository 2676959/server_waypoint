package _959.server_waypoint.common.util;

public final class SyncedWaypointHighlight {
    public static final int XAEROS_SYNCED_DEFAULT_BACKGROUND = 0x7F0D47A1;
    public static final int XAEROS_SYNCED_HOVERED_BACKGROUND = 0xC80D47A1;
    public static final int XAEROS_SYNCED_SELECTED_BACKGROUND = 0xCC00BCD4;
    public static final int XAEROS_SYNCED_SELECTED_HOVERED_BACKGROUND = 0xDD26C6DA;

    private SyncedWaypointHighlight() {
    }

    public static int xaerosBackground(int color) {
        return switch (color) {
            case -10496 -> XAEROS_SYNCED_SELECTED_HOVERED_BACKGROUND;
            case -922757376 -> XAEROS_SYNCED_SELECTED_BACKGROUND;
            case -13487566 -> XAEROS_SYNCED_HOVERED_BACKGROUND;
            case -939524096 -> XAEROS_SYNCED_DEFAULT_BACKGROUND;
            default -> color;
        };
    }
}