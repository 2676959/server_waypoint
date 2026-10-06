package _959.server_waypoint.text.feedback;

import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.crossserver.RemoteCatalogState;
import _959.server_waypoint.crossserver.RemoteServerId;
import _959.server_waypoint.crossserver.catalog.RemoteCatalogQuery.Server;
import _959.server_waypoint.crossserver.protocol.ApplicationMessage.Result;
import _959.server_waypoint.text.chat.ChatIcons;
import _959.server_waypoint.text.chat.ListQuery;
import _959.server_waypoint.text.chat.VanillaChatSprites;
import _959.server_waypoint.text.chat.Viewer;
import _959.server_waypoint.util.NamespacedId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static _959.server_waypoint.text.chat.ChatAssert.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatIconsTest {
    private static final ChatIcons ICONS = new ChatIcons() {
        @Override
        public Component item(NamespacedId id) {
            return Component.text("sprite:" + id);
        }
    };

    /** Main Home with a diamond icon. */
    private static SimpleWaypoint diamondHome() {
        var original = Fixtures.homeBases().getWaypointByName("Main Home");
        return new SimpleWaypoint(original.name(), original.displayName(), original.initials(), original.pos(),
                original.rgb(), original.yaw(), original.global(), List.of(), "", NamespacedId.parse("minecraft:diamond"));
    }

    @Test
    void spritesKeepTheirColoursAndTeleportActionWithoutChangingTheNameAction() {
        var waypoint = diamondHome();
        Viewer viewer = new Viewer(Viewer.everything(), false, false, Fixtures.OVERWORLD, null, 0, ICONS);
        Component line = WaypointRefs.reference(Fixtures.dims(viewer), Fixtures.OVERWORLD, Fixtures.homeBases(), waypoint);

        assertEquals("sprite:minecraft:diamond [MH] Main Home", render(line));
        assertEquals(NamedTextColor.WHITE, colorOf(line, "sprite:minecraft:diamond"));
        assertEquals("/wp tp minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(line, "sprite:minecraft:diamond"));
        assertEquals("/wp details waypoint minecraft:overworld \"Home Bases\" \"Main Home\"", clickOf(line, "Main Home"));

        Viewer console = new Viewer(Viewer.everything(), false, true, null, null, 0, ICONS);
        assertEquals("[MH] Main Home", render(WaypointRefs.plain(console, waypoint)));
        assertSame(ChatIcons.NONE, console.icons());
    }

    @Test
    void remoteRowsKeepTheViewersSprites() {
        WaypointList homes = new WaypointList("Home Bases", 1, List.of(diamondHome()));
        Server survival = new Server(new RemoteServerId("survival"), "Survival", RemoteCatalogState.AVAILABLE,
                Map.of(Fixtures.OVERWORLD, List.of(homes)));
        Viewer viewer = new Viewer(Viewer.everything(), false, false, Fixtures.OVERWORLD, null, 0, ICONS);

        assertEquals("sprite:minecraft:diamond [MH] Main Home",
                lines(RemoteScreens.list(viewer, survival, Fixtures.OVERWORLD, homes, ListQuery.DEFAULT, 10)).get(1));
    }

    @Test
    void arrivalIncludesTheWaypointSpriteWithoutTintingIt() {
        Component arrival = RemoteScreens.arrival(ICONS, Result.SUCCESS, "survival", "Main Home", diamondHome());

        assertEquals("✔ Arrived at sprite:minecraft:diamond [MH] Main Home on survival", render(arrival));
        assertEquals(NamedTextColor.WHITE, colorOf(arrival, "sprite:minecraft:diamond"));
        assertNull(clickOf(arrival, "sprite:minecraft:diamond"));
    }

    @Test
    void textureNamesComeFromModelsIncludingAnimatedItemsAndBlockFaces() {
        assertEquals("minecraft:item/diamond", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:diamond")));
        assertEquals("minecraft:item/compass_16", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:compass")));
        assertEquals("minecraft:block/grass_block_side", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:grass_block")));
        assertEquals("minecraft:item/spyglass", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:spyglass")),
                "the inventory model, not the in-hand one");
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("voxelmap:waypoint")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("example:custom")));
    }

    @Test
    void staticSpecialItemsHaveRepresentativeSprites() {
        for (String colour : List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black")) {
            assertEquals("minecraft:block/" + colour + "_wool",
                    VanillaChatSprites.sprite(NamespacedId.parse("minecraft:" + colour + "_bed")));
            assertEquals("minecraft:block/" + colour + "_shulker_box",
                    VanillaChatSprites.sprite(NamespacedId.parse("minecraft:" + colour + "_shulker_box")));
        }
        assertEquals("minecraft:block/oak_planks", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:chest")));
        assertEquals("minecraft:block/conduit", VanillaChatSprites.sprite(NamespacedId.parse("minecraft:conduit")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:white_banner")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:player_head")));
    }

    @Test
    void tintedTexturesAndTexturesMissingFromANewerClientKeepText() {
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:potion")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:oak_leaves")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:leather_helmet")));
        assertNull(VanillaChatSprites.sprite(NamespacedId.parse("minecraft:quartz_pillar")));
    }
}
