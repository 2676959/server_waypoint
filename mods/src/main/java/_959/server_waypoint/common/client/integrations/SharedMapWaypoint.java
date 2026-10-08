package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.WaypointPos;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/** The native chat formats use dimension coordinates, not VoxelMap's scaled storage coordinates. */
public record SharedMapWaypoint(
        String name, String initials, int x, int y, int z, String dimensionName,
        int rgb, int yaw, @Nullable String icon
) {
    /** Encoded ownership is checked by identity even if the shared coordinates are outdated. */
    public boolean isOnServer(WaypointFilesManagerCore files) {
        var file = files.getWaypointFileManager(this.dimensionName);
        if (file == null) {
            return false;
        }
        var encoded = SyncedWaypointName.parse(this.name);
        if (encoded != null) {
            var list = file.getWaypointListByName(encoded.listName());
            return list != null && list.getWaypointByName(encoded.waypointName()) != null;
        }
        String decoded = SyncedWaypointName.parseSyncedName(this.name);
        String actualName = decoded == null ? this.name : decoded;
        for (var list : file.getWaypointLists()) {
            var saved = list.getWaypointByName(actualName);
            if (saved != null && (decoded != null || saved.pos().equals(new WaypointPos(this.x, this.y, this.z)))) {
                return true;
            }
        }
        return false;
    }

    private static final Pattern VOXEL = Pattern.compile("\\[([^\\[\\]]+)\\]");
    private static final Pattern DIMENSION = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final int[] XAERO_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF0000, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static @Nullable SharedMapWaypoint parse(String message, String currentDimension, int fallbackY) {
        if (message == null || currentDimension == null || !DIMENSION.matcher(currentDimension).matches()) {
            return null;
        }
        try {
            if (message.startsWith("xaero-waypoint:")) {
                return parseXaero(message, currentDimension, fallbackY);
            }
            Matcher matcher = VOXEL.matcher(message);
            while (matcher.find()) {
                SharedMapWaypoint waypoint = parseVoxel(matcher.group(1), currentDimension, fallbackY);
                if (waypoint != null) {
                    return waypoint;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Ordinary chat and malformed shares must not disrupt chat transmission.
        }
        return null;
    }

    private static @Nullable SharedMapWaypoint parseVoxel(String text, String currentDimension, int fallbackY) {
        Map<String, String> fields = new HashMap<>();
        for (String field : text.split(",")) {
            int separator = field.indexOf(':');
            if (separator < 0) {
                return null;
            }
            String key = field.substring(0, separator).strip().toLowerCase(Locale.ROOT);
            if (fields.putIfAbsent(key, field.substring(separator + 1).strip()) != null) {
                return null;
            }
        }
        if (!fields.containsKey("x") || !fields.containsKey("z")) {
            return null;
        }
        String dimension = fields.getOrDefault("dim", fields.getOrDefault("dimension", currentDimension));
        if (!dimension.contains(":")) {
            dimension = "minecraft:" + dimension;
        }
        if (!DIMENSION.matcher(dimension).matches()) {
            return null;
        }
        String name = fields.getOrDefault("name", "").replace("~comma~", ",").replace("~colon~", ":")
                .replace('﹐', ',').replace('⟦', '[').replace('⟧', ']');
        String icon = fields.getOrDefault("icon", fields.getOrDefault("suffix", ""));
        int rgb = fields.containsKey("color") ? Integer.decode(fields.get("color")) & 0xFFFFFF : 0x00FF00;
        return new SharedMapWaypoint(name, "", Integer.parseInt(fields.get("x")),
                Integer.parseInt(fields.getOrDefault("y", Integer.toString(fallbackY))),
                Integer.parseInt(fields.get("z")), dimension, rgb, 0,
                icon.isEmpty() ? null : "voxelmap:" + icon);
    }

    private static @Nullable SharedMapWaypoint parseXaero(String message, String currentDimension, int fallbackY) {
        String[] fields = message.split(":", -1);
        if (fields.length != 10 || fields[9].equals("External")) {
            return null;
        }
        String destination = fields[9];
        // These pinned Xaero builds include their waypoint world node in the shared destination.
        //? if <=1.21.3 {
        /*if (destination.endsWith("-waypoints")) {
            destination = destination.substring(0, destination.length() - "-waypoints".length());
        }
        *///?}
        String dimension = currentDimension;
        if (destination.startsWith("Internal-")) {
            String path = destination.substring("Internal-".length());
            dimension = restoreXaero(path);
            if (dimension.startsWith("dim%")) {
                dimension = dimension.substring(4).replace('$', ':').replace('%', '/').replace(',', '.');
            }
            dimension = switch (dimension) {
                case "0", "overworld" -> "minecraft:overworld";
                case "-1", "the_nether" -> "minecraft:the_nether";
                case "1", "the_end" -> "minecraft:the_end";
                default -> dimension;
            };
        } else if (!destination.equals("Internal")) {
            return null;
        }
        int color = Integer.parseInt(fields[6]);
        if (!DIMENSION.matcher(dimension).matches() || color < 0 || color >= XAERO_COLORS.length
                || !(fields[7].equals("true") || fields[7].equals("false"))) {
            return null;
        }
        return new SharedMapWaypoint(restoreXaero(fields[1]), restoreXaero(fields[2]),
                Integer.parseInt(fields[3]), fields[4].equals("~") ? fallbackY : Integer.parseInt(fields[4]),
                Integer.parseInt(fields[5]), dimension, XAERO_COLORS[color],
                fields[7].equals("true") ? Integer.parseInt(fields[8]) : 0, null);
    }

    private static String restoreXaero(String value) {
        return value.replace("^ast^", "*").replace("-", "_").replace("^min^", "-")
                .replace("^col^", ":");
    }
}
