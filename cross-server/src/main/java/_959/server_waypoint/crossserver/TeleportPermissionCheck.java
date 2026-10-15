package _959.server_waypoint.crossserver;

/** Independent destination permission decisions, without platform types or permission-provider data. */
public record TeleportPermissionCheck(boolean tp, boolean remoteTp) {
    public boolean allowed() {
        return tp && remoteTp;
    }
}
