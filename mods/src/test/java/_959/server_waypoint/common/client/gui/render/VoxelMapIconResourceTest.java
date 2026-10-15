//~ resource_location_import
//? if voxelmap {
package _959.server_waypoint.common.client.gui.render;

import _959.server_waypoint.common.client.integrations.VoxelMapIconIds;
import _959.server_waypoint.util.NamespacedId;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class VoxelMapIconResourceTest {
    private Field instance;
    private Object previousClient;
    private PackagedResources resources;

    @BeforeEach
    void installPackagedResourcesWithoutStartingTheClient() throws ReflectiveOperationException {
        instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        previousClient = instance.get(null);
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafe = unsafeClass.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        Minecraft client = (Minecraft) unsafeClass.getMethod("allocateInstance", Class.class)
                .invoke(unsafe.get(null), Minecraft.class);
        resources = new PackagedResources();
        Field resourceManager = Minecraft.class.getDeclaredField("resourceManager");
        resourceManager.setAccessible(true);
        resourceManager.set(client, resources);
        instance.set(null, client);
    }

    @AfterEach
    void restoreClient() throws IllegalAccessException {
        instance.set(null, previousClient);
        resources.close();
    }

    static Stream<NamespacedId> selectableIcons() {
        return VoxelMapIconIds.ids().stream();
    }

    @ParameterizedTest
    @MethodSource("selectableIcons")
    void resolvesSelectableIconFromTheConfiguredVoxelMapJar(NamespacedId id) throws Exception {
        WaypointIconRenderer.ResolvedIcon icon = WaypointIconRenderer.resolve(id);

        assertEquals(WaypointIconRenderer.Kind.VOXELMAP, icon.kind(), id.toString());
        assertEquals(id, icon.id());
        assertNotNull(icon.texture());
        try (var image = resources.getResource(icon.texture()).orElseThrow().open()) {
            assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, image.readNBytes(8));
        }
    }

    @Test
    void missingResourcesKeepTheStoredIconAndUseInitials() {
        resources.available = false;
        NamespacedId id = NamespacedId.parse("voxelmap:star");
        WaypointIconRenderer.ResolvedIcon icon = WaypointIconRenderer.resolve(id);

        assertEquals(WaypointIconRenderer.Kind.INITIALS, icon.kind());
        assertEquals(id, icon.id());
        assertNull(icon.texture());
    }

    @Test
    void unsupportedIconsUseInitialsEvenIfResourcesAreInstalled() {
        WaypointIconRenderer.ResolvedIcon icon = WaypointIconRenderer.resolve(
                NamespacedId.parse("voxelmap:missing"));

        assertEquals(WaypointIconRenderer.Kind.INITIALS, icon.kind());
        assertNull(icon.texture());
    }

    /** Reads the dependency's actual images while avoiding the client and GPU lifecycle. */
    private static final class PackagedResources extends ReloadableResourceManager {
        private boolean available = true;

        private PackagedResources() {
            super(PackType.CLIENT_RESOURCES);
        }

        @Override
        public Optional<Resource> getResource(
                //$ resource_location_type_swap
                Identifier
                id) {
            var image = getClass().getClassLoader().getResource("assets/" + id.getNamespace() + "/" + id.getPath());
            return available && image != null
                    ? Optional.of(new Resource(null, image::openStream)) : Optional.empty();
        }
    }
}
//?}
