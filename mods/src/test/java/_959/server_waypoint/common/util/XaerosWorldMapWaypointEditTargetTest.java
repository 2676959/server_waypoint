package _959.server_waypoint.common.util;

import _959.server_waypoint.common.client.integrations.XaerosWorldMapWaypointHelper;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.util.NamespacedId;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.container.MinimapWorldContainer;

import static _959.server_waypoint.common.util.TestDimensions.NETHER;
import static _959.server_waypoint.common.util.TestDimensions.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class XaerosWorldMapWaypointEditTargetTest {
    @TempDir
    Path directory;

    @Test
    void selectedWorldDeterminesTheDimensionWithoutUsingThePlayersDimension() throws Exception {
        assertEquals("minecraft:the_nether", XaerosWorldMapWaypointHelper.getWaypointDimensionName(world(NETHER)));
        assertEquals("minecraft:overworld", XaerosWorldMapWaypointHelper.getWaypointDimensionName(world(OVERWORLD)));
        assertEquals("example:moon", XaerosWorldMapWaypointHelper.getWaypointDimensionName(
                world(TestDimensions.of("example", "moon"))));
    }

    @Test
    void missingWorldDoesNotChooseAnUnrelatedDimension() throws Exception {
        assertNull(XaerosWorldMapWaypointHelper.getWaypointDimensionName(null));
        assertNull(XaerosWorldMapWaypointHelper.getWaypointDimensionName(world(null)));
    }

    @Test
    void editorUsesTheCompleteDetachedSnapshotFromTheOwningDimension() {
        WaypointFilesManagerCore files = files();
        SimpleWaypoint cached = files.getWaypointFileManager("minecraft:the_nether")
                .getWaypointListByName("Bases").getWaypointByName("Home");

        var target = XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                "minecraft:the_nether", "sw\u241FBases", "Home", files);

        assertNotNull(target);
        assertEquals("minecraft:the_nether", target.dimensionName());
        assertEquals("Bases", target.listName());
        assertEquals("Server bases", target.listDisplayName());
        assertEquals("Home", target.waypoint().name());
        assertEquals("Nether home", target.waypoint().displayName());
        assertEquals("H", target.waypoint().initials());
        assertEquals(new WaypointPos(10, 64, 20), target.waypoint().pos());
        assertEquals(0x123456, target.waypoint().rgb());
        assertEquals(90, target.waypoint().yaw());
        assertEquals(true, target.waypoint().global());
        assertEquals(List.of("portal", "base"), target.waypoint().keywords());
        assertEquals("Server description", target.waypoint().description());
        assertEquals(NamespacedId.parse("minecraft:diamond"), target.waypoint().icon());
        assertNotSame(cached, target.waypoint());
    }

    @Test
    void personalAndStaleMarkersCannotResolveToAnotherWaypoint() {
        WaypointFilesManagerCore files = files();

        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(null, "sw\u241FBases", "Home", files));
        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget("minecraft:the_end", "sw\u241FBases", "Home", files));
        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget("minecraft:the_nether", "Bases", "Home", files));
        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget("minecraft:the_nether", "sw\u241FMissing", "Home", files));
        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget("minecraft:the_nether", "sw\u241FBases", "Missing", files));
    }

    @Test
    void sameNameInAnotherListKeepsItsOwnIdentity() {
        WaypointFilesManagerCore files = files();
        files.putWaypointList("minecraft:the_nether", new WaypointList("Mines", 1, List.of(saved("Mine home"))));

        var target = XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                "minecraft:the_nether", "sw\u241FMines", "Home", files);

        assertNotNull(target);
        assertEquals("Mines", target.listName());
        assertEquals("Mine home", target.waypoint().displayName());
    }

    @Test
    void markedDefaultWaypointResolvesToDefaultServerListAndPersonalEntryDoesNot() {
        WaypointFilesManagerCore files = files();
        files.putWaypointList("minecraft:the_nether",
                new WaypointList("gui.xaero_default", 1, List.of(saved("Default home"))));

        var target = XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                "minecraft:the_nether", "gui.xaero_default", "sw\u241FHome", files);

        assertNotNull(target);
        assertEquals("gui.xaero_default", target.listName());
        assertEquals("Home", target.waypoint().name());
        assertEquals("Default home", target.waypoint().displayName());
        assertNull(XaerosWorldMapWaypointHelper.resolveSyncedEditTarget(
                "minecraft:the_nether", "gui.xaero_default", "Home", files));
    }

    private WaypointFilesManagerCore files() {
        WaypointFilesManagerCore files = new WaypointFilesManagerCore(this.directory);
        files.putWaypointList("minecraft:overworld", new WaypointList("Bases", 7, List.of(saved("Overworld home"))));
        files.putWaypointList("minecraft:the_nether", new WaypointList("Bases", "Server bases", 9,
                List.of(saved("Nether home"))));
        return files;
    }

    private static SimpleWaypoint saved(String displayName) {
        return new SimpleWaypoint("Home", displayName, "H", new WaypointPos(10, 64, 20),
                0x123456, 90, true, List.of("portal", "base"), "Server description",
                NamespacedId.parse("minecraft:diamond"));
    }

    private static MinimapWorld world(ResourceKey<Level> dimension) throws ReflectiveOperationException {
        //? if >= 1.21.5 {
        Constructor<MinimapWorld> constructor = MinimapWorld.class.getDeclaredConstructor(
                MinimapWorldContainer.class, String.class, ResourceKey.class);
        constructor.setAccessible(true);
        return constructor.newInstance(null, "selected", dimension);
        //?} else {
        /*return new MinimapWorld(null, "selected", dimension) {
        };
        *///?}
    }
}
