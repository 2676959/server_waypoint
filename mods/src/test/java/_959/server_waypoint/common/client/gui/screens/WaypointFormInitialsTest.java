package _959.server_waypoint.common.client.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaypointFormInitialsTest {
    @Test
    void theDefaultComesFromTheNamesPlainText() {
        assertEquals("HB", WaypointFormInitials.defaultFor("Home Base"));
        assertEquals("HB", WaypointFormInitials.defaultFor("{\"text\":\"Home Base\",\"color\":\"red\"}"));
        assertEquals("", WaypointFormInitials.defaultFor(""));
    }

    @Test
    void initialsFollowTheNameWhileTheyEqualTheDefaultForThePreviousName() {
        // Add starts with no name and no initials, which is the default for an empty name.
        String initials = "";
        initials = WaypointFormInitials.afterNameChange("", "Home", initials);
        assertEquals(WaypointFormInitials.defaultFor("Home"), initials);
        initials = WaypointFormInitials.afterNameChange("Home", "Home Base", initials);
        assertEquals("HB", initials);
        initials = WaypointFormInitials.afterNameChange("Home Base", "Camp Site", initials);
        assertEquals("CS", initials);
    }

    @Test
    void initialsTheUserTypedStayWhenTheNameChanges() {
        assertEquals("X", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "X"));
        assertEquals("HBX", WaypointFormInitials.afterNameChange("Home Base", "Home Bases", "HBX"));
    }

    @Test
    void emptiedInitialsStayEmptyOnceTheNameHasAnotherDefault() {
        // Deleting the initials is typing other initials: "" is not the default for "Home Base".
        assertEquals("", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", ""));
    }

    @Test
    void initialsTypedBackToTheDefaultFollowTheNameAgain() {
        assertEquals("CS", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "HB"));
    }

    @Test
    void onEditTheInitialsFollowOnlyIfTheSavedOnesAreTheDefaultForTheSavedName() {
        // Saved as the default for "Home Base": editing the name updates them.
        assertEquals("CS", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "HB"));
        // Saved as something else: they stay.
        assertEquals("ZZ", WaypointFormInitials.afterNameChange("Home Base", "Camp Site", "ZZ"));
    }

    @Test
    void formattedNamesFollowThroughTheirPlainText() {
        String json = "{\"text\":\"Home Base\"}";

        assertEquals("CS", WaypointFormInitials.afterNameChange(json, "Camp Site", "HB"));
    }
}
