package _959.server_waypoint.config;

public class Config {
    CommandPermission CommandPermission = new CommandPermission();
    AddWaypointFromChatSharing AddWaypointFromChatSharing = new AddWaypointFromChatSharing();

    public CommandPermission CommandPermission() {
        return CommandPermission;
    }

    public AddWaypointFromChatSharing AddWaypointFromChatSharing() {
        return AddWaypointFromChatSharing;
    }

    @Override
    public String toString() {
        return "Config{" +
                "CommandPermission=" + CommandPermission +
                ", AddWaypointFromChatSharing=" + AddWaypointFromChatSharing +
                '}';
    }
}