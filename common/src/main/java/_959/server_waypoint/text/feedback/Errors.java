package _959.server_waypoint.text.feedback;

import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.Tooltip;
import net.kyori.adventure.text.Component;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;

/** One red ✘ line, plus the link that helps recover where one helps (spec 13). */
public final class Errors {
    private Errors() {
    }

    /** ✘ No dimension called x. Dimensions */
    public static Component noDimension(DimensionStyle dims, String dimension) {
        return Chat.error(translatable("wp.error.no_dimension", text(dimension)),
                Chat.control(dims.viewer(), translatable("wp.dimensions.title"), AQUA, Click.run("/wp list dimensions"),
                        Tooltip.of("wp.dimensions.choose")));
    }

    /** ✘ No list called Farm in Overworld. Browse lists */
    public static Component noList(DimensionStyle dims, String dimension, String list) {
        return Chat.error(translatable("wp.error.no_list", text(list), dims.name(dimension)),
                Chat.control(dims.viewer(), translatable("wp.error.browse_lists"), AQUA, Click.run("/wp list " + dimension),
                        Tooltip.of("wp.list.every_list_in", dims.name(dimension))));
    }
}
