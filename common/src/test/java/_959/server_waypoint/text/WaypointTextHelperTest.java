package _959.server_waypoint.text;

import _959.server_waypoint.core.waypoint.WaypointList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WaypointTextHelperTest {
    @Test
    void listIndentPrecedesDetailsButton() {
        Component output = WaypointTextHelper.getWaypointListText(
                new WaypointList("meadow", "Meadow", 1, List.of()),
                "minecraft:overworld",
                1,
                true,
                false,
                false,
                false
        );
        List<Component> header = output.children().get(0).children();

        assertEquals("  ", ((TextComponent) header.get(0)).content());
        assertNotNull(header.get(1).clickEvent());
        assertEquals(ClickEvent.Action.RUN_COMMAND, header.get(1).clickEvent().action());
        assertEquals(" ", ((TextComponent) header.get(2)).content());
        assertEquals("Meadow", ((TextComponent) header.get(3)).content());
        assertNull(header.get(3).clickEvent());
    }
}
