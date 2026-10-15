package _959.server_waypoint.common.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
//? if <= 1.20.2 {
/*import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
*///?}

/**
 * Dimension keys that plain JUnit tests can create on every target. {@link ResourceKey#create} interns keys, so
 * these are the same instances as Level's constants; reading those constants would initialize Level, which
 * NeoForge 1.21.9+ cannot do without its loader.
 */
final class TestDimensions {
    //? if <= 1.20.2 {
    /*static {
        // In 1.20.1 and 1.20.2 creating any registry key initializes BuiltInRegistries, which requires the bootstrap.
        SharedConstants.tryDetectVersion();
        try {
            Bootstrap.bootStrap();
        } catch (ExceptionInInitializerError networkSetup) {
            // Forge 1.20.1 fails at the bootstrap's last step, network setup, after the registries are built.
        }
    }
    *///?}

    static final ResourceKey<Level> OVERWORLD = of("minecraft", "overworld");
    static final ResourceKey<Level> NETHER = of("minecraft", "the_nether");

    private TestDimensions() {
    }

    static ResourceKey<Level> of(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocationHelper.mcId(namespace, path));
    }
}
