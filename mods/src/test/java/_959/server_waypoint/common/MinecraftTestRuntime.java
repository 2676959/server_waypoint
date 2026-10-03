package _959.server_waypoint.common;

import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Assumptions;

/** What a unit-test JVM can rely on after the tests have asked Minecraft to bootstrap. */
public final class MinecraftTestRuntime {
    private MinecraftTestRuntime() {
    }

    /**
     * Skips the test unless entity types are registered. A bootstrap that fails still marks itself done,
     * so when a runtime without Forge's loader fails it in one test class, the classes after it in the
     * same JVM see it succeed and get empty registries.
     */
    public static void assumeEntityTypesAreRegistered() {
        Assumptions.assumeTrue(BuiltInRegistries.ENTITY_TYPE.size() > 0,
                "Minecraft's entity types are not registered in this test runtime");
    }
}
