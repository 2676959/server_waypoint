package _959.server_waypoint.mixin;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the text-shadow injections against the {@code EditBox} on the test classpath, which is the loader's patched
 * class, so a loader that changes its draw calls fails the build instead of crashing the client at its first text
 * field.
 */
class EditBoxTextShadowMixinTargetsTest {
    @Test
    void everyInjectionPointExistsInTheLoadersEditBox() throws IOException {
        int checked = MixinTargetAssertions.assertInjectionPointsExist(EditBoxTextShadowMixin.class.getName());
        assertTrue(checked > 0, "EditBoxTextShadowMixin has no injection point to check on this version");
    }
}
