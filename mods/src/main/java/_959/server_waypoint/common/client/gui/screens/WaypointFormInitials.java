package _959.server_waypoint.common.client.gui.screens;

import static _959.server_waypoint.text.FormattedTextHelper.plainText;
import static _959.server_waypoint.util.WaypointInitials.getDefaultInitials;

/**
 * When the form's initials follow the name: as long as they still equal what
 * {@code WaypointInitials.getDefaultInitials} makes of the previous name. Once the player types
 * something else they stay, and on Edit they follow only if the saved ones were the default.
 */
final class WaypointFormInitials {
    private WaypointFormInitials() {
    }

    /** The initials made from a name, which may hold formatted text. */
    static String defaultFor(String name) {
        return getDefaultInitials(plainText(name));
    }

    /** The initials to show after the name changed from {@code previousName} to {@code newName}. */
    static String afterNameChange(String previousName, String newName, String initials) {
        return initials.equals(defaultFor(previousName)) ? defaultFor(newName) : initials;
    }
}
