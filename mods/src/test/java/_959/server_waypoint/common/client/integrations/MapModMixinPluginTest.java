package _959.server_waypoint.common.client.integrations;

import _959.server_waypoint.mixin.MixinConfigFile;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mixin asks a config's plugin about each mixin before it looks the target class up, so a map-mod mixin the
 * plugin skips leaves no warning in the log when that map mod isn't installed (issue #38). Covers every mixin the
 * configs list for this target.
 */
class MapModMixinPluginTest {
    private static final String CORE_PACKAGE = "_959.server_waypoint.mixin";
    /** The mod each integration package needs, written out independently of the plugin. */
    private static final Map<String, String> REQUIRED_MOD = Map.of(
            "_959.server_waypoint.mixin.voxelmap", "voxelmap",
            "_959.server_waypoint.mixin.xaeros_minimap", "xaerominimap",
            "_959.server_waypoint.mixin.xaeros_worldmap", "xaeroworldmap");
    /** The plugin decides by mixin, but Mixin passes the target class too. */
    private static final String ANY_TARGET = "net.minecraft.client.Minecraft";
    private static final List<String> CONFIGS =
            List.of(MixinConfigFile.COMMON/*? if voxelmap {*/, MixinConfigFile.VOXELMAP/*?}*/);

    @TestFactory
    List<DynamicTest> everyConfigNamesTheMapModPlugin() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String name : CONFIGS) {
            MixinConfigFile config = MixinConfigFile.load(name);
            tests.add(DynamicTest.dynamicTest(name, () -> assertEquals(MapModMixinPlugin.class.getName(),
                    config.plugin(), name + " must let the plugin skip mixins for absent map mods")));
        }
        return tests;
    }

    @TestFactory
    List<DynamicTest> mapModMixinsApplyOnlyWhenTheirModIsLoaded() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String name : CONFIGS) {
            for (String mixin : MixinConfigFile.load(name).mixinClasses()) {
                tests.add(DynamicTest.dynamicTest(mixin.substring(CORE_PACKAGE.length() + 1),
                        () -> assertGatedByItsMod(mixin)));
            }
        }
        return tests;
    }

    private static void assertGatedByItsMod(String mixin) {
        String mixinPackage = mixin.substring(0, mixin.lastIndexOf('.'));
        if (mixinPackage.equals(CORE_PACKAGE)) {
            assertTrue(applies(mixin, Set.of()), "core mixins apply without any map mod");
            return;
        }
        String mod = REQUIRED_MOD.get(mixinPackage);
        assertNotNull(mod, mixinPackage + " is not a known map-mod package; gate it in MapModMixinPlugin");
        Set<String> otherMods = new HashSet<>(REQUIRED_MOD.values());
        otherMods.remove(mod);
        assertFalse(applies(mixin, Set.of()), "applied although no map mod is loaded");
        assertFalse(applies(mixin, otherMods), "applied with only other map mods loaded");
        assertTrue(applies(mixin, Set.of(mod)), "skipped although " + mod + " is loaded");
    }

    private static boolean applies(String mixin, Set<String> loadedMods) {
        return new MapModMixinPlugin(loadedMods::contains).shouldApplyMixin(ANY_TARGET, mixin);
    }
}
