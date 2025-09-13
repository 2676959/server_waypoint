package _959.server_waypoint.common.util;

import net.minecraft.util.Formatting;
import static _959.server_waypoint.util.VanillaDimensionNames.*;

public class TextHelper {
    public static Formatting getDimensionColor(String dimString) {
        return switch (dimString) {
            case MINECRAFT_OVERWORLD -> Formatting.GREEN;
            case MINECRAFT_THE_NETHER -> Formatting.RED;
            case MINECRAFT_THE_END -> Formatting.LIGHT_PURPLE;
            default -> Formatting.YELLOW;
        };
    }
}
