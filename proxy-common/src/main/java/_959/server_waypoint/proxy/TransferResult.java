package _959.server_waypoint.proxy;

/** Stable adapter outcomes. SUCCESS means server switch only, never destination teleport. */
public enum TransferResult {
    SUCCESS,
    PLAYER_OFFLINE,
    SOURCE_MISMATCH,
    UNKNOWN_DESTINATION,
    PERMISSION_DENIED,
    CONNECTION_FAILED,
    CANCELLED
}
