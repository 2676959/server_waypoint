package _959.server_waypoint.common.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import xaero.hud.minimap.module.MinimapSession;
import xaero.hud.minimap.world.MinimapDimensionHelper;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;
import xaero.hud.minimap.world.container.MinimapWorldContainer;
import xaero.hud.minimap.world.container.MinimapWorldRootContainer;
import xaero.hud.minimap.world.state.MinimapWorldStateUpdater;
import xaero.hud.path.XaeroPath;

import static _959.server_waypoint.common.util.TestDimensions.NETHER;
import static _959.server_waypoint.common.util.TestDimensions.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertSame;

class XaeroMinimapWorldTest {
    @Test
    void enteringNetherDoesNotResolveToStaleOverworld() throws ReflectiveOperationException {
        assertResolvedWorld(NETHER, OVERWORLD, NETHER, false);
    }

    @Test
    void returningToOverworldDoesNotResolveToStaleNether() throws ReflectiveOperationException {
        assertResolvedWorld(OVERWORLD, NETHER, OVERWORLD, false);
    }

    @Test
    void matchingAutomaticWorldPreservesXaerosConnectedSubworld() throws ReflectiveOperationException {
        assertResolvedWorld(NETHER, NETHER, NETHER, true);
    }

    @Test
    void nonCurrentDimensionResolvesItsOwnWorld() throws ReflectiveOperationException {
        assertResolvedWorld(OVERWORLD, OVERWORLD, NETHER, false);
    }

    @Test
    void missingAutomaticWorldResolvesRequestedDimension() throws ReflectiveOperationException {
        assertResolvedWorld(NETHER, null, NETHER, false);
    }

    private static void assertResolvedWorld(ResourceKey<Level> clientDimension,
                                            ResourceKey<Level> autoDimension,
                                            ResourceKey<Level> requestedDimension,
                                            boolean expectAutomaticWorld) throws ReflectiveOperationException {
        MinimapWorld automatic = autoDimension == null ? null : createWorld(autoDimension, "connected");
        MinimapWorld fallback = createWorld(requestedDimension, "mw$123");
        MinimapSession session = createSession();
        MinimapWorldRootContainer root = allocate(MinimapWorldRootContainer.class);
        setField(MinimapWorldContainer.class, root, "path", XaeroPath.root("Multiplayer_test"));
        // Xaero's helper reads Level's constants, which NeoForge 1.21.9+ cannot initialize without its loader.
        // These are the directory names it gives both dimensions.
        MinimapDimensionHelper dimensionHelper = new MinimapDimensionHelper() {
            @Override
            public String getDimensionDirectoryName(ResourceKey<Level> dimension) {
                return NETHER.equals(dimension) ? "dim%-1" : "dim%0";
            }
        };
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

        // The client dimension reproduces the interval after Minecraft changes level but before Xaero updates its path.
        assertSame(expectAutomaticWorld ? automatic : fallback,
                XaeroMinimapHelper.getMinimapWorld(session, requestedDimension, clientDimension));
    }

    private static MinimapWorld createWorld(ResourceKey<Level> dimension, String node)
            throws ReflectiveOperationException {
        //? if >= 1.21.5 {
        Constructor<MinimapWorld> constructor = MinimapWorld.class.getDeclaredConstructor(
                MinimapWorldContainer.class,
                String.class,
                ResourceKey.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(null, node, dimension);
        //?} else {
        /*return new MinimapWorld(null, node, dimension) {
        };
        *///?}
    }

    private static MinimapSession createSession() throws ReflectiveOperationException {
        //? if >= 1.21.5 {
        return allocate(MinimapSession.class);
        //?} else {
        /*return allocate(TestMinimapSession.class);
        *///?}
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

    // Xaero declares MinimapSession abstract before 1.21.5. Allocating this subclass skips the
    // session constructor, which needs a running client.
    //? if < 1.21.5 {
    /*private static final class TestMinimapSession extends MinimapSession {
        private TestMinimapSession() {
            super(null, null, null);
        }
    }
    *///?}
}
