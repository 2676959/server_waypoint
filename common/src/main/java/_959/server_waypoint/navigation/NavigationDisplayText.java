package _959.server_waypoint.navigation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * Builds the shared actionbar and bossbar text without retaining platform
 * player or display objects.
 */
public final class NavigationDisplayText {
    private static final double STRAIGHT_AHEAD_THRESHOLD_DEGREES = 0.5D;

    private NavigationDisplayText() {
    }

    public static Component build(NavigationSession session, NavigationSnapshot snapshot) {
        NavigationTarget target = session.target();
        Component targetName = text(target.waypointName(), TextColor.color(target.rgb()));
        if (!snapshot.inTargetDimension()) {
            return translatable(
                    "waypoint.navigation.display.wrong_dimension",
                    targetName,
                    translatable(
                            "waypoint.navigation.wrong_dimension",
                            text(target.dimensionName())
                    )
            );
        }

        long distance = Math.round(snapshot.horizontalDistance());
        double signedTurn = snapshot.signedTurnAngle();
        long turnAngle = Math.round(Math.abs(signedTurn));
        if (Math.abs(signedTurn) < STRAIGHT_AHEAD_THRESHOLD_DEGREES) {
            return translatable(
                    "waypoint.navigation.display.ahead",
                    targetName,
                    text(distance),
                    translatable("waypoint.navigation.turn.ahead")
            );
        }

        String turnKey = signedTurn < 0.0D
                ? "waypoint.navigation.turn.left"
                : "waypoint.navigation.turn.right";
        return translatable(
                "waypoint.navigation.display",
                targetName,
                text(distance),
                translatable(turnKey),
                text(turnAngle)
        );
    }
}
