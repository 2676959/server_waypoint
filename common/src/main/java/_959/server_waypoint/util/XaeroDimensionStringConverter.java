package _959.server_waypoint.util;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class XaeroDimensionStringConverter {
    public static String toVanilla(String xaeroDimString) {
        return switch (xaeroDimString) {
            case "overworld" -> MINECRAFT_OVERWORLD;
            case "the-nether" -> MINECRAFT_THE_NETHER;
            case "the-end" -> MINECRAFT_THE_END;
            default -> xaeroDimString.substring(4).replace("$", ":").replace("%", "/").replaceAll("-", "_");
        };
    }
}
