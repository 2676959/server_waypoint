package _959.server_waypoint.util;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringCommandBuilderTest {
    @Test
    void escapeArgumentPreservesEveryIdentifierShape() {
        assertEquals("\"\"", StringCommandBuilder.escapeArgument(""));
        assertEquals("\"   \"", StringCommandBuilder.escapeArgument("   "));
        assertEquals("\"a \\\"quoted\\\" value\"", StringCommandBuilder.escapeArgument("a \"quoted\" value"));
        assertEquals("search", StringCommandBuilder.escapeArgument("search"));
        assertEquals("\"{\\\"text\\\":\\\"Shown\\\"}\"", StringCommandBuilder.escapeArgument("{\"text\":\"Shown\"}"));
    }

    @Test
    void selectorsAndEditsUseExactEscapedIdentifiers() {
        assertEquals(
                "/wp details waypoint minecraft:overworld \"\" \"way point\"",
                StringCommandBuilder.detailsWaypointCmd("minecraft:overworld", "", "way point")
        );
        assertEquals(
                "/wp edit waypoint minecraft:overworld list old set identifier renamed",
                StringCommandBuilder.editCmd(
                        "minecraft:overworld",
                        "list",
                        "old",
                        waypoint("renamed")
                )
        );
    }

    @Test
    void editSuggestionsOmitValuesRejectedByMinecraftChatComponents() {
        assertEquals(
                "/wp edit waypoint minecraft:overworld list Text set description ",
                StringCommandBuilder.editWaypointSetSuggestionCmd(
                        "minecraft:overworld",
                        "list",
                        "Text",
                        "description",
                        "First line\nSecond line"
                )
        );
        assertEquals(
                "/wp edit waypoint minecraft:overworld list Text set description plain",
                StringCommandBuilder.editWaypointSetSuggestionCmd(
                        "minecraft:overworld",
                        "list",
                        "Text",
                        "description",
                        "plain"
                )
        );
    }

    @Test
    void addCommandUsesIdentifierInsteadOfDisplayName() {
        SimpleWaypoint waypoint = new SimpleWaypoint(
                "exact-id", "{\"text\":\"Presentation\"}", "E",
                new WaypointPos(1, 2, 3), 0x123456, 0, true, List.of(), ""
        );

        String command = StringCommandBuilder.addCmd("minecraft:overworld", "list", waypoint);

        assertTrue(command.contains(" exact-id E "));
        assertTrue(!command.contains("Presentation"));
    }

    private static SimpleWaypoint waypoint(String identifier) {
        return new SimpleWaypoint(
                identifier, "I", new WaypointPos(0, 64, 0), 0x39C5BB, 0, true
        );
    }
}
