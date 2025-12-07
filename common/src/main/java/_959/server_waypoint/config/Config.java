package _959.server_waypoint.config;

import java.util.concurrent.ThreadLocalRandom;

public class Config {
    int serverId = ThreadLocalRandom.current().nextInt();
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

    public int getServerId() {
        return serverId;
    }

    @Override
    public String toString() {
        return "Config{serverId=" + serverId + ", CommandPermission=" + CommandPermission + ", Features=" + Features + "}";
    }
}
