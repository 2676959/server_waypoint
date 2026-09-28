package _959.server_waypoint.common.client.render;

/** Carries the waypoint item's tint from GUI extraction to the item-atlas draw. */
public final class WaypointItemAlpha {
    private static final ThreadLocal<Integer> CURRENT_TINT = ThreadLocal.withInitial(() -> -1);

    private WaypointItemAlpha() {
    }

    public static int currentTint() {
        return CURRENT_TINT.get();
    }

    public static int pushWorldItemTint(int alpha) {
        int previous = currentTint();
        int premultipliedTint = (alpha << 24) | (alpha << 16) | (alpha << 8) | alpha;
        CURRENT_TINT.set(premultipliedTint);
        return previous;
    }

    public static void restoreTint(int previous) {
        if (previous == -1) {
            CURRENT_TINT.remove();
        } else {
            CURRENT_TINT.set(previous);
        }
    }
}
