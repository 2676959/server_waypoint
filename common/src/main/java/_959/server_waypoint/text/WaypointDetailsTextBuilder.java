package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.List;

import static _959.server_waypoint.text.FormattedTextHelper.parse;
import static _959.server_waypoint.text.TextButtonBuilder.*;
import static _959.server_waypoint.util.ColorUtils.rgbToHexCode;
import static _959.server_waypoint.util.StringCommandBuilder.*;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public final class WaypointDetailsTextBuilder {
    private WaypointDetailsTextBuilder() {
    }

    public static Component listDetails(
            String dimensionName,
            WaypointList waypointList,
            boolean canAdd,
            boolean canEdit,
            boolean canRemove
    ) {
        WaypointList snapshot = waypointList.deepCopy();
        Component details = translatable("waypoint.details.list.title", NamedTextColor.GOLD)
                .appendNewline()
                .append(property(
                        "waypoint.details.identifier",
                        text(snapshot.name()),
                        editControl(
                                canEdit,
                                editListSetCmd(
                                        dimensionName,
                                        snapshot.name(),
                                        "identifier",
                                        snapshot.name()
                                )
                        )
                ))
                .append(property(
                        "waypoint.details.display_name",
                        parse(snapshot.displayName()),
                        editControl(
                                canEdit,
                                editListSetCmd(
                                        dimensionName,
                                        snapshot.name(),
                                        "display-name",
                                        snapshot.displayName()
                                )
                        ).append(snapshot.hasDisplayNameOverride()
                                ? clearControl(
                                        canEdit,
                                        editListClearCmd(
                                                dimensionName,
                                                snapshot.name(),
                                                "display-name"
                                        )
                                )
                                : text(""))
                ))
                .append(property("waypoint.details.dimension", WaypointTextHelper.dimensionNameWithColor(dimensionName), text("")))
                .append(property("waypoint.details.waypoint_count", text(snapshot.size()), text("")));
        Component actions = runCommandButton(
                translatable("button.open_list"),
                NamedTextColor.AQUA,
                listWaypointListCmd(
                        dimensionName,
                        snapshot.name(),
                        new ListOptions("", _959.server_waypoint.core.waypoint.WaypointSorting.SortMode.DEFAULT, false, 1, 10)
                ),
                translatable("button.open_list")
        );
        actions = actions.appendSpace().append(canAdd
                ? suggestCommandButton(
                        translatable("button.add.waypoint.label"),
                        NamedTextColor.GREEN,
                        "/wp add " + dimensionName + ' ' + escapeArgument(snapshot.name()) + ' ',
                        translatable("button.add.waypoint")
                )
                : disabledPermissionButton("+", translatable("button.permission.add.required")));
        actions = actions.appendSpace().append(canRemove
                ? suggestCommandButton(
                        translatable("button.remove.label"),
                        NamedTextColor.RED,
                        removeListCmd(dimensionName, snapshot.name(), true),
                        translatable("button.remove")
                )
                : disabledPermissionButton("❌", translatable("button.permission.remove.required")));
        return details.appendNewline().append(actions).appendSpace().append(backButton(dimensionName));
    }

    public static Component waypointDetails(
            String dimensionName,
            WaypointList waypointList,
            SimpleWaypoint waypoint,
            boolean canEdit,
            boolean canRemove,
            boolean canTeleport,
            boolean canNavigate
    ) {
        WaypointList list = waypointList.deepCopy();
        SimpleWaypoint snapshot = new SimpleWaypoint(waypoint);
        String listIdentifier = list.name();
        String identifier = snapshot.name();
        Component details = translatable("waypoint.details.waypoint.title", NamedTextColor.GOLD)
                .appendNewline()
                .append(property("waypoint.details.identifier", text(identifier), editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "identifier", identifier))))
                .append(property(
                        "waypoint.details.display_name",
                        parse(snapshot.displayName()),
                        editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "display-name", snapshot.displayName()))
                                .append(snapshot.hasDisplayNameOverride()
                                        ? clearControl(canEdit, editWaypointClearCmd(dimensionName, listIdentifier, identifier, "display-name"))
                                        : text(""))
                ))
                .append(property("waypoint.details.source_list_display_name", parse(list.displayName()), text("")))
                .append(property("waypoint.details.source_list_identifier", text(listIdentifier), text("")))
                .append(property("waypoint.details.dimension", WaypointTextHelper.dimensionNameWithColor(dimensionName), text("")))
                .append(property("waypoint.details.initials", text(snapshot.initials()), editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "initials", snapshot.initials()))))
                .append(property("waypoint.details.position", text(snapshot.pos().toShortString()), editControl(canEdit, editWaypointPositionCmd(dimensionName, listIdentifier, identifier, snapshot))))
                .append(property(
                        "waypoint.details.color",
                        text("■", TextColor.color(snapshot.rgb())).appendSpace().append(text(rgbToHexCode(snapshot.rgb(), true))),
                        editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "color", rgbToHexCode(snapshot.rgb(), false)))
                ))
                .append(property("waypoint.details.yaw", text(snapshot.yaw()), editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "yaw", Integer.toString(snapshot.yaw())))))
                .append(property("waypoint.details.visibility", translatable(snapshot.global() ? "waypoint.global" : "waypoint.local"), editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "visibility", snapshot.global() ? "global" : "local"))))
                .append(property(
                        "waypoint.details.keywords",
                        text(String.join(", ", snapshot.keywords())),
                        editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "keywords", String.join(", ", snapshot.keywords())))
                                .append(snapshot.keywords().isEmpty() ? text("") : clearControl(canEdit, editWaypointClearCmd(dimensionName, listIdentifier, identifier, "keywords")))
                ))
                .append(property(
                        "waypoint.details.description",
                        parse(snapshot.description()),
                        editControl(canEdit, editWaypointSetCmd(dimensionName, listIdentifier, identifier, "description", snapshot.description()))
                                .append(snapshot.description().isEmpty() ? text("") : clearControl(canEdit, editWaypointClearCmd(dimensionName, listIdentifier, identifier, "description")))
                ));
        Component actions = canNavigate
                ? runCommandButton(translatable("button.navigate"), NamedTextColor.AQUA, navigateCmd(dimensionName, listIdentifier, identifier), translatable("button.navigate"))
                : disabledPermissionButton("N", translatable("button.permission.navigate.required"));
        actions = actions.appendSpace().append(canTeleport
                ? runCommandButton(translatable("button.teleport"), NamedTextColor.LIGHT_PURPLE, tpCmd(dimensionName, listIdentifier, identifier), translatable("button.teleport"))
                : disabledPermissionButton("T", translatable("button.permission.tp.required")));
        actions = actions.appendSpace().append(canRemove
                ? suggestCommandButton(translatable("button.remove.label"), NamedTextColor.RED, removeCmd(dimensionName, listIdentifier, snapshot), translatable("button.remove"))
                : disabledPermissionButton("❌", translatable("button.permission.remove.required")));
        actions = actions.appendSpace().append(runCommandButton(
                translatable("button.back"),
                NamedTextColor.GRAY,
                detailsListCmd(dimensionName, listIdentifier),
                translatable("button.back")
        ));
        return details.appendNewline().append(actions);
    }

    private static Component property(String key, Component value, Component controls) {
        return translatable(key, NamedTextColor.GRAY)
                .append(text(": "))
                .append(value.colorIfAbsent(NamedTextColor.WHITE))
                .appendSpace()
                .append(controls)
                .appendNewline();
    }

    private static Component editControl(boolean enabled, String command) {
        return enabled
                ? propertyEditButton(command, translatable("button.edit"))
                : disabledPermissionButton("📝", translatable("button.permission.edit.required"));
    }

    private static Component clearControl(boolean enabled, String command) {
        return enabled
                ? propertyClearButton(command, translatable("button.clear"))
                : disabledPermissionButton("x", translatable("button.permission.edit.required"));
    }

    private static Component backButton(String dimensionName) {
        return runCommandButton(
                translatable("button.back"),
                NamedTextColor.GRAY,
                listDimensionCmd(
                        dimensionName,
                        new ListOptions("", _959.server_waypoint.core.waypoint.WaypointSorting.SortMode.DEFAULT, false, 1, 10)
                ),
                translatable("button.back")
        );
    }
}
