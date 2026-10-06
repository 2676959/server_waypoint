package _959.server_waypoint.common.util;

import java.lang.reflect.Field;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapDimensionHelper;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;
import xaero.hud.path.XaeroPath;

import static org.junit.jupiter.api.Assertions.assertSame;

class XaeroMinimapWorldTest {
    @BeforeAll
    static void bootstrapRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void enteringNetherDoesNotResolveToStaleOverworld() throws ReflectiveOperationException {
        assertResolvedWorld(Level.NETHER, Level.OVERWORLD, Level.NETHER, false);
    }

    @Test
    void returningToOverworldDoesNotResolveToStaleNether() throws ReflectiveOperationException {
        assertResolvedWorld(Level.OVERWORLD, Level.NETHER, Level.OVERWORLD, false);
    }

    @Test
    void matchingAutomaticWorldPreservesXaerosConnectedSubworld() throws ReflectiveOperationException {
        assertResolvedWorld(Level.NETHER, Level.NETHER, Level.NETHER, true);
    }

    @Test
    void nonCurrentDimensionResolvesItsOwnWorld() throws ReflectiveOperationException {
        assertResolvedWorld(Level.OVERWORLD, Level.OVERWORLD, Level.NETHER, false);
    }

    @Test
    void missingAutomaticWorldResolvesRequestedDimension() throws ReflectiveOperationException {
        assertResolvedWorld(Level.NETHER, null, Level.NETHER, false);
    }

    private static void assertResolvedWorld(ResourceKey<Level> clientDimension,
                                            ResourceKey<Level> autoDimension,
                                            ResourceKey<Level> requestedDimension,
                                            boolean expectAutomaticWorld) throws ReflectiveOperationException {
        MinimapWorld automatic = autoDimension == null ? null : createWorld(autoDimension, "connected");
        MinimapWorld fallback = createWorld(requestedDimension, "mw$123");
        MinimapSession session = allocate(MinimapSession.class);
        MinimapWorldRootContainer root = allocate(MinimapWorldRootContainer.class);
        setField(MinimapWorldContainer.class, root, "path", XaeroPath.root("Multiplayer_test"));
        MinimapDimensionHelper dimensionHelper = new MinimapDimensionHelper();
        XaeroPath expectedPath = XaeroPath.root("Multiplayer_test")
                .resolve(dimensionHelper.getDimensionDirectoryName(requestedDimension)).resolve("mw$123");
        MinimapWorldManager manager = new MinimapWorldManager(null, session) {
            @Override
            public MinimapWorld getAutoWorld() {
                return automatic;
            }

            @Override
            public MinimapWorldRootContainer getAutoRootContainer() {
                return root;
            }

            @Override
            public MinimapWorld getWorld(XaeroPath path) {
                return expectedPath.equals(path) ? fallback : null;
            }
        };
        MinimapWorldStateUpdater updater = new MinimapWorldStateUpdater(null, session, null) {
            @Override
            public String getPotentialWorldNode(ResourceKey dimension, boolean useWorldmap) {
                return "mw$123";
            }
        };
        setField(MinimapSession.class, session, "worldManager", manager);
        setField(MinimapSession.class, session, "dimensionHelper", dimensionHelper);
        setField(MinimapSession.class, session, "worldStateUpdater", updater);

        // Reproduce the interval after Minecraft changes level but before Xaero updates its path.
        Minecraft client = allocate(Minecraft.class);
        ClientLevel level = allocate(ClientLevel.class);
        setField(Level.class, level, "dimension", clientDimension);
        client.level = level;
        Field instance = Minecraft.class.getDeclaredField("instance");
        instance.setAccessible(true);
        Object previous = instance.get(null);
        instance.set(null, client);
        try {
            assertSame(expectAutomaticWorld ? automatic : fallback,
                    XaeroMinimapHelper.getMinimapWorld(session, requestedDimension));
        } finally {
            instance.set(null, previous);
        }
    }

    private static MinimapWorld createWorld(ResourceKey<Level> dimension, String node)
            throws ReflectiveOperationException {
        MinimapWorld world = allocate(MinimapWorld.class);
        world.setDimId(dimension);
        world.setNode(node);
        return world;
    }

    private static void setField(Class<?> owner, Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(unsafeClass.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }
}
