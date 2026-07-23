package _959.server_waypoint.navigation;

/**
 * Direction and distance values shared by every method update for one player.
 * Directional values are {@link Double#NaN} when the player is outside the
 * target dimension.
 */
public record NavigationSnapshot(
        boolean inTargetDimension,
        double targetYaw,
        double signedTurnAngle,
        double horizontalDistance,
        double verticalDifference,
        float headingProgress
) {
    public boolean waypointBehindPlayer() {
        return this.inTargetDimension && NavigationMath.isBehind(this.signedTurnAngle);
    }

    public static NavigationSnapshot wrongDimension() {
        return new NavigationSnapshot(
                false,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                0.0F
        );
    }
}
