package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;

import java.util.Objects;

/**
 * Platform-neutral calculations used by live navigation displays.
 */
public final class NavigationMath {
    private NavigationMath() {
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0D;
        if (wrapped >= 180.0D) {
            wrapped -= 360.0D;
        }
        if (wrapped < -180.0D) {
            wrapped += 360.0D;
        }
        return wrapped;
    }

    public static float facingProgress(double signedTurnAngle) {
        double progress = 1.0D - Math.abs(wrapDegrees(signedTurnAngle)) / 180.0D;
        return (float) Math.max(0.0D, Math.min(1.0D, progress));
    }

    public static NavigationSnapshot snapshot(
            String playerDimension,
            double playerX,
            double playerY,
            double playerZ,
            double playerYaw,
            NavigationTarget target
    ) {
        Objects.requireNonNull(playerDimension, "playerDimension");
        Objects.requireNonNull(target, "target");
        if (!target.dimensionName().equals(playerDimension)) {
            return NavigationSnapshot.wrongDimension();
        }

        WaypointPos targetPosition = target.position();
        double deltaX = targetPosition.X() - playerX;
        double deltaZ = targetPosition.Z() - playerZ;
        double targetYaw = wrapDegrees(Math.toDegrees(Math.atan2(-deltaX, deltaZ)));
        double signedTurnAngle = wrapDegrees(targetYaw - playerYaw);
        double horizontalDistance = Math.hypot(deltaX, deltaZ);
        double verticalDifference = targetPosition.y() - playerY;
        return new NavigationSnapshot(
                true,
                targetYaw,
                signedTurnAngle,
                horizontalDistance,
                verticalDifference,
                facingProgress(signedTurnAngle)
        );
    }
}
