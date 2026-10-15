package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListControls;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Paging;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static _959.server_waypoint.util.ColorUtils.VANILLA_COLORS;
import static _959.server_waypoint.util.ColorUtils.VANILLA_COLOR_CODES;
import static _959.server_waypoint.util.ColorUtils.VANILLA_COLOR_NAMES;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;
import static _959.server_waypoint.util.StringCommandBuilder.detailsWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** The colour, facing and add pickers (spec 9). */
public final class PickerScreens {
    private static final int[] FACINGS = {0, 90, 180, -90};
    private static final String COLOR_SWATCH = "█";

    private PickerScreens() {
    }

    /** set color without a value */
    public static Component color(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        String edit = editWaypointCmd(dimension, list.name(), waypoint.name(), "set color");
        ChatLines lines = new ChatLines().add(title("wp.details.color", dims, dimension, list, waypoint,
                colorValue(waypoint.rgb())));
        if (viewer.plainText()) {
            return lines.line(text("  "), translatable("wp.picker.color.values", GRAY,
                    text(String.join(", ", VANILLA_COLOR_NAMES)))).buildScreen();
        }
        List<Component> swatches = new ArrayList<>();
        for (int index = 0; index < VANILLA_COLORS.length; index++) {
            swatches.add(Chat.link(viewer, text(COLOR_SWATCH), TextColor.color(VANILLA_COLORS[index]),
                    Click.run(edit + " " + VANILLA_COLOR_NAMES[index]),
                    Tooltip.of("wp.color." + VANILLA_COLOR_NAMES[index]).line(text(VANILLA_COLOR_CODES[index]))));
        }
        lines.add(Chat.spaced(swatches));
        lines.add(Chat.join(
                Chat.link(viewer, translatable("wp.picker.random"), AQUA, Click.run(edit + " random"),
                        Tooltip.of("wp.picker.random.tooltip")),
                Chat.link(viewer, Chat.concat(translatable("wp.picker.custom"), text(Chat.ELLIPSIS)), YELLOW,
                        Click.suggest(edit + " " + rgbToHexCode(waypoint.rgb(), false)),
                        Tooltip.of("wp.picker.custom.color").hint("wp.hint.type_hex")),
                back(viewer, dimension, list, waypoint)));
        return lines.buildScreen();
    }

    /** set yaw without a value */
    public static Component facing(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        Viewer viewer = dims.viewer();
        String edit = editWaypointCmd(dimension, list.name(), waypoint.name(), "set yaw");
        ChatLines lines = new ChatLines().add(title("wp.picker.facing", dims, dimension, list, waypoint,
                yawValue(waypoint.yaw())));
        if (viewer.plainText()) {
            return lines.line(text("  "), translatable("wp.picker.yaw.values", GRAY)).buildScreen();
        }
        List<Component> facings = new ArrayList<>();
        for (int yaw : FACINGS) {
            String facing = Objects.requireNonNull(facing(yaw));
            facings.add(Chat.link(viewer, Chat.concat(translatable("wp.facing." + facing + ".title"), text(" " + yaw + "°")),
                    AQUA, Click.run(edit + " " + yaw), Tooltip.of("wp.picker.face", translatable("wp.facing." + facing))));
        }
        lines.add(Chat.join(facings));
        int yours = Math.floorMod(Math.round(viewer.yaw()) + 180, 360) - 180;
        lines.add(Chat.join(
                Chat.link(viewer, Chat.concat(translatable("wp.picker.yours"), text(" " + yours + "°")), GREEN,
                        Click.run(edit + " " + yours), Tooltip.of("wp.picker.yours.tooltip")),
                Chat.link(viewer, Chat.concat(translatable("wp.picker.custom"), text(Chat.ELLIPSIS)), YELLOW,
                        Click.suggest(edit + " " + waypoint.yaw()),
                        Tooltip.of("wp.picker.custom.facing").hint("wp.hint.type_degrees")),
                back(viewer, dimension, list, waypoint)));
        return lines.buildScreen();
    }

    /** /wp add without arguments: this dimension's lists to add a waypoint here to. */
    public static Component add(DimensionStyle dims, String dimension, List<WaypointList> lists, int page, int pageLimit) {
        Viewer viewer = dims.viewer();
        WaypointPos here = Objects.requireNonNullElse(viewer.position(), new WaypointPos(0, 0, 0));
        ChatLines lines = new ChatLines().line(translatable("wp.picker.add", GOLD), text(" "),
                translatable("wp.picker.add.at", GRAY, text(DimensionStyle.coordinates(here))));
        Component newList = ListActions.newList(dims, dimension);
        Component back = Chat.control(viewer, translatable("wp.action.back"), GRAY, Click.run("/wp"),
                Tooltip.of("wp.picker.back.menu"));
        if (lists.isEmpty()) {
            return lines.line(translatable("wp.dimension.no_lists.sentence", GRAY), newList == null ? null : text(" "),
                    newList).buildScreen();
        }
        List<Component> links = lists.stream().map(list -> Chat.link(viewer,
                WaypointRefs.label(viewer, list.displayName(), list.name()), GREEN, ListActions.addClick(viewer, dimension, list),
                Tooltip.of("wp.picker.add.to", WaypointRefs.label(list.displayName(), list.name())).hint("wp.hint.type_name")))
                .toList();
        if (lists.size() <= pageLimit) {
            lines.line(translatable("wp.picker.into", GRAY), text("  "), Chat.join(links));
            return lines.add(Chat.join(newList, back)).buildScreen();
        }
        ListTarget target = new ListTarget("/wp add");
        ListQuery query = ListQuery.DEFAULT.withPage(page);
        List<List<Component>> pages = Paging.bySize(links, pageLimit + 5);
        if (page > pages.size()) {
            return ListControls.pageNotFound(viewer, target, query, pages.size());
        }
        lines.add(translatable("wp.picker.into", GRAY));
        pages.get(page - 1).forEach(link -> lines.line(text("  "), link));
        lines.add(ListControls.more(viewer, "list", Paging.after(pages, page), target.command(query.withPage(page + 1))));
        ListControls.controls(lines, ListControls.pager(viewer, target, query, pages.size(),
                ListControls.pageDetail(pageLimit + 5, Chat.count("wp.count.list", lists.size()))), Chat.join(newList, back));
        return lines.buildScreen();
    }

    /** "0° (south)": the yaw, and the direction when it is one of the four. */
    public static Component yawValue(int yaw) {
        String facing = facing(yaw);
        Component degrees = text(yaw + "°");
        return facing == null ? degrees : translatable("wp.yaw.facing", degrees, translatable("wp.facing." + facing));
    }

    /** "█ #FFAA00": a swatch in the colour and its hex code in white. */
    public static Component colorValue(int rgb) {
        return Chat.concat(text(COLOR_SWATCH, TextColor.color(rgb)), text(" " + rgbToHexCode(rgb, true), WHITE));
    }

    /** Back to the waypoint's details. */
    public static @Nullable Component back(Viewer viewer, String dimension, WaypointList list, SimpleWaypoint waypoint) {
        return Chat.control(viewer, translatable("wp.action.back"), GRAY,
                Click.run(detailsWaypointCmd(dimension, list.name(), waypoint.name())), Tooltip.of("wp.picker.back.details"));
    }

    private static @Nullable String facing(int yaw) {
        return switch (Math.floorMod(yaw, 360)) {
            case 0 -> "south";
            case 90 -> "west";
            case 180 -> "north";
            case 270 -> "east";
            default -> null;
        };
    }

    /** "Color · [MH] Main Home   now █ #FFAA00" */
    private static Component title(String titleKey, DimensionStyle dims, String dimension, WaypointList list,
                                   SimpleWaypoint waypoint, Component value) {
        return Chat.concat(translatable(titleKey, GOLD), Chat.SEPARATOR,
                WaypointRefs.title(dims, dimension, list, waypoint, WHITE), text("   "),
                translatable("wp.picker.now", GRAY, value));
    }
}
