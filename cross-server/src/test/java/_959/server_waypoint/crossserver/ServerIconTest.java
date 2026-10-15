package _959.server_waypoint.crossserver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServerIconTest {
    @Test void acceptsNamespacedVanillaAndModdedIdentifiers() {
        assertEquals("minecraft:beacon", ServerIcon.validate(ServerIcon.DEFAULT));
        assertEquals("my_mod:icons/server", ServerIcon.validate("my_mod:icons/server"));
    }

    @Test void rejectsMalformedOrOversizedIdentifiers() {
        for (String invalid : new String[]{"", "compass", "Minecraft:compass", "minecraft:",
                "minecraft:iron ingot", "minecraft:compass\n", "mod:" + "x".repeat(253)}) {
            assertThrows(IllegalArgumentException.class, () -> ServerIcon.validate(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> ServerIcon.validate(null));
    }
}
