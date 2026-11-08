package _959.server_waypoint.navigation;

import _959.server_waypoint.core.waypoint.WaypointPos;

/** Short horizontal forecast for the client-server-client trip and display interpolation. */
final class WorldMarkerMotion {
    private static final double MAX_LEAD_DISTANCE = 2.0D;
    private static final int MAX_LATENCY_MILLIS = 200;
    private WorldMarkerPosition previousEye;
    private long previousTick;
    private double velocityX;
    private double velocityZ;

    void sample(WorldMarkerPosition eye, long tick) {
        if (this.previousEye != null && tick == this.previousTick) {
            return;
        }
        this.velocityX = 0.0D;
        this.velocityZ = 0.0D;
        long elapsed = tick - this.previousTick;
        if (this.previousEye != null && elapsed > 0 && elapsed <= 3) {
            double dx = eye.x() - this.previousEye.x();
            double dy = eye.y() - this.previousEye.y();
            double dz = eye.z() - this.previousEye.z();
            // Discard discontinuities instead of interpreting a teleport as sprint velocity.
            double maximumStep = 2.0D * elapsed;
            if (dx * dx + dy * dy + dz * dz <= maximumStep * maximumStep) {
                this.velocityX = dx / elapsed;
                this.velocityZ = dz / elapsed;
            }
        }
        this.previousEye = eye;
        this.previousTick = tick;
    }

    WorldMarkerPosition project(WorldMarkerPosition eye, WaypointPos target, int latencyMillis, double radius) {
        // Server-observed player positions already paid the inbound trip; outbound delivery pays it again.
        double networkTicks = Math.min(MAX_LATENCY_MILLIS, Math.max(0, latencyMillis)) / 50.0D;
        double leadTicks = 1.0D + networkTicks; // One-tick display interpolation.
        double dx = this.velocityX * leadTicks;
        double dz = this.velocityZ * leadTicks;
        double distanceSquared = dx * dx + dz * dz;
        if (distanceSquared > MAX_LEAD_DISTANCE * MAX_LEAD_DISTANCE) {
            double factor = MAX_LEAD_DISTANCE / Math.sqrt(distanceSquared);
            dx *= factor;
            dz *= factor;
        }
        // Predict the observer, then project again: nearby markers still converge to the real waypoint.
        // Eye-height changes are kept current rather than extrapolating crouches or jumps.
        WorldMarkerPosition predictedEye = new WorldMarkerPosition(eye.x() + dx, eye.y(), eye.z() + dz);
        return WorldMarkerPosition.project(predictedEye, target, radius);
    }
}
