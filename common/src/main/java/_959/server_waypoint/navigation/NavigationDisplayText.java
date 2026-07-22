package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * Builds the shared actionbar and bossbar text without retaining platform
 * player or display objects.
 */
public final class NavigationDisplayText {
    private static final double STRAIGHT_AHEAD_THRESHOLD_DEGREES = 0.5D;
    public static final String SYMBOL_HIGH = "⏶";
    public static final String SYMBOL_LOW = "⏷";
    public static final String SYMBOL_RIGHT = "⏵";
    public static final String SYMBOL_LEFT = "⏴";
    public static final String SYMBOL_SAME_LEVEL = "⏺";
    public static final String SYMBOL_FORWARD = "↑";

    private NavigationDisplayText() {
    }

    public static Component build(NavigationSession session, NavigationSnapshot snapshot) {
        NavigationTarget target = session.target();
        Component targetName = text(target.waypointName(), TextColor.color(target.rgb()));
        if (!snapshot.inTargetDimension()) {
            return empty()
                    .append(targetName)
                    .append(text(" — "))
                    .append(translatable(
                            "waypoint.navigation.wrong_dimension",
                            text(target.dimensionName())
                    ));
        }

        long distance = Math.round(snapshot.horizontalDistance());
        return empty()
                .append(targetName)
                .append(text(" — "))
                .append(turnIndicator(snapshot.signedTurnAngle()))
                .append(text(" | "))
                .append(meters(distance))
                .append(text(" "))
                .append(verticalDifference(snapshot.verticalDifference()));
    }

    private static Component turnIndicator(double signedTurn) {
        if (Math.abs(signedTurn) < STRAIGHT_AHEAD_THRESHOLD_DEGREES) {
            return text(SYMBOL_FORWARD);
        }
        String symbol = signedTurn < 0.0D ? SYMBOL_LEFT : SYMBOL_RIGHT;
        return text(symbol + Math.round(Math.abs(signedTurn)) + "°");
    }

    private static Component verticalDifference(double signedDifference) {
        long difference = Math.round(Math.abs(signedDifference));
        String symbol = difference == 0L ? SYMBOL_SAME_LEVEL : signedDifference > 0.0D ? SYMBOL_HIGH : SYMBOL_LOW;
        return text(symbol).append(meters(difference));
    }

    private static Component meters(long distance) {
        return translatable("waypoint.navigation.unit.meter", text(distance));
    }
}
