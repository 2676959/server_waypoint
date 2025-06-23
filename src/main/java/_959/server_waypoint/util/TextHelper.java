package _959.server_waypoint.util;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class TextHelper {
    public static Text END_LINE = Text.literal("\n");

    public static int formattingToColorIndex(Formatting formatting) {
        return (formatting.getColorIndex() < 0) ? 15 : formatting.getColorIndex();
    }

    public static MutableText text(String text) {
        return Text.literal(text);
    }
}
