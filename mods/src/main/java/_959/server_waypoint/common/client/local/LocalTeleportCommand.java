package _959.server_waypoint.common.client.local;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.util.NamespacedId;
import java.util.Map;

/** The profile template is a single ordinary command, with named literal substitutions only. */
public final class LocalTeleportCommand {
    private LocalTeleportCommand() { }

    public static String expand(String template, String dimensionName, SimpleWaypoint waypoint) {
        if (template == null || template.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Command contains control characters");
        }
        NamespacedId.parse(dimensionName);
        String command = template.trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || command.startsWith("/")) throw new IllegalArgumentException("Empty or invalid command");
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        Map<String, String> values = Map.of("dimension", dimensionName, "x", Integer.toString(snapshot.x()),
                "y", Integer.toString(snapshot.y()), "z", Integer.toString(snapshot.z()), "yaw", Integer.toString(snapshot.yaw()));
        StringBuilder expanded = new StringBuilder();
        for (int i = 0; i < command.length(); i++) {
            char character = command.charAt(i);
            if (character == '}') throw new IllegalArgumentException("Unbalanced command token");
            if (character != '{') { expanded.append(character); continue; }
            int end = command.indexOf('}', i + 1);
            if (end < 0) throw new IllegalArgumentException("Unbalanced command token");
            String replacement = values.get(command.substring(i + 1, end));
            if (replacement == null) throw new IllegalArgumentException("Unknown command token");
            expanded.append(replacement);
            i = end;
        }
        return expanded.toString();
    }
}
