package _959.server_waypoint.common.client.gui.layout;

/** Allocates two content-sized rails growing inward from opposite ends of a shared space. */
public final class OpposedExpansionLayout {
    private OpposedExpansionLayout() { }

    public record Sizes(int top, int bottom) { }

    public static Sizes allocate(int available, int minimum, int topPreferred, int bottomPreferred, int gap) {
        int usable = Math.max(0, available - gap);
        // Below the minimum viewport, callers hide both rails instead of overlapping controls.
        if (usable < minimum * 2) return new Sizes(0, 0);
        int top = Math.max(minimum, topPreferred);
        int bottom = Math.max(minimum, bottomPreferred);
        if (top + bottom <= usable) return new Sizes(top, bottom);
        int half = usable / 2;
        if (top <= half) return new Sizes(top, usable - top);
        if (bottom <= half) return new Sizes(usable - bottom, bottom);
        return new Sizes(half, usable - half);
    }
}
