package _959.server_waypoint.text.chat;

import _959.server_waypoint.core.waypoint.WaypointSorting.SortMode;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.util.StringCommandBuilder.escapeArgument;
import static _959.server_waypoint.util.StringCommandBuilder.escapeListName;

/**
 * The command a list screen points at, without options. Options follow in the order the grammar
 * accepts: search, sort and order, limit, view, then page, so a command can end in "page ".
 */
public record ListTarget(String base) {
    public static ListTarget dimension(String dimension) {
        return new ListTarget("/wp list " + dimension);
    }

    public static ListTarget list(String dimension, String list) {
        return new ListTarget("/wp list " + dimension + " " + escapeListName(list));
    }

    public static ListTarget allDimensions() {
        return new ListTarget("/wp list all");
    }

    public static ListTarget dimensions() {
        return new ListTarget("/wp list dimensions");
    }

    /** /wp remote list with as many of server, dimension and list as are given, in that order. */
    public static ListTarget remote(@Nullable String server, @Nullable String dimension, @Nullable String list) {
        StringBuilder base = new StringBuilder("/wp remote list");
        for (String identity : new String[]{server, dimension, list}) {
            if (identity == null) {
                break;
            }
            base.append(' ').append(escapeListName(identity));
        }
        return new ListTarget(base.toString());
    }

    public String command(ListQuery query) {
        StringBuilder command = new StringBuilder(this.base);
        if (query.searching()) {
            command.append(" search ").append(escapeArgument(query.search()));
        }
        if (query.sort() != SortMode.DEFAULT) {
            command.append(" sort ").append(query.sort().name().toLowerCase(Locale.ROOT));
            if (query.descending()) {
                command.append(" order descending");
            }
        }
        if (query.limit() != null) {
            command.append(" limit ").append(query.limit());
        }
        if (query.view() != ListView.DEFAULT) {
            command.append(" view ").append(query.view().id());
        }
        if (query.page() > 1) {
            command.append(" page ").append(query.page());
        }
        return command.toString();
    }

    /** The command up to "page ", for the player to type a page number. */
    public String pagePrompt(ListQuery query) {
        return this.command(query.withPage(1)) + " page ";
    }

    /** The target followed by "search ", for the player to type what to look for. */
    public String searchPrompt() {
        return this.base + " search ";
    }
}
