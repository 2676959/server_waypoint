package _959.server_waypoint.config;

/** Per-owner limits across all dimensions; zero disables the corresponding limit. */
public final class PrivateWaypointConfig {
    private int maxWaypointsPerPlayer = 1000;
    private int maxListsPerPlayer = 100;

    public PrivateWaypointConfig() {
    }

    public PrivateWaypointConfig(int maxWaypointsPerPlayer, int maxListsPerPlayer) {
        this.maxWaypointsPerPlayer = maxWaypointsPerPlayer;
        this.maxListsPerPlayer = maxListsPerPlayer;
        this.validate();
    }

    public int maxWaypointsPerPlayer() {
        return this.maxWaypointsPerPlayer;
    }

    public int maxListsPerPlayer() {
        return this.maxListsPerPlayer;
    }

    public void validate() {
        if (this.maxWaypointsPerPlayer < 0 || this.maxListsPerPlayer < 0) {
            throw new IllegalArgumentException("Private waypoint limits cannot be negative");
        }
    }
}
