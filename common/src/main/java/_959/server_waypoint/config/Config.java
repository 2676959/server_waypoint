package _959.server_waypoint.config;

public class Config {
    CommandPermission CommandPermission = new CommandPermission();
    Features Features = new Features();

    public CommandPermission CommandPermission() {
        return this.CommandPermission;
    }

    public Features Features() {
        return this.Features;
    }

    @Override
    public String toString() {
        return "{CommandPermission=" + CommandPermission + ", Features=" + Features + "}";
    }
}
