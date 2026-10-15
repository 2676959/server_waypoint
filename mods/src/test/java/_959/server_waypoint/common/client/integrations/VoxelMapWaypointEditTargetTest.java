//? if voxelmap {
package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.common.util.SyncedWaypointName;
import _959.server_waypoint.common.client.WaypointClientMod;
import _959.server_waypoint.core.WaypointFilesManagerCore;
import _959.server_waypoint.core.waypoint.SimpleWaypoint;
import _959.server_waypoint.core.waypoint.WaypointList;
import _959.server_waypoint.core.waypoint.WaypointPos;
import _959.server_waypoint.mixin.voxelmap.VoxelMapGuiWaypointsMixin;
import _959.server_waypoint.util.NamespacedId;
import com.mamiyaotaru.voxelmap.util.DimensionContainer;
import com.mamiyaotaru.voxelmap.util.Waypoint;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoxelMapWaypointEditTargetTest {
    @TempDir
    Path directory;

    @Test
    void localEditContinuesThroughVoxelMapsOwnEditor() throws ReflectiveOperationException {
        var handler = VoxelMapGuiWaypointsMixin.class.getDeclaredMethod(
                "sw$redirectEditGui", Waypoint.class, CallbackInfo.class);
        handler.setAccessible(true);
        CallbackInfo callback = new CallbackInfo("editWaypoint", true);

        handler.invoke(new VoxelMapGuiWaypointsMixin(), waypoint("Local home", "overworld"), callback);

        assertFalse(callback.isCancelled());
    }

    @Test
    void staleSyncedEditContinuesThroughVoxelMapsOwnEditor() throws ReflectiveOperationException {
        assertNativeEditContinues(waypoint("sw\u241FBases\u241FMissing", "the_nether"));
    }

    @Test
    void ambiguousSyncedEditContinuesThroughVoxelMapsOwnEditor() throws ReflectiveOperationException {
        assertNativeEditContinues(waypoint("sw\u241FBases\u241FHome", "overworld", "the_nether"));
    }

    private void assertNativeEditContinues(Waypoint selected) throws ReflectiveOperationException {
        // Skip the client's renderer/network constructor, but use a fully initialized real waypoint store.
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        WaypointClientMod client = (WaypointClientMod) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), WaypointClientMod.class);
        WaypointFilesManagerCore files = files();
        for (Field field : WaypointFilesManagerCore.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                field.setAccessible(true);
                field.set(client, field.get(files));
            }
        }
        Field instance = WaypointClientMod.class.getDeclaredField("INSTANCE");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        try {
            instance.set(null, client);
            var handler = VoxelMapGuiWaypointsMixin.class.getDeclaredMethod(
                    "sw$redirectEditGui", Waypoint.class, CallbackInfo.class);
            handler.setAccessible(true);
            CallbackInfo callback = new CallbackInfo("editWaypoint", true);

            handler.invoke(new VoxelMapGuiWaypointsMixin(), selected, callback);

            assertFalse(callback.isCancelled(), "An unresolved marker must not silently swallow the native Edit action");
        } finally {
            instance.set(null, previous);
        }
    }

    @Test
    void resolvesTheSelectedDimensionAndPreservesServerOnlyFields() {
        WaypointFilesManagerCore files = files();
        SimpleWaypoint cached = files.getWaypointFileManager("minecraft:the_nether")
                .getWaypointListByName("Bases").getWaypointByName("Home");

        var target = VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Bases", "Home"), "the_nether"), files);

        assertNotNull(target);
        assertEquals("minecraft:the_nether", target.dimensionName());
        assertEquals("Bases", target.listName());
        assertEquals("Server bases", target.listDisplayName());
        assertEquals("Home", target.waypoint().name());
        assertEquals("Nether home", target.waypoint().displayName());
        assertEquals(new WaypointPos(10, 64, 20), target.waypoint().pos());
        assertEquals("H", target.waypoint().initials());
        assertEquals(0x123456, target.waypoint().rgb());
        assertEquals(90, target.waypoint().yaw());
        assertEquals(true, target.waypoint().global());
        assertEquals(List.of("portal", "base"), target.waypoint().keywords());
        assertEquals("Server description", target.waypoint().description());
        assertEquals(NamespacedId.parse("minecraft:diamond"), target.waypoint().icon());
        assertNotSame(cached, target.waypoint(), "editing uses a detached server snapshot");
    }

    @Test
    void encodedListNameDisambiguatesWaypointsWithTheSameName() {
        WaypointFilesManagerCore files = files();
        files.putWaypointList("minecraft:the_nether", new WaypointList("Mines", 1,
                List.of(saved("Mine home"))));

        var target = VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Mines", "Home"), "the_nether"), files);

        assertNotNull(target);
        assertEquals("Mines", target.listName());
        assertEquals("Mine home", target.waypoint().displayName());
    }

    @Test
    void customDimensionsKeepTheirNamespace() {
        WaypointFilesManagerCore files = files();
        files.putWaypointList("example:moon", new WaypointList("Bases", 1, List.of(saved("Moon home"))));

        var target = VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Bases", "Home"), "example:moon"), files);

        assertNotNull(target);
        assertEquals("example:moon", target.dimensionName());
        assertEquals("Moon home", target.waypoint().displayName());
    }

    @Test
    void staleAndLocalWaypointsDoNotResolveToAnotherServerWaypoint() {
        WaypointFilesManagerCore files = files();

        assertNull(VoxelMapWaypointHelper.resolveSyncedEditTarget(waypoint("Home", "the_nether"), files));
        assertNull(VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Missing", "Home"), "the_nether"), files));
        assertNull(VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Bases", "Missing"), "the_nether"), files));
        assertNull(VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Bases", "Home"), "the_end"), files));
    }

    @Test
    void ambiguousMultiDimensionMarkersDoNotChooseAnArbitraryWaypoint() {
        WaypointFilesManagerCore files = files();

        assertNull(VoxelMapWaypointHelper.resolveSyncedEditTarget(
                waypoint(SyncedWaypointName.format("Bases", "Home"), "overworld", "the_nether"), files));
    }

    private WaypointFilesManagerCore files() {
        WaypointFilesManagerCore files = new WaypointFilesManagerCore(directory);
        files.putWaypointList("minecraft:overworld",
                new WaypointList("Bases", 7, List.of(saved("Overworld home"))));
        files.putWaypointList("minecraft:the_nether",
                new WaypointList("Bases", "Server bases", 9, List.of(saved("Nether home"))));
        return files;
    }

    private static SimpleWaypoint saved(String displayName) {
        return new SimpleWaypoint("Home", displayName, "H", new WaypointPos(10, 64, 20),
                0x123456, 90, true, List.of("portal", "base"), "Server description",
                NamespacedId.parse("minecraft:diamond"));
    }

    private static Waypoint waypoint(String name, String... dimensionNames) {
        TreeSet<DimensionContainer> dimensions = new TreeSet<>();
        for (String dimensionName : dimensionNames) {
            dimensions.add(new DimensionContainer(null, dimensionName, null) {
                @Override
                public String getStorageName() {
                    return dimensionName;
                }
            });
        }
        // VoxelMap's scaled coordinates are deliberately different from the cached server position.
        return new Waypoint(name, 80, 160, 64, true, 1, 1, 1, "", "", dimensions);
    }
}
//?}
