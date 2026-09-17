package _959.server_waypoint.common.client.gui.render;

import java.util.function.IntSupplier;

public final class WidgetThemeColors {
    private WidgetThemeColors() {
    }

    public static int getColor(WidgetThemeVariable variable) {
        return WidgetThemeManager.getColor(variable);
    }

    public static IntSupplier getColorSupplier(WidgetThemeVariable variable) {
        return WidgetThemeManager.getColorSupplier(variable);
    }
}
