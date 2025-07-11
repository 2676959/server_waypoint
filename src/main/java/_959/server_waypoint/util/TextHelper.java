package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.function.Function;

import static _959.server_waypoint.util.BlockPosConverter.netherToOverWorld;
import static _959.server_waypoint.util.BlockPosConverter.overWorldToNether;

public class TextHelper {
    public static Text END_LINE = Text.literal("\n");

    public static int formattingToColorIndex(Formatting formatting) {
        return (formatting.getColorIndex() < 0) ? 15 : formatting.getColorIndex();
    }

    public static String colorIndexToName(int colorIdx) {
        Formatting color = Formatting.byColorIndex(colorIdx);
        return color != null ? color.asString() : "white";
    }

    public static Text waypointInfoText(RegistryKey<World> dimKey, SimpleWaypoint waypoint) {
        BlockPos pos = waypoint.pos();
        MutableText info = text(pos.toShortString());
        if (dimKey == World.OVERWORLD) {
            info.append(END_LINE);
            info.append(text(overWorldToNether(pos).toShortString()).setStyle(Style.EMPTY.withColor(Formatting.RED).withItalic(true)));
        } else if (dimKey == World.NETHER) {
            info.append(END_LINE);
            info.append(text(netherToOverWorld(pos).toShortString()).setStyle(Style.EMPTY.withColor(Formatting.GREEN).withItalic(true)));
        }
        return info;
    }

    public static MutableText text(String text) {
        return Text.literal(text);
    }

    public static class DimensionColorHelper {
        public static Formatting getDimensionColor(RegistryKey<World> dimKey) {
            if (dimKey == World.OVERWORLD) {
                return Formatting.GREEN;
            } else if (dimKey == World.NETHER) {
                return Formatting.RED;
            } else if (dimKey == World.END) {
                return Formatting.LIGHT_PURPLE;
            } else {
                return Formatting.YELLOW;
            }
        }
    }

    public static class ClickEventHelper {
        //#if MC>=12105
        public static final Function<String, ClickEvent> SuggestCommand = ClickEvent.SuggestCommand::new;
        public static final Function<String, ClickEvent> RunCommand = ClickEvent.RunCommand::new;
        //#else
        //$$ public static final Function<String, ClickEvent> SuggestCommand = (command) -> new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command);
        //$$ public static final Function<String, ClickEvent> RunCommand = (command) -> new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        //#endif
    }

    public static class HoverEventHelper {
        //#if MC>=12105
        public static Function<Text, HoverEvent> ShowText = HoverEvent.ShowText::new;
        //#else
        //$$ public static Function<Text, HoverEvent> ShowText = (text) -> new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        //#endif
    }
}
