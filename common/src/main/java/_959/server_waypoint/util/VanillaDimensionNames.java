package _959.server_waypoint.util;

public abstract class VanillaDimensionNames {
    public static final String MINECRAFT_OVERWORLD = "minecraft:overworld";
    public static final String MINECRAFT_THE_NETHER = "minecraft:the_nether";
    public static final String MINECRAFT_THE_END = "minecraft:the_end";

    /** Returns 0 for overworld, 1 for the_nether, 2 for the_end, 3 otherwise. */
    public static int vanillaOrdinal(String name) {
        return switch (name) {
            case MINECRAFT_OVERWORLD -> 0;
            case MINECRAFT_THE_NETHER -> 1;
            case MINECRAFT_THE_END -> 2;
            default -> 3;
        };
    }
}