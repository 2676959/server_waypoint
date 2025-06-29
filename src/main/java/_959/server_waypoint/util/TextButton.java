package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import static _959.server_waypoint.util.CommandGenerator.*;
import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.TextHelper.ClickEventHelper.SuggestCommand;
import static _959.server_waypoint.util.TextHelper.HoverEventHelper.ShowText;

public class TextButton {
    public static Text replaceButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.AQUA)
                .withClickEvent(SuggestCommand.apply(editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to replace")));
        return TextHelper.text("[⇄]").setStyle(btnStyle);
    }

    public static Text restoreButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.LIGHT_PURPLE)
                .withClickEvent(SuggestCommand.apply(addCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to restore")));
        return TextHelper.text("[↓]").setStyle(btnStyle);
    }

    public static Text removeButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.RED)
                .withClickEvent(SuggestCommand.apply(removeCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to remove")));
        return TextHelper.text("[❌]").setStyle(btnStyle);
    }

    public static Text editButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(SuggestCommand.apply(editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("edit")));
        return TextHelper.text("[📝]").setStyle(btnStyle);
    }
}
