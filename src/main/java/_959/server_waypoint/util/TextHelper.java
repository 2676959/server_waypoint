package _959.server_waypoint.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public class TextHelper {
    public static Text END_LINE = Text.literal("\n");

    public static MutableText text(String text) {
        return Text.literal(text);
    }
}
