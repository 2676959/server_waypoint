package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.edit.PatchField;
import _959.server_waypoint.core.edit.WaypointListPatch;
import _959.server_waypoint.core.edit.WaypointPatch;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.text.chat.Chat;
import _959.server_waypoint.text.chat.ChatLines;
import _959.server_waypoint.text.chat.Click;
import _959.server_waypoint.text.chat.DimensionStyle;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.ListTarget;
import _959.server_waypoint.text.chat.Tooltip;
import _959.server_waypoint.text.chat.Viewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.FormattedTextHelper.parse;
import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.StringCommandBuilder.downloadCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editListClearCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editListSetSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointClearCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointIconSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointPositionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.editWaypointSetSuggestionCmd;
import static _959.server_waypoint.util.StringCommandBuilder.navigateCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeCmd;
import static _959.server_waypoint.util.StringCommandBuilder.removeListCmd;
import static _959.server_waypoint.util.StringCommandBuilder.tpCmd;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.WHITE;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

/** Waypoint and list details (spec 8): a breadcrumb, one property per line and a row of buttons. */
public final class DetailsScreen {
    private DetailsScreen() {
    }

    /** /wp details waypoint, with the result of an edit on top. */
    public static Component waypoint(DimensionStyle dims, String dimension, WaypointList list, SimpleWaypoint waypoint,
                                     @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        String listId = list.name();
        String id = waypoint.name();
        String listCommand = ListTarget.list(dimension, listId).command(ListQuery.DEFAULT);
        Component name = WaypointRefs.label(waypoint.displayName(), id);
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(ListScreen.dimensionLink(dims, dimension), Chat.CRUMB,
                WaypointRefs.listLink(dims, list, WHITE, Click.run(listCommand), "wp.hint.open"),
                Chat.CRUMB, WaypointRefs.title(dims, dimension, list, waypoint, GOLD));
        lines.add(property(suggestEdit(viewer, "display_name",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "display-name", waypoint.displayName())),
                "display_name", name,
                waypoint.hasDisplayNameOverride()
                        ? clear(viewer, "display_name", editWaypointClearCmd(dimension, listId, id, "display-name"))
                        : null));
        lines.add(property(suggestEdit(viewer, "identifier",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "identifier", id)),
                "identifier", identifier(id), null));
        lines.add(property(suggestEdit(viewer, "initials",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "initials", waypoint.initials())),
                "initials", text(waypoint.initials()), null));
        lines.add(property(suggestEdit(viewer, "icon", editWaypointIconSuggestionCmd(dimension, listId, id, waypoint.icon())),
                "icon", waypoint.icon() == null ? none() : WaypointRefs.withIcon(viewer, waypoint, text(waypoint.icon().toString())),
                waypoint.icon() == null ? null : clear(viewer, "icon", editWaypointClearCmd(dimension, listId, id, "icon"))));
        lines.add(property(suggestEdit(viewer, "position", editWaypointPositionCmd(dimension, listId, id, waypoint)),
                "position", text(DimensionStyle.coordinates(waypoint.pos())),
                viewer.can(Viewer.Permission.EDIT) && viewer.isIn(dimension)
                        ? Chat.button(viewer, translatable("wp.details.here"), GREEN,
                        Click.run(editWaypointCmd(dimension, listId, id, "set position ~ ~ ~")),
                        Tooltip.of("wp.details.here.tooltip"))
                        : null));
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set color")),
                Tooltip.of("wp.edit.color")), "color", PickerScreens.colorValue(waypoint.rgb()), null));
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set yaw")),
                Tooltip.of("wp.edit.yaw")), "yaw", PickerScreens.yawValue(waypoint.yaw()), null));
        String toggled = waypoint.global() ? "local" : "global";
        lines.add(property(edit(viewer, Click.run(editWaypointCmd(dimension, listId, id, "set visibility " + toggled)),
                        Tooltip.of("wp.edit.visibility", translatable("wp.visibility." + toggled))),
                "visibility", translatable(waypoint.global() ? "wp.visibility.global" : "wp.visibility.local"), null));
        String keywords = String.join(", ", waypoint.keywords());
        lines.add(property(suggestEdit(viewer, "keywords",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "keywords", keywords)),
                "keywords", keywords.isEmpty() ? none() : text(keywords),
                keywords.isEmpty() ? null : clear(viewer, "keywords", editWaypointClearCmd(dimension, listId, id, "keywords"))));
        String description = waypoint.description();
        lines.add(property(suggestEdit(viewer, "description",
                        editWaypointSetSuggestionCmd(dimension, listId, id, "description", description)),
                "description", plainText(description).isBlank() ? none() : parse(description),
                description.isEmpty() ? null
                        : clear(viewer, "description", editWaypointClearCmd(dimension, listId, id, "description"))));
        lines.add(buttons(
                viewer.can(Viewer.Permission.NAVIGATE)
                        ? Chat.button(viewer, translatable("wp.action.navigate"), LIGHT_PURPLE,
                        Click.run(navigateCmd(dimension, listId, id)), Tooltip.of("wp.action.navigate.tooltip", name))
                        : null,
                viewer.can(Viewer.Permission.TP)
                        ? Chat.button(viewer, translatable("wp.action.teleport"), LIGHT_PURPLE,
                        Click.run(tpCmd(dimension, listId, id)), WaypointRefs.teleportTooltip(dims, dimension, waypoint))
                        : null,
                viewer.hasMod()
                        ? Chat.button(viewer, translatable("wp.action.download"), AQUA,
                        Click.run(downloadCmd(dimension, listId, id)), Tooltip.of("wp.action.download.waypoint", name))
                        : null,
                viewer.can(Viewer.Permission.REMOVE)
                        ? Chat.button(viewer, translatable("wp.action.remove"), RED,
                        Click.suggest(removeCmd(dimension, listId, waypoint)),
                        Tooltip.of("wp.action.remove.tooltip", name).hint("wp.hint.confirm"))
                        : null,
                Chat.button(viewer, translatable("wp.action.back"), GRAY, Click.run(listCommand),
                        Tooltip.of("wp.action.back.tooltip", WaypointRefs.label(list.displayName(), listId)))));
        return lines.buildScreen();
    }

    /** /wp details list, with the result of an edit on top. */
    public static Component list(DimensionStyle dims, String dimension, WaypointList list, @Nullable Component updated) {
        Viewer viewer = dims.viewer();
        String listId = list.name();
        Component name = WaypointRefs.label(list.displayName(), listId);
        ChatLines lines = new ChatLines();
        if (updated != null) {
            lines.add(Chat.ok(updated));
        }
        lines.line(ListScreen.dimensionLink(dims, dimension), Chat.CRUMB,
                Chat.hover(viewer, Chat.colored(WaypointRefs.label(viewer, list.displayName(), listId), GOLD),
                        WaypointRefs.listTooltip(list, null)),
                text("  "), Chat.colored(Chat.count("wp.count.waypoint", list.size()), GRAY));
        lines.add(property(suggestEdit(viewer, "display_name",
                        editListSetSuggestionCmd(dimension, listId, "display-name", list.displayName())),
                "display_name", name,
                list.hasDisplayNameOverride()
                        ? clear(viewer, "display_name", editListClearCmd(dimension, listId, "display-name"))
                        : null));
        lines.add(property(suggestEdit(viewer, "identifier", editListSetSuggestionCmd(dimension, listId, "identifier", listId)),
                "identifier", identifier(listId), null));
        lines.add(buttons(
                Chat.button(viewer, translatable("wp.action.open_list"), AQUA,
                        Click.run(ListTarget.list(dimension, listId).command(ListQuery.DEFAULT)), Tooltip.of("wp.open", name)),
                viewer.can(Viewer.Permission.ADD)
                        ? Chat.button(viewer, Chat.concat(text("+ "),
                                translatable(viewer.isIn(dimension) ? "wp.action.waypoint_here" : "wp.action.waypoint")),
                        GREEN, ListActions.addClick(viewer, dimension, list), ListActions.addTooltip(dims, dimension, list))
                        : null,
                viewer.hasMod() && !list.isEmpty()
                        ? Chat.button(viewer, translatable("wp.action.download"), AQUA,
                        Click.run(downloadCmd(dimension, listId, null)), Tooltip.of("wp.action.download.list", name))
                        : null,
                removeList(viewer, dimension, list, name),
                Chat.button(viewer, translatable("wp.action.back"), GRAY,
                        Click.run(ListTarget.dimension(dimension).command(ListQuery.DEFAULT)),
                        Tooltip.of("wp.action.back.tooltip", dims.name(dimension)))));
        return lines.buildScreen();
    }

    /** "Updated the colour" or "Cleared the description": the first property the patch changes. */
    public static @Nullable Component updated(WaypointPatch patch) {
        Map<String, PatchField<?>> fields = new LinkedHashMap<>();
        fields.put("identifier", patch.identifier());
        fields.put("display_name", patch.displayName());
        fields.put("initials", patch.initials());
        fields.put("position", patch.position());
        fields.put("color", patch.color());
        fields.put("yaw", patch.yaw());
        fields.put("visibility", patch.visibility());
        fields.put("keywords", patch.keywords());
        fields.put("description", patch.description());
        fields.put("icon", patch.icon());
        return updated(fields);
    }

    public static @Nullable Component updated(WaypointListPatch patch) {
        Map<String, PatchField<?>> fields = new LinkedHashMap<>();
        fields.put("identifier", patch.identifier());
        fields.put("display_name", patch.displayName());
        return updated(fields);
    }

    private static @Nullable Component updated(Map<String, PatchField<?>> fields) {
        for (Map.Entry<String, PatchField<?>> field : fields.entrySet()) {
            if (field.getValue().isClear()) {
                return translatable("wp.cleared." + field.getKey());
            }
            if (field.getValue().isSet()) {
                return translatable("wp.updated." + field.getKey());
            }
        }
        return null;
    }

    /** [✎] Label: value, then [×] or [Here]. */
    private static Component property(@Nullable Component edit, String field, Component value, @Nullable Component after) {
        return Chat.concat(edit, edit == null ? null : text(" "),
                translatable("wp.details.property", GRAY, translatable("wp.details." + field), Chat.colored(value, WHITE)),
                after == null ? null : text(" "), after);
    }

    /** The yellow [✎]; hidden without the edit permission. */
    private static @Nullable Component edit(Viewer viewer, Click click, Tooltip tooltip) {
        return viewer.can(Viewer.Permission.EDIT) ? Chat.button(viewer, text("✎"), YELLOW, click, tooltip) : null;
    }

    /** [✎] suggesting "set <property> <current value>". */
    private static @Nullable Component suggestEdit(Viewer viewer, String field, String command) {
        return edit(viewer, Click.suggest(command), Tooltip.of("wp.edit." + field).hint("wp.hint.edit"));
    }

    /** The red [×] suggesting "clear <property>". */
    private static @Nullable Component clear(Viewer viewer, String field, String command) {
        return viewer.can(Viewer.Permission.EDIT)
                ? Chat.button(viewer, text("×"), RED, Click.suggest(command),
                Tooltip.of("wp.clear." + field).hint("wp.hint.confirm"))
                : null;
    }

    /** Red for an empty list; dark gray with the reason otherwise. */
    private static @Nullable Component removeList(Viewer viewer, String dimension, WaypointList list, Component name) {
        if (!viewer.can(Viewer.Permission.REMOVE)) {
            return null;
        }
        if (list.isEmpty()) {
            return Chat.button(viewer, translatable("wp.action.remove"), RED,
                    Click.suggest(removeListCmd(dimension, list.name(), true)),
                    Tooltip.of("wp.action.remove.tooltip", name).hint("wp.hint.confirm"));
        }
        return Chat.disabledButton(viewer, translatable("wp.action.remove"), Tooltip.of("wp.action.remove.blocked")
                .line(translatable("wp.action.remove.blocked.detail", Chat.count("wp.count.waypoint", list.size()))));
    }

    /** The buttons one space apart, or no line when none is left. */
    private static @Nullable Component buttons(@Nullable Component... buttons) {
        List<Component> row = Arrays.asList(buttons);
        return Chat.isEmpty(row) ? null : Chat.spaced(row);
    }

    /** An identifier as typed; a blank one in quotes so it stays visible. */
    private static Component identifier(String identifier) {
        return text(identifier.isBlank() ? "\"" + identifier + "\"" : identifier);
    }

    private static Component none() {
        return translatable("wp.details.none", DARK_GRAY).decorate(TextDecoration.ITALIC);
    }
}
