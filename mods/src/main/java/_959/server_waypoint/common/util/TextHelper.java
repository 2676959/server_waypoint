package _959.server_waypoint.common.util;

import static _959.server_waypoint.util.VanillaDimensionNames.*;

import net.minecraft.ChatFormatting;

public class TextHelper {
    public static ChatFormatting getDimensionColor(String dimString) {
        return switch (dimString) {
            case MINECRAFT_OVERWORLD -> ChatFormatting.GREEN;
            case MINECRAFT_THE_NETHER -> ChatFormatting.RED;
            case MINECRAFT_THE_END -> ChatFormatting.LIGHT_PURPLE;
            default -> ChatFormatting.YELLOW;
        };
    }
}
