package _959.server_waypoint.common.client.render;

import _959.server_waypoint.common.client.gui.render.WaypointIconRenderer;

/** Layout dimensions for a projected waypoint marker, before world-distance scaling. */
final class WorldWaypointMarkerLayout {
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 2;

    private WorldWaypointMarkerLayout() {
    }

    static int width(WaypointIconRenderer.Kind kind, float initialsWidth) {
        int width = (int) Math.ceil(initialsWidth);
        return hasIcon(kind) ? Math.max(width, ICON_SIZE) : width;
    }

    static int height(WaypointIconRenderer.Kind kind, int initialsHeight) {
        return hasIcon(kind) ? ICON_SIZE + ICON_GAP + initialsHeight : initialsHeight;
    }

    static float initialsTop(WaypointIconRenderer.Kind kind) {
        return hasIcon(kind) ? ICON_SIZE + ICON_GAP : 0.0F;
    }

    static float initialsLeft(float initialsWidth) {
        int badgeWidth = (int) Math.ceil(initialsWidth);
        return (Math.max(badgeWidth, ICON_SIZE) - badgeWidth) * 0.5F;
    }

    static float iconLeft(float initialsWidth) {
        return (Math.max((int) Math.ceil(initialsWidth), ICON_SIZE) - ICON_SIZE) * 0.5F;
    }

    static float markerTop(WaypointIconRenderer.Kind kind, int initialsHeight, float centerY, float scale) {
        if (hasIcon(kind)) {
            return centerY - (initialsHeight * 0.5F + ICON_SIZE + ICON_GAP) * scale;
        }
        return centerY - height(kind, initialsHeight) * scale * 0.5F;
    }

    static float labelTop(WaypointIconRenderer.Kind kind, int initialsHeight, float centerY, float scale) {
        if (hasIcon(kind)) {
            return markerTop(kind, initialsHeight, centerY, scale) + initialsTop(kind) * scale;
        }
        return centerY - initialsHeight * scale * 0.5F;
    }

    static boolean contains(WaypointIconRenderer.Kind kind, float initialsWidth, int initialsHeight,
                            float centerX, float centerY, float scale, float pointX, float pointY) {
        float left = centerX - width(kind, initialsWidth) * scale * 0.5F;
        float top = markerTop(kind, initialsHeight, centerY, scale);
        float badgeLeft = left + (hasIcon(kind) ? initialsLeft(initialsWidth) * scale : 0.0F);
        float badgeTop = top + initialsTop(kind) * scale;
        if (inside(pointX, pointY, badgeLeft, badgeTop,
                (int) Math.ceil(initialsWidth) * scale, initialsHeight * scale)) return true;
        return hasIcon(kind) && inside(pointX, pointY,
                left + iconLeft(initialsWidth) * scale, top, ICON_SIZE * scale, ICON_SIZE * scale);
    }

    private static boolean hasIcon(WaypointIconRenderer.Kind kind) {
        return kind != WaypointIconRenderer.Kind.INITIALS;
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x <= left + width && y >= top && y <= top + height;
    }
}
