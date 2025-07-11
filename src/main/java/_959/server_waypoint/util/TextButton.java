package _959.server_waypoint.util;

import _959.server_waypoint.server.waypoint.SimpleWaypoint;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import static _959.server_waypoint.util.CommandGenerator.*;
import static _959.server_waypoint.util.TextHelper.text;
import static _959.server_waypoint.util.TextHelper.ClickEventHelper.SuggestCommand;
import static _959.server_waypoint.util.TextHelper.HoverEventHelper.ShowText;

public class TextButton {
    public static MutableText replaceButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.AQUA)
                .withClickEvent(SuggestCommand.apply(editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to replace")));
        return text("[⇄]").setStyle(btnStyle);
    }

    public static MutableText restoreButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.LIGHT_PURPLE)
                .withClickEvent(SuggestCommand.apply(addCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to restore")));
        return text("[↓]").setStyle(btnStyle);
    }

    public static MutableText removeButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.RED)
                .withClickEvent(SuggestCommand.apply(removeCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to remove")));
        return text("[❌]").setStyle(btnStyle);
    }

    public static MutableText editButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.YELLOW)
                .withClickEvent(SuggestCommand.apply(editCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("edit")));
        return text("[📝]").setStyle(btnStyle);
    }

    public static MutableText addButton(RegistryKey<World> dimKey, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(SuggestCommand.apply(addCmd(dimKey, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to add waypoint")));
        return text("[+]").setStyle(btnStyle);
    }

    public static MutableText addListButton(RegistryKey<World> dimKey, String listName) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(SuggestCommand.apply(addListCmd(dimKey, listName)))
                .withHoverEvent(ShowText.apply(text("click to add waypoint list")));
        return text("[+]").setStyle(btnStyle);
    }
}
