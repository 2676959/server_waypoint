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
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
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
    void restoreUsesItsOwnTranslationAndLiveWaypointActions() {
        var line = line(WaypointScope.SHARED, RESTORE, true);
        assertEquals("server_waypoint.update_notification.chat.restored", line.key());
        assertEquals(2, line.arguments().size());
        assertEquals(ClickEvent.runCommand("/wp tp minecraft:overworld \"test list\" home"),
                line.arguments().get(0).asComponent().children().get(0).clickEvent());
    }

    @Test
    void listChatLinksOnlyListsThatStillExist() {
        for (var action : List.of(WaypointUpdateFeedback.Action.ADD_LIST, WaypointUpdateFeedback.Action.UPDATE_LIST,
                WaypointUpdateFeedback.Action.REMOVE_LIST)) {
            var update = new WaypointUpdateFeedback.Update(WaypointScope.PRIVATE, DIMENSION, action, LIST, null);
            var line = chatLine(update, false);
            var link = line.arguments().get(0).asComponent();
            if (action == WaypointUpdateFeedback.Action.REMOVE_LIST) {
                assertNull(link.clickEvent());
                assertNull(link.hoverEvent());
            } else {
                assertEquals(ClickEvent.runCommand("/wp private list minecraft:overworld \"test list\""), link.clickEvent());
                assertNotNull(link.hoverEvent());
            }
        }
    }

    @Test
    void eachToastUsesItsOwnKeyAndOneDisplayNameArgument() {
        for (var entry : List.of(java.util.Map.entry(WaypointUpdateFeedback.Action.ADD, "added"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.UPDATE, "updated"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.REMOVE, "removed"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.RESTORE, "restored"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.ADD_LIST, "created_list"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.UPDATE_LIST, "updated_list"),
                java.util.Map.entry(WaypointUpdateFeedback.Action.REMOVE_LIST, "removed_list"))) {
            boolean list = entry.getValue().endsWith("_list");
            var update = new WaypointUpdateFeedback.Update(WaypointScope.SHARED, DIMENSION, entry.getKey(),
                    LIST, list ? null : WAYPOINT);
            var contents = assertInstanceOf(TranslatableContents.class,
                    WaypointUpdateFeedback.toast(update).getContents());
            assertEquals("server_waypoint.update_notification." + entry.getValue(), contents.getKey());
            assertArrayEquals(new Object[]{list ? "Farms" : "Home Base"}, contents.getArgs());
        }
    }

    private static TranslatableComponent line(WaypointScope scope, WaypointModificationType type, boolean canTeleport) {
        var update = new WaypointModificationMessage(scope, DIMENSION, LIST.name(), LIST.displayName(),
                WAYPOINT.name(), type == REMOVE ? null : WAYPOINT, type, 8);
        var feedback = WaypointUpdateFeedback.Update.fromDelta(update, LIST, WAYPOINT);
        return chatLine(feedback, canTeleport);
    }

    private static TranslatableComponent chatLine(WaypointUpdateFeedback.Update update, boolean canTeleport) {
        //? if >=1.21.6 {
        // Including the dialog click-event codec initializes the vanilla registries.
        //? if neoforge && >=1.21.9 {
        /*org.junit.jupiter.api.Assumptions.assumeTrue(net.neoforged.fml.loading.FMLLoader.getCurrentOrNull() != null,
                "Minecraft registries require a running NeoForge loader");
        *///?} elif neoforge {
        /*org.junit.jupiter.api.Assumptions.assumeTrue(net.neoforged.fml.loading.LoadingModList.get() != null,
                "Minecraft registries require a running NeoForge loader");
        *///?}
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        //?}
        Component line = TextHelper.toAdventure(WaypointUpdateFeedback.chat(update, canTeleport));
        return assertInstanceOf(TranslatableComponent.class, line);
    }
}
