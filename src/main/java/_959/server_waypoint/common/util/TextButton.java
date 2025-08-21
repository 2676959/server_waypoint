package _959.server_waypoint.common.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

import static _959.server_waypoint.util.CommandGenerator.*;
import static _959.server_waypoint.common.util.TextHelper.text;
import static _959.server_waypoint.common.util.TextHelper.ClickEventHelper.SuggestCommand;
import static _959.server_waypoint.common.util.TextHelper.HoverEventHelper.ShowText;

public class TextButton {
    public static MutableText replaceButton(String dimString, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.AQUA)
                .withClickEvent(SuggestCommand.apply(editCmd(dimString, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to replace")));
        return text("[⇄]").setStyle(btnStyle);
    }

    public static MutableText restoreButton(String dimString, String listName, SimpleWaypoint waypoint) {

        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.LIGHT_PURPLE)
                .withClickEvent(SuggestCommand.apply(addCmd(dimString, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to restore")));
        return text("[↓]").setStyle(btnStyle);
    }

    public static MutableText removeButton(String dimString, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.RED)
                .withClickEvent(SuggestCommand.apply(removeCmd(dimString, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to remove")));
        return text("[❌]").setStyle(btnStyle);
    }

    public static MutableText editButton(String dimString, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.YELLOW)
                .withClickEvent(SuggestCommand.apply(editCmd(dimString, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("edit")));
        return text("[📝]").setStyle(btnStyle);
    }

    public static MutableText addButton(String dimString, String listName, SimpleWaypoint waypoint) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(SuggestCommand.apply(addCmd(dimString, listName, waypoint)))
                .withHoverEvent(ShowText.apply(text("click to add waypoint")));
        return text("[+]").setStyle(btnStyle);
    }

    public static MutableText addListButton(String dimString, String listName) {
        Style btnStyle = Style.EMPTY
                .withBold(true)
                .withColor(Formatting.GREEN)
                .withClickEvent(SuggestCommand.apply(addListCmd(dimString, listName)))
                .withHoverEvent(ShowText.apply(text("click to add waypoint list")));
        return text("[+]").setStyle(btnStyle);
    }
}
