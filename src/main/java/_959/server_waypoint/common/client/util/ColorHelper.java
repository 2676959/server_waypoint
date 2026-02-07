package _959.server_waypoint.common.client.util;

import _959.server_waypoint.common.util.MathHelper;

public final class ColorHelper {
    public static int scaleRgb(int argb, float scale) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;

        r = (int) (r * scale);
        g = (int) (g * scale);
        b = (int) (b * scale);

        r = MathHelper.clamp(r, 0, 255);
        g = MathHelper.clamp(g, 0, 255);
        b = MathHelper.clamp(b, 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
