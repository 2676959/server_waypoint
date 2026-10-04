package _959.server_waypoint.crossserver;

/** Registry-independent item identifier validation shared by backends and catalog codecs. */
public final class ServerIcon {
    public static final String DEFAULT = "minecraft:beacon";
    private ServerIcon() { }

    public static String validate(String identifier) {
        if (identifier == null || identifier.length() > 256
                || !identifier.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Invalid server icon item identifier");
        }
        return identifier;
    }
}
