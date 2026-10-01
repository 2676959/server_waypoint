package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.EditResultStatus;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static _959.server_waypoint.util.StringCommandBuilder.detailsListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;

/** One red ✘ line, plus the link that helps recover where one helps (spec 13). */
public final class Errors {
    private Errors() {
    }

    /** ✘ and a translated sentence, without a recovery link. */
    public static Component of(String key, ComponentLike... arguments) {
        return Chat.error(translatable(key, arguments));
    }

    /** ✘ Only players can do that. */
    public static Component playerOnly() {
        return of("wp.error.player_only");
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

    /** ✘ Mars has no lists yet. New list */
    public static Component noLists(DimensionStyle dims, String dimension) {
        return Chat.error(translatable("wp.error.no_lists", dims.name(dimension)), ListActions.newList(dims, dimension));
    }

    /** ✘ No waypoint called x in Farms. Open Farms */
    public static Component noWaypoint(DimensionStyle dims, String dimension, String list, String waypoint) {
        return Chat.error(translatable("wp.error.no_waypoint", text(waypoint), text(list)),
                Chat.control(dims.viewer(), translatable("wp.open", text(list)), AQUA,
                        Click.run(ListTarget.list(dimension, list).command(ListQuery.DEFAULT)),
                        Tooltip.of("wp.list.every_list_in", dims.name(dimension))));
    }

    /** ✘ Overworld already has a list called Farms. Open */
    public static Component listExists(DimensionStyle dims, String dimension, WaypointList list) {
        return Chat.error(translatable("wp.error.list_exists", WaypointRefs.label(list.displayName(), list.name()),
                        dims.name(dimension)),
                Chat.control(dims.viewer(), translatable("wp.action.open"), AQUA,
                        Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)),
                        WaypointRefs.listTooltip(list, "wp.hint.open")));
    }

    /** ✘ Farms already has a waypoint called Iron Farm. Details */
    public static Component waypointExists(DimensionStyle dims, String dimension, WaypointList list,
                                           SimpleWaypoint existing) {
        return Chat.error(translatable("wp.error.waypoint_exists",
                        WaypointRefs.label(existing.displayName(), existing.name()),
                        WaypointRefs.label(list.displayName(), list.name())),
                Chat.control(dims.viewer(), translatable("wp.action.details"), AQUA,
                        Click.run(detailsWaypointCmd(dimension, list.name(), existing.name())),
                        WaypointRefs.waypointTooltip(dims, dimension, existing, "wp.hint.details")));
    }

    /** ✘ Only empty lists can be removed. Open Farms */
    public static Component listNotEmpty(DimensionStyle dims, String dimension, WaypointList list) {
        return Chat.error(translatable("wp.error.list_not_empty"),
                Chat.control(dims.viewer(), translatable("wp.open", WaypointRefs.label(list.displayName(), list.name())),
                        AQUA, Click.run(ListTarget.list(dimension, list.name()).command(ListQuery.DEFAULT)),
                        WaypointRefs.listTooltip(list, "wp.hint.open")));
    }

    /** Why an edit of a waypoint (or of a list, when waypoint is null) failed. */
    public static Component edit(DimensionStyle dims, EditResultStatus status, String dimension, String list,
                                 @Nullable String waypoint) {
        return switch (status) {
            case DIMENSION_NOT_FOUND -> noDimension(dims, dimension);
            case LIST_NOT_FOUND -> noList(dims, dimension, list);
            case WAYPOINT_NOT_FOUND -> noWaypoint(dims, dimension, list, waypoint == null ? "" : waypoint);
            case STALE_REVISION -> Chat.error(translatable("wp.error.edit.stale_revision", text(waypoint == null ? list : waypoint)),
                    Chat.control(dims.viewer(), translatable("wp.error.reload_details"), AQUA,
                            Click.run(waypoint == null ? detailsListCmd(dimension, list) : detailsWaypointCmd(dimension, list, waypoint)),
                            null));
            case PERMISSION_DENIED -> of("wp.error.permission");
            case DUPLICATE_KEYWORD -> of("wp.error.keywords.duplicate");
            case ENCODING_FAILED -> of("wp.error.encoding");
            default -> of("wp.error.edit." + status.name().toLowerCase(Locale.ROOT));
        };
    }
}
