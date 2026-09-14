package _959.server_waypoint.mixin;

import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayerManagerMixinTest {
    @Test
    void sendsXaerosWorldIdBeforeVanillaLevelInfoPackets() {
        Method hook = Arrays.stream(PlayerManagerMixin.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("onSendWorldInfo"))
                .findFirst()
                .orElseThrow();

        Inject injection = hook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertEquals(1, injection.at().length);
        At injectionPoint = injection.at()[0];
        assertEquals("HEAD", injectionPoint.value());
    }
}
