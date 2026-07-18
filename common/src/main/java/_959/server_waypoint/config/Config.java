package _959.server_waypoint.config;

import java.util.concurrent.ThreadLocalRandom;

public class Config {
    public static final int MIN_PAGE_LIMIT = 1;
    public static final int DEFAULT_PAGE_LIMIT = 10;
    public static final int MAX_PAGE_LIMIT = 100;

    int serverId = ThreadLocalRandom.current().nextInt();
    int defaultPageLimit = DEFAULT_PAGE_LIMIT;
    CommandPermission CommandPermission = new CommandPermission();
    Features Features = new Features();

    public Config() {
    }

    public CommandPermission CommandPermission() {
        return this.CommandPermission;
    }

    public Features Features() {
        return this.Features;
    }

    public int defaultPageLimit() {
        return Math.max(MIN_PAGE_LIMIT, Math.min(MAX_PAGE_LIMIT, this.defaultPageLimit));
    }

    public int getServerId() {
        return serverId;
    }

    @Override
    public String toString() {
        return "Config{serverId=" + serverId + ", defaultPageLimit=" + defaultPageLimit() +
                ", CommandPermission=" + CommandPermission + ", Features=" + Features + "}";
    }
}
