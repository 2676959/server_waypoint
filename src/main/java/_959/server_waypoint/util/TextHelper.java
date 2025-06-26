package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static _959.server_waypoint.util.CommandGenerator.*;
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

    public static Text replaceButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("click to replace")));
        return text("[⇄]").setStyle(btnStyle);
    }

    public static Text restoreButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.LIGHT_PURPLE)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, addCmd(dimKey, listName, waypoint)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("click to restore")));
        return text("[↓]").setStyle(btnStyle);
    }

    public static Text removeButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, removeCmd(dimKey, listName, waypoint)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("click to remove")));
        return text("[❌]").setStyle(btnStyle);
    }

    public static Text editButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, text("edit")));
        return text("[📝]").setStyle(btnStyle);
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
}
