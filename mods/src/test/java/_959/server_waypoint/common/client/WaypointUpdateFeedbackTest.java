package _959.server_waypoint.common.client;

import _959.server_waypoint.common.util.TextHelper;
import _959.server_waypoint.core.collection.WaypointScope;
import _959.server_waypoint.core.network.message.WaypointModificationMessage;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointModificationType;
import _959.server_waypoint.core.waypoint.WaypointPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;

import static _959.server_waypoint.core.waypoint.WaypointModificationType.*;
import static org.junit.jupiter.api.Assertions.*;

class WaypointUpdateFeedbackTest {
    private static final String DIMENSION = "minecraft:overworld";
    private static final SimpleWaypoint WAYPOINT = new SimpleWaypoint("home", "Home Base", "HB",
            new WaypointPos(10, 64, 20), 0xE9A130, 0, false, List.of(), "A test waypoint");
    private static final WaypointList LIST = new WaypointList("test list", "Farms", 8, List.of(WAYPOINT));

    @Test
    void addUsesBroadcastColoursAndInteractiveReferences() {
        var line = line(WaypointScope.SHARED, ADD, true);
        assertEquals("server_waypoint.update_notification.chat.added", line.key());
        assertEquals(NamedTextColor.GRAY, line.color());
        assertNull(line.clickEvent());
        assertNull(line.hoverEvent());

        var waypointRef = line.arguments().get(0).asComponent();
        var marker = waypointRef.children().get(0);
        var name = waypointRef.children().get(2);
        var list = line.arguments().get(1).asComponent();
        assertEquals(TextColor.color(WAYPOINT.rgb()), marker.color());
        assertEquals(NamedTextColor.WHITE, name.color());
        assertEquals(NamedTextColor.WHITE, list.color());
        assertEquals(ClickEvent.runCommand("/wp tp minecraft:overworld \"test list\" home"), marker.clickEvent());
        assertEquals(ClickEvent.runCommand("/wp details waypoint minecraft:overworld \"test list\" home"), name.clickEvent());
        assertEquals(ClickEvent.runCommand("/wp list minecraft:overworld \"test list\""), list.clickEvent());
        assertNotNull(marker.hoverEvent());
        assertNotNull(name.hoverEvent());
        assertNotNull(list.hoverEvent());
    }

    @Test
    void privateEditScopesDetailsAndOmitsTeleportWithoutPermission() {
        var line = line(WaypointScope.PRIVATE, UPDATE, false);
        assertEquals("server_waypoint.update_notification.chat.updated", line.key());
        assertEquals(1, line.arguments().size());
        var ref = line.arguments().get(0).asComponent();
        assertNull(ref.children().get(0).clickEvent());
        assertNotNull(ref.children().get(0).hoverEvent());
        assertEquals(ClickEvent.runCommand("/wp private details waypoint minecraft:overworld \"test list\" home"),
                ref.children().get(2).clickEvent());
    }

    @Test
    void removalUsesCachedMarkerWithoutWaypointActionsAndKeepsPrivateListLink() {
        var line = line(WaypointScope.PRIVATE, REMOVE, true);
        assertEquals("server_waypoint.update_notification.chat.removed", line.key());
        var ref = line.arguments().get(0).asComponent();
        assertEquals(TextColor.color(WAYPOINT.rgb()), ref.children().get(0).color());
        assertTrue(ref.children().stream().allMatch(piece -> piece.clickEvent() == null && piece.hoverEvent() == null));
        assertEquals(ClickEvent.runCommand("/wp private list minecraft:overworld \"test list\""),
                line.arguments().get(1).asComponent().clickEvent());
    }

    @Test
    void toastKeepsItsOriginalKeysAndSingleDisplayNameArgument() {
        for (var entry : List.of(java.util.Map.entry(ADD, "added"), java.util.Map.entry(UPDATE, "updated"),
                java.util.Map.entry(REMOVE, "removed"))) {
            var contents = assertInstanceOf(TranslatableContents.class,
                    WaypointUpdateFeedback.toast(entry.getKey(), WAYPOINT.displayName()).getContents());
            assertEquals("server_waypoint.update_notification." + entry.getValue(), contents.getKey());
            assertArrayEquals(new Object[]{"Home Base"}, contents.getArgs());
        }
    }

    private static TranslatableComponent line(WaypointScope scope, WaypointModificationType type, boolean canTeleport) {
        var update = new WaypointModificationMessage(scope, DIMENSION, LIST.name(), LIST.displayName(),
                WAYPOINT.name(), type == REMOVE ? null : WAYPOINT, type, 8);
        Component line = TextHelper.toAdventure(WaypointUpdateFeedback.chat(update, LIST, WAYPOINT, canTeleport));
        return assertInstanceOf(TranslatableComponent.class, line);
    }
}
